import json
import logging
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from config import DEVICE_ID, PENDING_QUEUE_PATH


logger = logging.getLogger(__name__)


def _queue_path() -> Path:
    path = Path(PENDING_QUEUE_PATH)
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def _read_entries() -> list[dict[str, Any]]:
    path = _queue_path()
    if not path.exists():
        return []

    entries: list[dict[str, Any]] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            entry = json.loads(line)
        except json.JSONDecodeError:
            logger.warning("Skipping invalid queue line: %s", line[:120])
            continue
        if isinstance(entry, dict):
            entries.append(entry)
    return entries


def _write_entries(entries: list[dict[str, Any]]) -> None:
    path = _queue_path()
    if not entries:
        if path.exists():
            path.unlink()
        return

    lines = [json.dumps(entry, ensure_ascii=False) for entry in entries]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def enqueue_turn(
    user_text: str,
    assistant_text: str,
    *,
    stt_success: bool,
    tts_success: bool,
    conversation_id: int | None = None,
) -> str:
    turn_id = str(uuid.uuid4())
    entry = {
        "turnId": turn_id,
        "deviceId": DEVICE_ID,
        "conversationId": conversation_id,
        "userText": user_text,
        "assistantText": assistant_text,
        "sttSuccess": stt_success,
        "ttsSuccess": tts_success,
        "createdAt": datetime.now(timezone.utc).isoformat(),
    }
    entries = _read_entries()
    entries.append(entry)
    _write_entries(entries)
    logger.info("Queued offline turn %s for sync", turn_id)
    return turn_id


def list_pending_turns() -> list[dict[str, Any]]:
    return _read_entries()


def remove_turn(turn_id: str) -> None:
    entries = [entry for entry in _read_entries() if entry.get("turnId") != turn_id]
    _write_entries(entries)


def pending_count() -> int:
    return len(_read_entries())
