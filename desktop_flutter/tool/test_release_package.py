from pathlib import Path
import unittest
from unittest.mock import patch

from package_release import release_version, windows_installer_script


class ReleasePackageTests(unittest.TestCase):
    def test_android_and_desktop_versions_match(self):
        self.assertRegex(release_version(), r"^\d+\.\d+\.\d+$")

    def test_windows_installers_have_separate_native_architectures(self):
        for target, arch in (("windows-x64", "x64compatible"), ("windows-arm64", "arm64")):
            with self.subTest(target=target), patch("package_release.release_version", return_value="1.1.0"):
                script = windows_installer_script(target, Path("payload"), Path("output"))
                self.assertIn(f"ArchitecturesAllowed={arch}\n", script)
                self.assertIn(f"OutputBaseFilename=NekoPlayer-v1.1.0-{target}-setup", script)
                self.assertIn("UninstallDisplayIcon={app}\\NekoPlayer.exe", script)
                self.assertNotIn("java", script.lower())


if __name__ == "__main__":
    unittest.main()
