import logging
import time
from typing import Any, Callable

import requests

from config import (
    CHAT_BACKEND,
    COMMAND_POLL_SECONDS,
    DEVICE_ID,
    IOT_EVENT_TIMEOUT_SECONDS,
    IOT_FAILURE_BACKOFF_SECONDS,
    IOT_REPORTING_ENABLED,
    REQUEST_TIMEOUT_SECONDS,
    SERVER_BASE_URL,
    USER_ID,
)
from led_state import LedState


logger = logging.getLogger(__name__)
_iot_backoff_until = 0.0
_connection_state_callback: Callable[[bool, float], None] | None = None
_reconnect_callback: Callable[[], None] | None = None
_last_server_ok_at = time.monotonic()
_server_online = True


class ServerClientError(RuntimeError):
    """Raised when the Spring Boot server response is unusable."""


def _url(path: str) -> str:
    return f"{SERVER_BASE_URL}{path}"


def set_connection_state_callback(callback: Callable[[bool, float], None] | None) -> None:
    global _connection_state_callback
    _connection_state_callback = callback


def set_reconnect_callback(callback: Callable[[], None] | None) -> None:
    global _reconnect_callback
    _reconnect_callback = callback


def _notify_connection_state(online: bool, failed_seconds: float) -> None:
    if _connection_state_callback is None:
        return
    try:
        _connection_state_callback(online, failed_seconds)
    except Exception:
        logger.warning("Connection state callback failed", exc_info=True)


def _mark_server_ok() -> None:
    global _last_server_ok_at, _server_online
    was_offline = not _server_online
    _last_server_ok_at = time.monotonic()
    if not _server_online:
        _server_online = True
    _notify_connection_state(True, 0.0)
    if was_offline and _reconnect_callback is not None:
        try:
            _reconnect_callback()
        except Exception:
            logger.warning("Reconnect callback failed", exc_info=True)


def _mark_server_fail() -> None:
    global _server_online
    failed_seconds = max(0.0, time.monotonic() - _last_server_ok_at)
    if _server_online:
        _server_online = False
    _notify_connection_state(False, failed_seconds)


def is_server_online() -> bool:
    return _server_online


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
        _mark_server_fail()
        logger.info("%s failed; telemetry will back off", action, exc_info=True)
        return False

    _mark_server_ok()
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
        _mark_server_fail()
        return False

    is_healthy = isinstance(data, dict) and data.get("status") == "ok"
    if is_healthy:
        _mark_server_ok()
    else:
        _mark_server_fail()
    return is_healthy


def should_use_server_chat() -> bool:
    if CHAT_BACKEND == "server":
        return True
    if CHAT_BACKEND == "local":
        return False
    return check_server_health()


def fetch_device_profile() -> dict[str, str] | None:
    try:
        response = requests.get(
            _url("/api/duck/device-profile"),
            params={"deviceId": DEVICE_ID},
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
    except (requests.RequestException, ValueError):
        logger.info("Device profile fetch failed", exc_info=True)
        _mark_server_fail()
        return None

    if not isinstance(data, dict):
        return None

    profile = {
        "processing": str(data.get("processing", "")),
        "expression": str(data.get("expression", "")),
        "understanding": str(data.get("understanding", "")),
    }
    if not any(profile.values()):
        return None

    _mark_server_ok()
    return profile


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
    Report an error to the BE without interrupting the voice loop.
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


def fetch_next_command() -> dict[str, Any] | None:
    """
    Claim the next pending server command for this device.
    """
    try:
        response = requests.post(
            _url("/api/iot/commands/next"),
            json={"device_id": DEVICE_ID},
            timeout=max(IOT_EVENT_TIMEOUT_SECONDS, COMMAND_POLL_SECONDS),
        )
        response.raise_for_status()
        payload = response.json()
    except (requests.RequestException, ValueError) as exc:
        logger.info("Command poll failed: %s", exc)
        _mark_server_fail()
        return None

    data = payload.get("data") if isinstance(payload, dict) and "data" in payload else payload
    if not isinstance(data, dict) or not data.get("available"):
        return None

    _mark_server_ok()
    return data


def complete_command(command_id: int, success: bool, error_message: str | None = None) -> bool:
    """
    Report command completion to the server.
    """
    payload: dict[str, Any] = {
        "device_id": DEVICE_ID,
        "success": success,
    }
    if error_message:
        payload["errorMessage"] = error_message[:500]

    try:
        response = requests.post(
            _url(f"/api/iot/commands/{command_id}/complete"),
            json=payload,
            timeout=IOT_EVENT_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
    except requests.RequestException:
        logger.warning("Command completion report failed", exc_info=True)
        _mark_server_fail()
        return False

    _mark_server_ok()
    return True


def send_message_to_server(user_text: str, conversation_id: int | None = None) -> dict[str, Any]:
    """
    Send transcribed user text to the Spring Boot server and return JSON.
    """
    payload: dict[str, Any] = {
        "deviceId": DEVICE_ID,
        "userId": USER_ID,
        "inputType": "voice",
        "message": user_text,
    }
    if conversation_id is not None:
        payload["conversationId"] = conversation_id

    try:
        response = requests.post(
            _url("/api/duck/conversation"),
            json=payload,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
    except requests.RequestException as exc:
        _mark_server_fail()
        raise ServerClientError(f"대화 API 요청 실패: {exc}") from exc
    except ValueError as exc:
        _mark_server_fail()
        raise ServerClientError("대화 API 응답이 JSON 형식이 아닙니다.") from exc

    if not isinstance(data, dict):
        raise ServerClientError("대화 API 응답이 JSON 객체가 아닙니다.")

    message = data.get("message")
    if not isinstance(message, str) or not message.strip():
        raise ServerClientError("대화 API 응답에 message가 없습니다.")

    _mark_server_ok()
    return data


def sync_conversation_turn(
    turn_id: str,
    user_text: str,
    assistant_text: str,
    *,
    stt_success: bool,
    tts_success: bool,
    conversation_id: int | None = None,
) -> dict[str, Any] | None:
    payload: dict[str, Any] = {
        "deviceId": DEVICE_ID,
        "userId": USER_ID,
        "clientTurnId": turn_id,
        "userMessage": user_text,
        "assistantMessage": assistant_text,
        "inputType": "voice",
        "sttSuccess": stt_success,
        "ttsSuccess": tts_success,
    }
    if conversation_id is not None:
        payload["conversationId"] = conversation_id

    try:
        response = requests.post(
            _url("/api/duck/conversation/sync"),
            json=payload,
            timeout=REQUEST_TIMEOUT_SECONDS,
        )
        response.raise_for_status()
        data = response.json()
    except requests.RequestException:
        logger.warning("Conversation sync failed for turn %s", turn_id, exc_info=True)
        _mark_server_fail()
        return None
    except ValueError:
        logger.warning("Conversation sync returned invalid JSON for turn %s", turn_id)
        _mark_server_fail()
        return None

    if not isinstance(data, dict):
        return None

    _mark_server_ok()
    return data


def flush_pending_queue() -> int:
    from conversation_queue import list_pending_turns, remove_turn

    synced_count = 0
    active_conversation_id: int | None = None

    for entry in list_pending_turns():
        turn_id = entry.get("turnId")
        user_text = entry.get("userText")
        assistant_text = entry.get("assistantText")
        if not isinstance(turn_id, str) or not user_text or not assistant_text:
            continue

        conversation_id = entry.get("conversationId", active_conversation_id)
        if isinstance(conversation_id, str) and conversation_id.isdigit():
            conversation_id = int(conversation_id)
        if not isinstance(conversation_id, int):
            conversation_id = active_conversation_id

        result = sync_conversation_turn(
            turn_id,
            str(user_text),
            str(assistant_text),
            stt_success=bool(entry.get("sttSuccess", True)),
            tts_success=bool(entry.get("ttsSuccess", True)),
            conversation_id=conversation_id,
        )
        if result is None:
            break

        raw_conversation_id = result.get("conversationId")
        if isinstance(raw_conversation_id, int):
            active_conversation_id = raw_conversation_id
        elif isinstance(raw_conversation_id, str) and raw_conversation_id.isdigit():
            active_conversation_id = int(raw_conversation_id)

        remove_turn(turn_id)
        synced_count += 1

    if synced_count:
        logger.info("Synced %s pending offline turn(s) to server", synced_count)
    return synced_count


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
        _mark_server_fail()
        return False

    _mark_server_ok()
    return True
