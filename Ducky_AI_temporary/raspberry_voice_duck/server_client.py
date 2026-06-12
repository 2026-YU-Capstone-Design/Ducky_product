import logging
from typing import Any

import requests

from config import (
    DEVICE_ID,
    LEARNING_TYPE,
    REQUEST_TIMEOUT_SECONDS,
    SERVER_BASE_URL,
    USER_ID,
)


logger = logging.getLogger(__name__)


class ServerClientError(RuntimeError):
    """Raised when the Spring Boot server response is unusable."""


def _url(path: str) -> str:
    return f"{SERVER_BASE_URL}{path}"


def check_server_health() -> bool:
    """
    Return True only when the server health endpoint responds with status ok.
    """
    try:
        response = requests.get(_url("/api/health"), timeout=REQUEST_TIMEOUT_SECONDS)
        response.raise_for_status()
        data = response.json()
    except (requests.RequestException, ValueError) as exc:
        logger.info("Server health check failed: %s", exc)
        return False

    return isinstance(data, dict) and data.get("status") == "ok"


def send_message_to_server(user_text: str) -> dict[str, Any]:
    """
    Send transcribed user text to the Spring Boot server and return JSON.
    """
    payload = {
        "deviceId": DEVICE_ID,
        "userId": USER_ID,
        "inputType": "voice",
        "message": user_text,
        "learningType": LEARNING_TYPE,
    }

    try:
        response = requests.post(
            _url("/api/duck/conversation"),
            json=payload,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
    except requests.RequestException as exc:
        raise ServerClientError(f"대화 API 요청 실패: {exc}") from exc
    except ValueError as exc:
        raise ServerClientError("대화 API 응답이 JSON 형식이 아닙니다.") from exc

    if not isinstance(data, dict):
        raise ServerClientError("대화 API 응답이 JSON 객체가 아닙니다.")

    message = data.get("message")
    if not isinstance(message, str) or not message.strip():
        raise ServerClientError("대화 API 응답에 message가 없습니다.")

    return data


def save_conversation_log(
    conversation_id: int | None,
    user_message: str,
    assistant_message: str,
    stt_success: bool,
    tts_success: bool,
) -> bool:
    """
    Save a conversation log to the server. Return False instead of raising.
    """
    payload: dict[str, Any] = {
        "deviceId": DEVICE_ID,
        "userId": USER_ID,
        "userMessage": user_message,
        "assistantMessage": assistant_message,
        "sttSuccess": stt_success,
        "ttsSuccess": tts_success,
    }

    if conversation_id is not None:
        payload["conversationId"] = conversation_id

    try:
        response = requests.post(
            _url("/api/duck/conversation/logs"),
            json=payload,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except requests.RequestException:
        logger.warning("Conversation log save failed", exc_info=True)
        return False

    return True
