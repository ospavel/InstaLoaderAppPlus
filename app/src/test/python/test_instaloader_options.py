"""Tests for per-session Instaloader configuration."""

import unittest
from unittest.mock import patch

from instaloader_service.options import DEFAULT_USER_AGENT, build_instaloader


class InstaloaderOptionsTest(unittest.TestCase):
    def setUp(self):
        self.constructor = patch("instaloader_service.options.instaloader.Instaloader").start()
        self.addCleanup(patch.stopall)
        self.loader = self.constructor.return_value

    def test_manual_user_agent_overrides_captured_session_user_agent(self):
        build_instaloader({
            "user_agent": "Configured manual UA",
            "session_user_agent": "Android WebView UA",
        })

        self.assertEqual(self.constructor.call_args.kwargs["user_agent"], "Configured manual UA")

    def test_captured_session_user_agent_is_used_when_manual_value_is_blank(self):
        build_instaloader({"user_agent": "  ", "session_user_agent": "Android WebView UA"})

        self.assertEqual(self.constructor.call_args.kwargs["user_agent"], "Android WebView UA")

    def test_default_user_agent_is_used_when_manual_and_session_values_are_blank(self):
        build_instaloader({"user_agent": "", "session_user_agent": "  "})

        self.assertEqual(self.constructor.call_args.kwargs["user_agent"], DEFAULT_USER_AGENT)


if __name__ == "__main__":
    unittest.main()
