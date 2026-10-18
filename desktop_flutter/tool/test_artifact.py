from pathlib import Path
import struct
import tempfile
import unittest

from check_artifact import architecture, verify


def elf(machine, bits=2):
    header = bytearray(64)
    header[:6] = b"\x7fELF" + bytes([bits, 1])
    struct.pack_into("<H", header, 18, machine)
    return header


def pe(machine, magic=0x20B):
    header = bytearray(154)
    header[:2] = b"MZ"
    struct.pack_into("<I", header, 60, 128)
    header[128:132] = b"PE\x00\x00"
    struct.pack_into("<H", header, 132, machine)
    struct.pack_into("<H", header, 152, magic)
    return header


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.binary = Path(self.directory.name) / "player"

    def test_all_four_64_bit_targets(self):
        for target, data in {
            "linux-x64": elf(62),
            "linux-arm64": elf(183),
            "windows-x64": pe(0x8664),
            "windows-arm64": pe(0xAA64),
        }.items():
            with self.subTest(target=target):
                self.binary.write_bytes(data)
                verify(target, [self.binary])

    def test_mislabeled_architecture_is_rejected(self):
        self.binary.write_bytes(elf(62))
        with self.assertRaises(ValueError):
            verify("linux-arm64", [self.binary])

    def test_32_bit_binaries_are_rejected(self):
        for data in [elf(3, 1), pe(0x14C, 0x10B), pe(0x1C4, 0x10B)]:
            self.binary.write_bytes(data)
            with self.assertRaises(ValueError):
                architecture(self.binary)

    def test_broken_headers_are_rejected(self):
        for data in [b"", b"PK" + bytes(64), b"MZ" + bytes(62)]:
            self.binary.write_bytes(data)
            with self.assertRaises(ValueError):
                architecture(self.binary)

    def test_native_libraries_are_also_checked(self):
        library = self.binary.with_suffix(".so")
        self.binary.write_bytes(elf(183))
        library.write_bytes(elf(62))
        with self.assertRaises(ValueError):
            verify("linux-arm64", [self.binary, library])


if __name__ == "__main__":
    unittest.main()
