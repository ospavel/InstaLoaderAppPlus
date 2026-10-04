"""Tests for importing Instagram sessions from Android WebView cookies."""

import json
import pickle
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import instaloader
from instaloader.exceptions import (
    ConnectionException,
    LoginException,
    TooManyRequestsException,
)

from instaloader_service import session


class CookieHeaderTest(unittest.TestCase):
    def test_preserves_equals_and_ignores_empty_segments(self):
        self.assertEqual(
            session.parse_cookie_header("  sessionid=abc== ; csrftoken=xyz== ;; ig_did=q  "),
            {"sessionid": "abc==", "csrftoken": "xyz==", "ig_did": "q"},
        )

    def test_requires_sessionid_without_echoing_cookie_values(self):
        secret = "not-a-session-secret"
        with self.assertRaises(LoginException) as raised:
            session.parse_cookie_header(f"csrftoken={secret}")

        self.assertNotIn(secret, str(raised.exception))

    def test_rejects_empty_sessionid(self):
        with self.assertRaises(LoginException):
            session.parse_cookie_header("sessionid=; csrftoken=present")

    def test_rejects_empty_header(self):
        with self.assertRaises(LoginException):
            session.parse_cookie_header(" ; ; ")


class BrowserSessionTest(unittest.TestCase):
    def test_verifies_browser_cookies_and_returns_normalized_session(self):
        loader = instaloader.Instaloader(quiet=True, user_agent="Android WebView UA")
        with patch.object(loader, "test_login", return_value="alice") as test_login:
            with patch.object(session.instaloader, "Instaloader", return_value=loader) as constructor:
                result = json.loads(
                    session.verify_browser_session(
                        "sessionid=abc==; csrftoken=xyz", "Android WebView UA"
                    )
                )

        self.assertEqual(result["username"], "alice")
        self.assertEqual(result["cookies"]["sessionid"], "abc==")
        self.assertEqual(result["cookies"]["csrftoken"], "xyz")
        self.assertEqual(loader.context.username, "alice")
        self.assertEqual(loader.context._session.headers["User-Agent"], "Android WebView UA")
        test_login.assert_called_once_with()
        constructor.assert_called_once_with(quiet=True, user_agent="Android WebView UA")

    def test_rejects_unverified_browser_cookies_without_leaking_values(self):
        secret = "private-cookie-value"
        loader = instaloader.Instaloader(quiet=True)
        with patch.object(loader, "test_login", return_value=None):
            with patch.object(session.instaloader, "Instaloader", return_value=loader):
                with self.assertRaises(LoginException) as raised:
                    session.verify_browser_session(f"sessionid={secret}", "WebView UA")

        self.assertNotIn(secret, str(raised.exception))

    def test_sanitizes_errors_raised_while_verifying_cookies(self):
        secret = "private-cookie-value"
        loader = instaloader.Instaloader(quiet=True)
        with patch.object(loader, "test_login", side_effect=RuntimeError(secret)):
            with patch.object(session.instaloader, "Instaloader", return_value=loader):
                with self.assertRaises(LoginException) as raised:
                    session.verify_browser_session(f"sessionid={secret}", "WebView UA")

        self.assertNotIn(secret, str(raised.exception))
        self.assertEqual(
            str(raised.exception),
            "Instagram session check failed unexpectedly. Try again later.",
        )

    def test_reports_connection_errors_without_exposing_details(self):
        loader = instaloader.Instaloader(quiet=True)
        with patch.object(loader, "test_login", side_effect=ConnectionException("private detail")):
            with patch.object(session.instaloader, "Instaloader", return_value=loader):
                with self.assertRaises(LoginException) as raised:
                    session.verify_browser_session("sessionid=secret", "WebView UA")

        self.assertEqual(
            str(raised.exception),
            "Instagram could not be reached. Check your connection and try again.",
        )
        self.assertNotIn("private detail", str(raised.exception))

    def test_reports_rate_limits_separately_from_connection_errors(self):
        loader = instaloader.Instaloader(quiet=True)
        with patch.object(loader, "test_login", side_effect=TooManyRequestsException("private detail")):
            with patch.object(session.instaloader, "Instaloader", return_value=loader):
                with self.assertRaises(LoginException) as raised:
                    session.verify_browser_session("sessionid=secret", "WebView UA")

        self.assertEqual(
            str(raised.exception),
            "Instagram is temporarily limiting requests. Wait before trying again.",
        )
        self.assertNotIn("private detail", str(raised.exception))

    def test_sanitizes_instagram_session_rejections(self):
        loader = instaloader.Instaloader(quiet=True)
        with patch.object(loader, "test_login", side_effect=LoginException("feedback_required")):
            with patch.object(session.instaloader, "Instaloader", return_value=loader):
                with self.assertRaises(LoginException) as raised:
                    session.verify_browser_session("sessionid=secret", "WebView UA")

        self.assertEqual(
            str(raised.exception),
            "Instagram rejected the session. Finish any security checks in the Instagram page and try again.",
        )
        self.assertNotIn("feedback_required", str(raised.exception))

    def test_converts_legacy_pickle_without_network_access(self):
        cookies = {"sessionid": "legacy-session", "csrftoken": "legacy-csrf"}
        with tempfile.TemporaryDirectory() as directory:
            session_file = Path(directory) / "session-alice"
            with session_file.open("wb") as stream:
                pickle.dump(cookies, stream)

            result = json.loads(session.legacy_session_json("alice", str(session_file)))

        self.assertEqual(result, {"username": "alice", "cookies": cookies})

    def test_applies_encrypted_session_to_real_instaloader_context(self):
        loader = instaloader.Instaloader(quiet=True)
        cookies_json = json.dumps({"sessionid": "session-value", "csrftoken": "csrf-value"})

        session.apply_session(loader, "alice", cookies_json)

        self.assertEqual(loader.context.username, "alice")
        self.assertEqual(loader.context._session.cookies.get("sessionid"), "session-value")
        self.assertEqual(loader.context._session.headers["X-CSRFToken"], "csrf-value")


if __name__ == "__main__":
    unittest.main()
