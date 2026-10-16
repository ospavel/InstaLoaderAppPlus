"""Progress reporting helpers."""

from __future__ import annotations

from typing import Any, Callable, Dict, Optional


def report(callback: Optional[Callable], **payload: Any) -> None:
    if callback is None:
        return
    callback(payload)


def merge_progress(
    callback: Optional[Callable],
    *,
    phase: str,
    total: Optional[int] = None,
    completed: int = 0,
    failed: int = 0,
    skipped: int = 0,
    current: Optional[str] = None,
    message: Optional[str] = None,
    transfer_file: Optional[str] = None,
    transfer_completed_bytes: Optional[int] = None,
    transfer_total_bytes: Optional[int] = None,
) -> None:
    remaining = None
    if total is not None:
        remaining = max(total - completed - failed - skipped, 0)
    report(
        callback,
        phase=phase,
        total=total,
        completed=completed,
        failed=failed,
        skipped=skipped,
        remaining=remaining,
        current=current or "",
        message=message or "",
        transfer_file=transfer_file or "",
        transfer_completed_bytes=transfer_completed_bytes,
        transfer_total_bytes=transfer_total_bytes,
    )
