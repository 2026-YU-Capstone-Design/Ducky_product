import json
import logging
from pathlib import Path

import requests

from config import (
    LEARNING_STYLE_CACHE_PATH,
    LOCAL_LLM_SYSTEM_PROMPT,
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    REQUEST_TIMEOUT_SECONDS,
)
from learning_style import (
    build_learning_style_instructions,
    resolve_learning_style,
)


logger = logging.getLogger(__name__)


def _cache_path() -> Path:
    path = Path(LEARNING_STYLE_CACHE_PATH)
    path.parent.mkdir(parents=True, exist_ok=True)
    return path


def load_cached_learning_style() -> dict[str, str] | None:
    path = _cache_path()
    if not path.exists():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict):
        return None
    return data


def save_cached_learning_style(style: dict[str, str]) -> None:
    _cache_path().write_text(
        json.dumps(style, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def build_system_prompt() -> str:
    cached = load_cached_learning_style()
    style = resolve_learning_style(cached)
    style_block = build_learning_style_instructions(style)
    return f"{LOCAL_LLM_SYSTEM_PROMPT}\n\n학습 스타일 지침:\n{style_block}"


def generate_response(user_text: str) -> str:
    payload = {
        "model": OLLAMA_MODEL,
        "stream": False,
        "messages": [
            {"role": "system", "content": build_system_prompt()},
            {"role": "user", "content": user_text},
        ],
    }

    try:
        response = requests.post(
            f"{OLLAMA_BASE_URL}/api/chat",
            json=payload,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
    except requests.RequestException as exc:
        logger.exception("Ollama request failed")
        raise RuntimeError(f"로컬 LLM 요청 실패: {exc}") from exc
    except ValueError as exc:
        raise RuntimeError("로컬 LLM 응답이 JSON 형식이 아닙니다.") from exc

    message = data.get("message") if isinstance(data, dict) else None
    content = message.get("content") if isinstance(message, dict) else None
    if not isinstance(content, str) or not content.strip():
        raise RuntimeError("로컬 LLM 응답이 비어 있습니다.")

    return content.strip()
