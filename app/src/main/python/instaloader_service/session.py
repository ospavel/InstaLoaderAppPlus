"""Instagram session management."""

from __future__ import annotations

import json
import os
from typing import Optional

import instaloader
from instaloader.exceptions import LoginException, TwoFactorAuthRequiredException

_PENDING_LOADER: Optional[instaloader.Instaloader] = None


def parse_cookie_header(cookie_header: str) -> dict[str, str]:
    """Parse Android's CookieManager header without exposing cookie values in errors."""
    cookies: dict[str, str] = {}
    for segment in cookie_header.split(";"):
        name, separator, value = segment.strip().partition("=")
        name = name.strip()
        if not separator or not name:
            continue
        cookies[name] = value.strip()

    if not cookies.get("sessionid"):
        raise LoginException("Instagram session cookies are missing or incomplete.")
    return cookies


def verify_browser_session(cookie_header: str, user_agent: str) -> str:
    """Verify WebView cookies and return a JSON session snapshot."""
    cookies = parse_cookie_header(cookie_header)
    if not user_agent or not user_agent.strip():
        raise LoginException("Could not read the Instagram WebView browser identity. Try again.")

    try:
        loader = instaloader.Instaloader(quiet=True, user_agent=user_agent)
        loader.context.update_cookies(cookies)
        username = loader.test_login()
    except Exception:
        raise LoginException(
            "Instagram session could not be checked. Check your connection and try again."
        ) from None

    if not username:
        raise LoginException(
            "Instagram did not confirm the login. Finish signing in on the Instagram page and try again."
        )

    try:
        loader.load_session(username, loader.context.save_session())
        normalized_cookies = loader.save_session()
    except Exception:
        raise LoginException(
            "Instagram confirmed the login, but the session could not be imported. Try again."
        ) from None

    if not normalized_cookies.get("sessionid"):
        raise LoginException("Instagram returned an incomplete session. Sign in again and retry.")

    return json.dumps(
        {"username": username, "cookies": normalized_cookies},
        separators=(",", ":"),
    )


def legacy_session_json(username: str, session_file: str) -> str:
    """Convert an existing Instaloader pickle into the encrypted-store JSON format."""
    try:
        loader = instaloader.Instaloader(quiet=True)
        loader.load_session_from_file(username, filename=session_file)
        cookies = loader.save_session()
        if not cookies.get("sessionid"):
            raise LoginException("The saved Instagram session is incomplete.")
    except Exception:
        raise LoginException("The saved Instagram session could not be migrated.") from None

    return json.dumps({"username": username, "cookies": cookies}, separators=(",", ":"))


def apply_session(loader: instaloader.Instaloader, username: str, cookies_json: str) -> None:
    """Load encrypted cookie JSON into an Instaloader instance in memory."""
    if not username or not cookies_json:
        return

    try:
        cookies = json.loads(cookies_json)
        if not isinstance(cookies, dict) or not all(
            isinstance(name, str) and isinstance(value, str)
            for name, value in cookies.items()
        ):
            raise ValueError("Invalid cookie data")
        if not cookies.get("sessionid"):
            raise ValueError("Missing session cookie")
        loader.load_session(username, cookies)
    except Exception:
        raise LoginException("The saved Instagram session is invalid. Sign in again and retry.") from None


def _session_path(session_dir: str, username: str) -> str:
    os.makedirs(session_dir, exist_ok=True)
    return os.path.join(session_dir, f"session-{username}")


def login(username: str, password: str, session_dir: str) -> None:
    global _PENDING_LOADER
    loader = instaloader.Instaloader(quiet=True)
    try:
        loader.login(username.strip(), password)
    except TwoFactorAuthRequiredException:
        _PENDING_LOADER = loader
        raise
    loader.save_session_to_file(_session_path(session_dir, username.strip()))
    _PENDING_LOADER = None


def two_factor_login(code: str, username: str, session_dir: str) -> None:
    global _PENDING_LOADER
    loader = _PENDING_LOADER or instaloader.Instaloader(quiet=True)
    loader.two_factor_login(code.strip())
    loader.save_session_to_file(_session_path(session_dir, username.strip()))
    _PENDING_LOADER = None


def test_session(username: str, session_dir: str) -> bool:
    loader = instaloader.Instaloader(quiet=True)
    session_file = _session_path(session_dir, username.strip())
    if not os.path.exists(session_file):
        return False
    loader.load_session_from_file(username.strip(), filename=session_file)
    return loader.test_login() == username.strip()

