"""Inspect actual ELF/PE machine IDs, including bundled JVM and decoder."""
import struct
from pathlib import Path


def verify_machine(file: Path, expected: str):
    with file.open("rb") as stream:
        header = stream.read(64)
        if header[:4] == b"\x7fELF":
            endian = "<" if header[5] == 1 else ">"
            machine = struct.unpack_from(endian + "H", header, 18)[0]
            found = {3: "x86", 62: "x64", 183: "arm64", 40: "armv7"}.get(machine)
            expected_class = 1 if expected in ("x86", "armv7") else 2
            assert header[4] == expected_class, "ELF bitness mismatch: " + str(file)
        elif header[:2] == b"MZ":
            stream.seek(struct.unpack_from("<I", header, 60)[0])
            pe = stream.read(6)
            assert pe[:4] == b"PE\0\0"
            found = {0x14c: "x86", 0x8664: "x64", 0xaa64: "arm64"}.get(struct.unpack_from("<H", pe, 4)[0])
        else:
            raise ValueError("Unknown executable format: " + str(file))
    assert found == expected, str(file) + ": " + str(found) + " != " + expected
    print("Verified", file.name, expected)
