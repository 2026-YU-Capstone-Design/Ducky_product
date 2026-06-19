import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests

from config import (
    LOCAL_LLM_SYSTEM_PROMPT,
    LOCAL_LOG_PATH,
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    REQUEST_TIMEOUT_SECONDS,
)


logger = logging.getLogger(__name__)


class LocalLLMError(RuntimeError):
    """Raised when the local Ollama response is unusable."""


def _chat_url() -> str:
    return f"{OLLAMA_BASE_URL.rstrip('/')}/api/chat"


def check_ollama_health() -> bool:
    try:
        response = requests.get(
            f"{OLLAMA_BASE_URL.rstrip('/')}/api/tags",
            timeout=5.0,
        )
        response.raise_for_status()
        return True
    except requests.RequestException:
        return False


def send_message_to_local_llm(user_text: str) -> dict[str, Any]:
    payload = {
        "model": OLLAMA_MODEL,
        "stream": False,
        "messages": [
            {"role": "system", "content": LOCAL_LLM_SYSTEM_PROMPT},
            {"role": "user", "content": user_text},
        ],
    }

    try:
        response = requests.post(
            _chat_url(),
            json=payload,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
    except requests.RequestException as exc:
        raise LocalLLMError(f"Ollama 요청 실패: {exc}") from exc
    except ValueError as exc:
        raise LocalLLMError("Ollama 응답이 JSON 형식이 아닙니다.") from exc

    message = data.get("message", {})
    content = message.get("content") if isinstance(message, dict) else None
    if not isinstance(content, str) or not content.strip():
        raise LocalLLMError("Ollama 응답에 message.content가 없습니다.")

    return {
        "message": content.strip(),
        "shouldSaveLog": True,
        "source": "ollama",
        "model": OLLAMA_MODEL,
    }


def save_local_conversation_log(
    user_message: str,
    assistant_message: str,
    stt_success: bool,
    tts_success: bool,
) -> bool:
    log_path = Path(LOCAL_LOG_PATH)
    log_path.parent.mkdir(parents=True, exist_ok=True)

    entry = {
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "userMessage": user_message,
        "assistantMessage": assistant_message,
        "sttSuccess": stt_success,
        "ttsSuccess": tts_success,
        "model": OLLAMA_MODEL,
    }

    try:
        with log_path.open("a", encoding="utf-8") as log_file:
            log_file.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except OSError:
        logger.warning("Local conversation log save failed", exc_info=True)
        return False

    return True
