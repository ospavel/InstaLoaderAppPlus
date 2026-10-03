"""Tests for importing Instagram sessions from Android WebView cookies."""

import unittest

from instaloader.exceptions import LoginException

from instaloader_service.session import parse_cookie_header


class CookieHeaderTest(unittest.TestCase):
    def test_preserves_equals_and_ignores_empty_segments(self):
        self.assertEqual(
            parse_cookie_header("  sessionid=abc== ; csrftoken=xyz== ;; ig_did=q  "),
            {"sessionid": "abc==", "csrftoken": "xyz==", "ig_did": "q"},
        )

    def test_requires_sessionid_without_echoing_cookie_values(self):
        secret = "not-a-session-secret"
        with self.assertRaises(LoginException) as raised:
            parse_cookie_header(f"csrftoken={secret}")

        self.assertNotIn(secret, str(raised.exception))

    def test_rejects_empty_sessionid(self):
        with self.assertRaises(LoginException):
            parse_cookie_header("sessionid=; csrftoken=present")

    def test_rejects_empty_header(self):
        with self.assertRaises(LoginException):
            parse_cookie_header(" ; ; ")


if __name__ == "__main__":
    unittest.main()
