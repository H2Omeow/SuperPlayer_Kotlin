from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

from test_artifact import pe


@unittest.skipUnless(shutil.which("cmake"), "CMake is required")
class WindowsAngleTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix="neko-angle-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.source = self.root / "arm64-angle"
        self.build = self.root / "build"
        self.build.mkdir()
        self.script = Path(__file__).resolve().parents[1] / "native/windows_angle.cmake"

    def prepare(self, machine=0xAA64):
        for name in ["include/EGL/egl.h", "include/GLES2/gl2.h", "lib/libEGL.dll.lib", "lib/libGLESv2.dll.lib"]:
            destination = self.source / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(b"fixture")
        for name in ["libEGL.dll", "libGLESv2.dll"]:
            (self.source / name).write_bytes(pe(machine))

    def configure(self, platform="windows-arm64", source=None):
        environment = os.environ.copy()
        environment.pop("NEKO_WINDOWS_ARM64_ANGLE_DIR", None)
        environment.pop("NEKO_WINDOWS_ANGLE_DIR", None)
        return subprocess.run(
            ["cmake", f"-DFLUTTER_TARGET_PLATFORM={platform}",
             f"-DNEKO_WINDOWS_ARM64_ANGLE_DIR={source or self.source}",
             "-P", str(self.script)], cwd=self.build, env=environment,
            capture_output=True, text=True, timeout=15,
        )

    def test_missing_arm64_angle_fails_early(self):
        result = self.configure()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("must not be used on ARM64", " ".join(result.stderr.split()))

    def test_upstream_x64_angle_cannot_be_used_on_arm64(self):
        self.prepare(0x8664)
        result = self.configure()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Wrong architecture", result.stderr)
        self.assertFalse((self.build / "ANGLE").exists())

    def test_auxiliary_dlls_are_checked_too(self):
        self.prepare()
        (self.source / "zlib.dll").write_bytes(pe(0x8664))
        self.assertNotEqual(self.configure().returncode, 0)

    def test_arm64_only_staging_replaces_stale_generated_dependencies(self):
        self.prepare()
        staging = self.build / "ANGLE"
        staging.mkdir()
        (staging / "stale-x64.dll").write_bytes(pe(0x8664))
        result = self.configure()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((staging / "stale-x64.dll").exists())
        self.assertEqual((staging / "libEGL.dll").read_bytes(), pe(0xAA64))
        self.assertTrue((self.source / "libEGL.dll").exists())

    def test_generated_directory_is_not_accepted_as_input(self):
        self.source = self.build / "ANGLE"
        self.prepare()
        result = self.configure()
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.source / "libEGL.dll").exists())

    def test_x64_does_not_require_an_arm64_dependency_override(self):
        self.assertEqual(self.configure("windows-x64").returncode, 0)

    def test_x64_override_checks_every_dll(self):
        self.prepare(0x8664)
        environment = os.environ.copy()
        environment["NEKO_WINDOWS_ANGLE_DIR"] = str(self.source)
        command = ["cmake", "-DFLUTTER_TARGET_PLATFORM=windows-x64", "-P", str(self.script)]
        result = subprocess.run(command, cwd=self.build, env=environment, capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        (self.source / "libEGL.dll").write_bytes(pe(0xAA64))
        result = subprocess.run(command, cwd=self.build, env=environment, capture_output=True, text=True)
        self.assertNotEqual(result.returncode, 0)

    def test_license_follows_the_generated_bundle_install_prefix(self):
        self.prepare()
        (self.source / "LICENSE").write_text("ANGLE license fixture")
        project = self.root / "project"
        project.mkdir()
        installed = self.root / "installed"
        (project / "CMakeLists.txt").write_text(
            'cmake_minimum_required(VERSION 3.14)\nproject(angle_install NONE)\n'
            'set(FLUTTER_TARGET_PLATFORM "windows-arm64")\n'
            'add_executable(player IMPORTED GLOBAL)\n'
            f'set_target_properties(player PROPERTIES IMPORTED_LOCATION "{installed.as_posix()}/player")\n'
            'set(CMAKE_INSTALL_PREFIX "$<TARGET_FILE_DIR:player>" CACHE PATH "" FORCE)\n'
            f'include("{self.script.as_posix()}")\n'
        )
        generated = self.root / "generated"
        subprocess.run(["cmake", "-S", str(project), "-B", str(generated),
                        f"-DNEKO_WINDOWS_ANGLE_DIR={self.source}"], cwd=self.root, check=True, capture_output=True)
        subprocess.run(["cmake", "--install", str(generated)], cwd=self.root, check=True, capture_output=True)
        self.assertEqual((installed / "licenses/angle/LICENSE").read_text(), "ANGLE license fixture")


if __name__ == "__main__":
    unittest.main()
