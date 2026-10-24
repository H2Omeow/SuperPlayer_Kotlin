import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from prepare_windows import download, forwarder_definitions


class WindowsDependencyTests(unittest.TestCase):
    def test_egl_aliases_and_gles_forward_to_the_same_native_runtime(self):
        definitions = forwarder_definitions(["EGL_Initialize", "EGL_GetProcAddress", "EGL_ChooseConfig", "glFinish", "glBindTexture", "InternalSymbol"])
        self.assertIn("eglGetProcAddress=av_libglesv2.EGL_GetProcAddress", definitions["libEGL"])
        self.assertIn("glFinish=av_libglesv2.glFinish", definitions["libGLESv2"])
        self.assertNotIn("InternalSymbol", "".join(definitions.values()))

    def test_incompatible_runtime_is_rejected(self):
        with self.assertRaises(ValueError):
            forwarder_definitions(["glFinish", "eglInitialize"])

    def test_download_refuses_corrupted_cached_or_remote_dependencies(self):
        specification = {"fixture": {"url": "https://example.test/file.zip", "sha256": "0" * 64}}
        with tempfile.TemporaryDirectory() as temporary, patch("prepare_windows.PINS", specification):
            directory = Path(temporary)
            with patch("urllib.request.urlopen", return_value=io.BytesIO(b"wrong")):
                with self.assertRaisesRegex(ValueError, "checksum"):
                    download("fixture", directory)
            with self.assertRaisesRegex(ValueError, "checksum"):
                download("fixture", directory)


if __name__ == "__main__":
    unittest.main()
