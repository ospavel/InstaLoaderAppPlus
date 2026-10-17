"""Tests for download progress callbacks."""

import io
import os
import tempfile
import unittest
from types import SimpleNamespace

from instaloader_service.downloader import _install_transfer_progress


class TransferProgressTest(unittest.TestCase):
    def test_completed_transfer_reports_saved_file_path(self):
        reports = []
        loader = SimpleNamespace(
            context=SimpleNamespace(
                write_raw=lambda response, filename: self.fail("raw transfer should be intercepted"),
                log=lambda *args, **kwargs: None,
            )
        )

        with tempfile.TemporaryDirectory() as directory:
            filename = os.path.join(directory, "media.jpg")
            response = SimpleNamespace(
                raw=io.BytesIO(b"image bytes"),
                headers={"Content-Length": "11"},
            )
            _install_transfer_progress(loader, reports.append, {})

            loader.context.write_raw(response, filename)

            with open(filename, "rb") as downloaded_file:
                self.assertEqual(b"image bytes", downloaded_file.read())
            self.assertEqual(filename, reports[-1]["downloaded_file_path"])


if __name__ == "__main__":
    unittest.main()
