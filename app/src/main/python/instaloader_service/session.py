"""Instagram session management."""

from __future__ import annotations

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


def apply_session(loader: instaloader.Instaloader, username: Optional[str], session_dir: Optional[str]) -> None:
    if not username or not session_dir:
        return
    session_file = _session_path(session_dir, username.strip())
    if os.path.exists(session_file):
        loader.load_session_from_file(username.strip(), filename=session_file)
