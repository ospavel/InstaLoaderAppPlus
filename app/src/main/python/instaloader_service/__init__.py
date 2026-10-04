"""Instaloader service package for Android Chaquopy integration."""

from instaloader_service.downloader import (
    cancel_download,
    download_with_options,
    get_post_count,
    is_cancelled,
)
__all__ = [
    "cancel_download",
    "download_with_options",
    "get_post_count",
    "is_cancelled",
]
