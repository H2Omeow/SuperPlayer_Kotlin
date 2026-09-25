#!/usr/bin/env python3
"""Build desktop native components and portable archives on Linux x86-64."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
DESKTOP = ROOT / "desktop"
PINS = json.loads((Path(__file__).with_name("dependencies.json")).read_text())
CACHE = Path(os.environ.get("NEKOPLAYER_BUILD_CACHE", str(DESKTOP / "build" / "packages")))


def run(args, cwd=None, env=None):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), cwd=cwd, env=env, check=True)


def download(spec):
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / spec["url"].rsplit("/", 1)[1]
    if not path.exists():
        temporary = path.with_suffix(path.suffix + ".part")
        with urllib.request.urlopen(spec["url"], timeout=90) as src, temporary.open("wb") as out:
            shutil.copyfileobj(src, out)
        temporary.replace(path)
    if hashlib.sha256(path.read_bytes()).hexdigest() != spec["sha256"]:
        raise RuntimeError("Checksum mismatch: " + path.name)
    return path


def extract(archive):
    dest = CACHE / (archive.name + ".unpacked")
    if not (dest / ".complete").exists():
        shutil.rmtree(dest, ignore_errors=True)
        dest.mkdir(parents=True)
        if archive.suffix == ".zip":
            with zipfile.ZipFile(archive) as source:
                for member in source.namelist():
                    if not (dest / member).resolve().is_relative_to(dest.resolve()):
                        raise ValueError("Unsafe archive path")
                source.extractall(dest)
        else:
            with tarfile.open(archive) as source:
                source.extractall(dest, filter="data")
        (dest / ".complete").touch()
    directories = [p for p in dest.iterdir() if p.is_dir()]
    if len(directories) != 1:
        raise ValueError("Expected one archive root")
    return directories[0]


def build(target):
    runtime = extract(download(PINS["runtimes"][target]))
    source_archive = download(PINS["ffmpeg"])
    source = extract(source_archive)
    build_dir = DESKTOP / "build" / "native-targets" / target
    build_dir.mkdir(parents=True, exist_ok=True)
    windows = target.startswith("windows-")
    arch = target.split("-")[1]
    env = os.environ.copy()
    flags = []
    cross = []
    if windows:
        toolchain = extract(download(PINS["llvm_mingw"]))
        triplet = {"x64": "x86_64", "x86": "i686", "arm64": "aarch64"}[arch] + "-w64-mingw32"
        prefix = str(toolchain / "bin" / (triplet + "-"))
        cc, cxx = prefix + "clang", prefix + "clang++"
        cross = ["--enable-cross-compile", "--target-os=mingw32", "--cross-prefix=" + prefix]
        native = build_dir / "nekoplayer_audio_effects.dll"
        launcher_binary = build_dir / "NekoPlayer.exe"
        include_platform = DESKTOP / "native" / "jni" / "win32"
        flags = ["-static", "-Wl,--kill-at"] if arch == "x86" else ["-static"]
        ffarch = {"x64": "x86_64", "x86": "x86", "arm64": "aarch64"}[arch]
        env["PATH"] = str(toolchain / "bin") + os.pathsep + env["PATH"]
    else:
        cc, cxx = "gcc", "g++"
        ffarch = {"x64": "x86_64", "x86": "x86", "arm64": "aarch64", "armv7": "arm"}[arch]
        if arch in ("arm64", "armv7"):
            prefix = "aarch64-linux-gnu-" if arch == "arm64" else "arm-linux-gnueabihf-"
            cc, cxx = prefix + "gcc", prefix + "g++"
            cross = ["--enable-cross-compile", "--target-os=linux", "--cross-prefix=" + prefix]
        if arch == "x86":
            flags += ["-m32"]
            cross += ["--extra-cflags=-m32", "--extra-ldflags=-m32"]
        flags += ["-static-libstdc++", "-static-libgcc"]
        native = build_dir / "libnekoplayer_audio_effects.so"
        include_platform = Path(os.environ["JAVA_HOME"]) / "include" / "linux"
    run([cxx, "-O2", "-std=c++17", "-fPIC", "-shared", *flags,
         "-I" + str(Path(os.environ["JAVA_HOME"]) / "include"), "-I" + str(include_platform),
         ROOT / "app/src/main/cpp/native-audio-effects.cpp", "-o", native], env=env)
    if windows:
        run([cxx, "-O2", "-std=c++17", "-municode", "-mwindows", "-static",
             DESKTOP / "native/windows-launcher.cpp", "-o", launcher_binary], env=env)
    ffbuild = build_dir / "ffmpeg-build"
    ffbuild.mkdir(exist_ok=True)
    configure = [str(source / "configure"), "--arch=" + ffarch, "--cc=" + cc, "--cxx=" + cxx,
        "--disable-autodetect", "--disable-everything", "--disable-network", "--disable-doc", "--disable-debug",
        "--disable-asm", "--disable-shared", "--enable-static", "--disable-ffplay", "--disable-ffprobe", "--enable-ffmpeg",
        "--enable-protocol=file,pipe", "--enable-demuxer=mov,mp3,flac,ogg,wav,aac,ape,asf",
        "--enable-decoder=mp3,mp3float,flac,aac,aac_fixed,alac,vorbis,opus,pcm_s16le,pcm_s24le,pcm_s32le,pcm_f32le,pcm_f64le,ape,wmav1,wmav2",
        "--enable-encoder=pcm_s16le", "--enable-muxer=pcm_s16le", "--enable-parser=mpegaudio,aac,flac,opus,vorbis",
        "--enable-filter=aresample,aformat,anull,atrim", "--enable-swresample", *cross]
    run(configure, cwd=ffbuild, env=env)
    run(["make", "-j" + os.environ.get("NEKOPLAYER_JOBS", "2")], cwd=ffbuild, env=env)
    ffmpeg = ffbuild / ("ffmpeg.exe" if windows else "ffmpeg")
    if not ffmpeg.is_file():
        raise RuntimeError("FFmpeg binary missing")
    stage = DESKTOP / "build" / "stage" / ("NekoPlayer-1.1.0-pre-" + target)
    shutil.rmtree(stage, ignore_errors=True)
    (stage / "native").mkdir(parents=True)
    shutil.copytree(runtime, stage / "runtime", symlinks=True)
    shutil.copytree(DESKTOP / "build/install/NekoPlayer-desktop/lib", stage / "lib")
    shutil.copy2(native, stage / "native" / native.name)
    shutil.copy2(ffmpeg, stage / "native" / ffmpeg.name)
    if windows:
        shutil.copy2(launcher_binary, stage / launcher_binary.name)
    licenses = stage / "licenses"
    licenses.mkdir()
    shutil.copy2(source / "COPYING.LGPLv2.1", licenses / "FFmpeg-LGPL-2.1.txt")
    shutil.copy2(source / "LICENSE.md", licenses / "FFmpeg-LICENSE.md")
    for name in ("LICENSE", "LICENSE.txt", "README.md"):
        if (ROOT / name).exists():
            shutil.copy2(ROOT / name, stage / name)
    shutil.copy2(DESKTOP / "README.md", stage / "DESKTOP.md")
    shutil.copytree(ROOT / "app/src/main/assets/licenses", licenses / "providers", dirs_exist_ok=True)
    shutil.copy2(DESKTOP / "packaging/dependencies.json", stage / "dependencies.json")
    manifest = {"target": target, "version": "1.1.0-pre", "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
                "ffmpeg_source": PINS["ffmpeg"], "ffmpeg_configure": configure, "runtime": PINS["runtimes"][target]}
    (stage / "build-info.json").write_text(json.dumps(manifest, indent=2) + "\n")
    if windows:
        (stage / "NekoPlayer.cmd").write_text('@echo off\nsetlocal\nset "APP_DIR=%~dp0"\n"%APP_DIR%runtime\\bin\\java.exe" -Dnekoplayer.home="%APP_DIR%." -Djava.library.path="%APP_DIR%native" -cp "%APP_DIR%lib\\*" top.nekoh2o.player.desktop.MainKt %*\nif errorlevel 1 pause\n', encoding="utf-8")
        (stage / "NekoPlayer.vbs").write_text('Set sh = CreateObject("WScript.Shell")\nSet fs = CreateObject("Scripting.FileSystemObject")\np = fs.GetParentFolderName(WScript.ScriptFullName)\nq = Chr(34)\nsh.Run q & p & "\\runtime\\bin\\javaw.exe" & q & " -Dnekoplayer.home=" & q & p & q & " -Djava.library.path=" & q & p & "\\native" & q & " -cp " & q & p & "\\lib\\*" & q & " top.nekoh2o.player.desktop.MainKt", 0, False\n')
    else:
        launcher = stage / "NekoPlayer"
        launcher.write_text('#!/bin/sh\nAPP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\nexec "$APP_DIR/runtime/bin/java" -Dnekoplayer.home="$APP_DIR" -Djava.library.path="$APP_DIR/native" -cp "$APP_DIR/lib/*" top.nekoh2o.player.desktop.MainKt "$@"\n')
        launcher.chmod(0o755)
        (stage / "native/ffmpeg").chmod(0o755)
    # Each published binary is checked for the requested machine type before packaging.
    from verify import verify_machine
    expected = {"x64": "x64", "x86": "x86", "arm64": "arm64", "armv7": "armv7"}[arch]
    verified_files = [stage / "native" / native.name, stage / "native" / ffmpeg.name,
                      stage / "runtime/bin" / ("java.exe" if windows else "java")]
    if windows:
        verified_files.append(stage / "NekoPlayer.exe")
    for file in verified_files:
        verify_machine(file, expected)
    dist = DESKTOP / "build/dist"
    dist.mkdir(exist_ok=True)
    if windows:
        with zipfile.ZipFile(dist / (stage.name + ".zip"), "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for file in stage.rglob("*"):
                if file.is_file(): archive.write(file, str(file.relative_to(stage.parent)))
    else:
        with tarfile.open(dist / (stage.name + ".tar.gz"), "w:gz") as archive:
            archive.add(stage, arcname=stage.name)
    shutil.copy2(source_archive, dist / source_archive.name)
    print("Built", stage.name, flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("target", choices=list(PINS["runtimes"]))
    build(parser.parse_args().target)
