import argparse
from pathlib import Path
import struct


TARGETS = {
    "windows-x64": ("pe", 0x8664),
    "windows-arm64": ("pe", 0xAA64),
    "linux-x64": ("elf", 62),
    "linux-arm64": ("elf", 183),
}


def architecture(path):
    with Path(path).open("rb") as binary:
        header = binary.read(64)
        if len(header) < 64:
            raise ValueError("Truncated executable header")
        if header[:4] == b"\x7fELF":
            if header[4:6] != b"\x02\x01":
                raise ValueError("Only 64-bit little-endian ELF is supported")
            return "elf", struct.unpack_from("<H", header, 18)[0]
        if header[:2] == b"MZ":
            offset = struct.unpack_from("<I", header, 60)[0]
            if not 64 <= offset <= 16 * 1024 * 1024:
                raise ValueError("Invalid PE header offset")
            binary.seek(offset)
            signature = binary.read(26)
            if len(signature) != 26 or signature[:4] != b"PE\x00\x00":
                raise ValueError("Invalid PE signature")
            if struct.unpack_from("<H", signature, 24)[0] != 0x20B:
                raise ValueError("Only PE32+ (64-bit) is supported")
            return "pe", struct.unpack_from("<H", signature, 4)[0]
        raise ValueError("Expected a native ELF or PE executable, not an archive")


def verify(target, paths):
    expected = TARGETS[target]
    for path in paths:
        actual = architecture(path)
        if actual != expected:
            raise ValueError(f"{path}: architecture {actual} does not match {target}")


def main():
    parser = argparse.ArgumentParser(description="Verify NekoPlayer executable and bundled native library architectures")
    parser.add_argument("target", choices=TARGETS)
    parser.add_argument("files", type=Path, nargs="+")
    arguments = parser.parse_args()
    try:
        verify(arguments.target, arguments.files)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Artifact validation failed: {error}\n")
    print(f"Verified {len(arguments.files)} native file(s) for {arguments.target}")


if __name__ == "__main__":
    main()
