import logging
import time
from typing import Any

import requests

from config import (
    DEVICE_ID,
    IOT_EVENT_TIMEOUT_SECONDS,
    IOT_FAILURE_BACKOFF_SECONDS,
    IOT_REPORTING_ENABLED,
    LEARNING_TYPE,
    REQUEST_TIMEOUT_SECONDS,
    SERVER_BASE_URL,
    USER_ID,
)
from led_state import LedState


logger = logging.getLogger(__name__)
_iot_backoff_until = 0.0


class ServerClientError(RuntimeError):
    """Raised when the Spring Boot server response is unusable."""


def _url(path: str) -> str:
    return f"{SERVER_BASE_URL}{path}"


def _state_value(state: LedState | str) -> str:
    if isinstance(state, LedState):
        return state.value
    return str(state)


def _can_send_iot_event() -> bool:
    return IOT_REPORTING_ENABLED and time.monotonic() >= _iot_backoff_until


def _post_iot_event(path: str, payload: dict[str, Any], action: str) -> bool:
    global _iot_backoff_until

    if not _can_send_iot_event():
        return False

    try:
        response = requests.post(
            _url(path),
            json=payload,
            timeout=IOT_EVENT_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except requests.RequestException:
        _iot_backoff_until = time.monotonic() + IOT_FAILURE_BACKOFF_SECONDS
        logger.info("%s failed; telemetry will back off", action, exc_info=True)
        return False

    return True


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


def report_iot_state(state: LedState | str) -> bool:
    """
    Report the current Raspberry runtime state to the BE.

    This is telemetry only. A failure never stops the voice loop.
    """
    return _post_iot_event(
        "/api/iot/state",
        {
            "device_id": DEVICE_ID,
            "current_state": _state_value(state),
        },
        "IoT state report",
    )


def report_tts_complete(message_id: int | None = None) -> bool:
    """
    Report that TTS/playback completed.
    """
    payload: dict[str, Any] = {"device_id": DEVICE_ID}
    if message_id is not None:
        payload["message_id"] = message_id

    return _post_iot_event("/api/iot/tts-complete", payload, "IoT TTS complete report")


def report_iot_error(error_code: str, error_message: str) -> bool:
    """
    Report an error to the BE without interrupting local fallback behavior.
    """
    return _post_iot_event(
        "/api/iot/error",
        {
            "device_id": DEVICE_ID,
            "errorCode": error_code,
            "errorMessage": error_message,
        },
        "IoT error report",
    )


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
