import logging
import time
from typing import Any

from audio_io import play_audio, record_audio
from config import (
    COMMAND_POLL_SECONDS,
    CONNECTIVITY_POLL_SECONDS,
    INPUT_AUDIO_PATH,
    NANO_RECONNECT_SECONDS,
    NANO_SERIAL_BAUD_RATE,
    NANO_SERIAL_PORT,
    NANO_SERIAL_TIMEOUT_SECONDS,
    NO_SPEECH_MESSAGE,
    OFFLINE_TIMEOUT_SECONDS,
    RECORD_DELAY_AFTER_SPEAK_SECONDS,
    RECORD_SECONDS,
    RESPONSE_AUDIO_PATH,
    RUN_CONTINUOUSLY,
    SERVER_FAILURE_MESSAGE,
    STT_FAILURE_MESSAGE,
    TTS_FAILURE_MESSAGE,
    TRIGGER_MODE,
    ensure_runtime_dirs,
    validate_required_environment,
)
from led_state import LedState
from nano_serial import NanoSerialAdapter
from server_client import (
    check_server_health,
    complete_command,
    fetch_active_conversation_id,
    fetch_next_command,
    get_offline_duration,
    is_server_online,
    report_iot_error,
    report_iot_state,
    report_tts_complete,
    save_conversation_log,
    send_message_to_server,
    set_connection_state_callback,
)
from stt import transcribe_audio
from tts import synthesize_speech


logger = logging.getLogger(__name__)
nano_adapter: NanoSerialAdapter | None = None
_is_offline_led_active = False
_active_led_states = {
    LedState.RECORDING,
    LedState.TRANSCRIBING,
    LedState.SPEAKING,
}


def _should_show_offline_led() -> bool:
    return (
        not is_server_online()
        and get_offline_duration() >= OFFLINE_TIMEOUT_SECONDS
    )


def _nano_led_label_for_state(state: LedState) -> str:
    if state in {LedState.RECORDING, LedState.TRANSCRIBING}:
        return "LISTENING"
    if state == LedState.SPEAKING:
        return "RESPONDING"
    if state == LedState.OFFLINE:
        return "OFFLINE"
    if _should_show_offline_led() and state not in _active_led_states:
        return "OFFLINE"
    return "IDLE"


def _report_state(state: LedState) -> None:
    global _is_offline_led_active
    if nano_adapter is not None:
        led_label = _nano_led_label_for_state(state)
        nano_adapter.send_led_state(led_label)
        _is_offline_led_active = led_label == "OFFLINE"
    report_iot_state(state)


def _handle_connection_state(online: bool, failed_seconds: float) -> None:
    global _is_offline_led_active
    if nano_adapter is None:
        return
    if online:
        if _is_offline_led_active:
            nano_adapter.send_led_state("IDLE")
        _is_offline_led_active = False
        return

    if failed_seconds >= OFFLINE_TIMEOUT_SECONDS:
        nano_adapter.send_led_state("OFFLINE")
        _is_offline_led_active = True


def _init_nano_adapter() -> None:
    global nano_adapter
    nano_adapter = NanoSerialAdapter(
        port=NANO_SERIAL_PORT,
        baud_rate=NANO_SERIAL_BAUD_RATE,
        timeout_seconds=NANO_SERIAL_TIMEOUT_SECONDS,
        reconnect_seconds=NANO_RECONNECT_SECONDS,
    )


def _poll_connectivity(last_check_at: float) -> float:
    now = time.monotonic()
    if now - last_check_at < CONNECTIVITY_POLL_SECONDS:
        return last_check_at

    check_server_health()
    return now


def _report_error(error_code: str, error: object) -> None:
    report_iot_error(error_code, str(error)[:500])


def _message_id_from_response(server_response: dict[str, Any]) -> int | None:
    raw_message_id = server_response.get("messageId") or server_response.get(
        "assistantMessageId",
    )
    if isinstance(raw_message_id, int):
        return raw_message_id
    if isinstance(raw_message_id, str) and raw_message_id.isdigit():
        return int(raw_message_id)
    return None


def _int_from_value(value: object) -> int | None:
    if isinstance(value, int):
        return value
    if isinstance(value, str) and value.isdigit():
        return int(value)
    return None


def _command_id_from_command(command: dict[str, Any]) -> int | None:
    return _int_from_value(command.get("commandId") or command.get("command_id"))


def _conversation_id_from_command(command: dict[str, Any]) -> int | None:
    return _int_from_value(command.get("conversationId") or command.get("conversation_id"))


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )


def _get_server_response(user_text: str, conversation_id: int | None = None) -> tuple[str, dict[str, Any]]:
    try:
        server_response = send_message_to_server(user_text, conversation_id=conversation_id)
    except Exception as exc:
        logger.warning("Server conversation failed: %s", exc)
        _report_error("SERVER_CONVERSATION_FAILED", exc)
        raise

    return server_response["message"].strip(), server_response


def _speak(text: str) -> bool:
    try:
        synthesize_speech(text, RESPONSE_AUDIO_PATH)
        logger.info("TTS 파일 생성 완료, 스피커 재생 시작")
        play_audio(RESPONSE_AUDIO_PATH)
    except Exception as exc:
        logger.exception("TTS or playback failed")
        _report_state(LedState.ERROR)
        _report_error("TTS_PLAYBACK_FAILED", exc)
        print(TTS_FAILURE_MESSAGE)
        return False

    return True


def run_once(conversation_id: int | None = None) -> int | None:
    resolved_conversation_id = conversation_id
    try:
        print("듣고 있어요. 문제를 설명해 주세요.")
        _report_state(LedState.SPEAKING)
        _speak("듣고 있어요. 문제를 설명해 주세요.")
        if RECORD_DELAY_AFTER_SPEAK_SECONDS > 0:
            time.sleep(RECORD_DELAY_AFTER_SPEAK_SECONDS)

        _report_state(LedState.RECORDING)
        record_audio(INPUT_AUDIO_PATH, duration=RECORD_SECONDS)

        stt_success = True
        _report_state(LedState.TRANSCRIBING)
        user_text = transcribe_audio(INPUT_AUDIO_PATH)
        print(f"사용자: {user_text}")

        server_response: dict[str, Any] = {}

        if user_text == STT_FAILURE_MESSAGE:
            stt_success = False
            _report_state(LedState.ERROR)
            _report_error("STT_FAILED", "Speech-to-text failed")
            response_text = STT_FAILURE_MESSAGE
        elif not user_text.strip():
            stt_success = False
            response_text = NO_SPEECH_MESSAGE
        else:
            _report_state(LedState.THINKING)
            try:
                response_text, server_response = _get_server_response(user_text, conversation_id=conversation_id)
            except Exception:
                _report_state(LedState.ERROR)
                response_text = SERVER_FAILURE_MESSAGE

        print(f"서버: {response_text}")

        _report_state(LedState.SPEAKING)
        tts_success = _speak(response_text)
        if server_response and tts_success:
            report_tts_complete(_message_id_from_response(server_response))

        if server_response and server_response.get("shouldSaveLog", True):
            resolved_conversation_id = _int_from_value(server_response.get("conversationId")) or conversation_id

            _report_state(LedState.LOGGING)
            saved = save_conversation_log(
                conversation_id=resolved_conversation_id,
                user_message=user_text,
                assistant_message=response_text,
                stt_success=stt_success,
                tts_success=tts_success,
            )
            if not saved:
                logger.info("Conversation log was not saved.")
                _report_error("CONVERSATION_LOG_SAVE_FAILED", "Conversation log save failed")

        return resolved_conversation_id
    finally:
        _report_state(LedState.IDLE)


def _execute_start_recording_command(command: dict[str, Any]) -> int | None:
    command_id = _command_id_from_command(command)
    command_type = command.get("commandType") or command.get("command_type")
    conversation_id = _conversation_id_from_command(command)
    if command_type != "START_RECORDING":
        logger.warning("Unknown command type from server: %r", command_type)
        if command_id is not None:
            complete_command(command_id, False, f"Unknown command type: {command_type}")
        return conversation_id

    try:
        resolved_conversation_id = run_once(conversation_id=conversation_id)
    except Exception as exc:
        logger.exception("Command execution failed")
        _report_state(LedState.ERROR)
        _report_error("COMMAND_EXECUTION_FAILED", exc)
        if command_id is not None:
            complete_command(command_id, False, str(exc))
        print(f"Command failed: {exc}")
        _speak("오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
        _report_state(LedState.IDLE)
        return conversation_id

    if command_id is not None:
        complete_command(command_id, True)
    return resolved_conversation_id or conversation_id


def run_command_loop() -> None:
    _report_state(LedState.IDLE)
    last_connectivity_check = 0.0
    while True:
        try:
            last_connectivity_check = _poll_connectivity(last_connectivity_check)
            command = fetch_next_command()
            if not command:
                time.sleep(COMMAND_POLL_SECONDS)
                continue

            _execute_start_recording_command(command)
        except KeyboardInterrupt:
            _report_state(LedState.STOPPED)
            print("?꾨줈洹몃옩??醫낅즺?⑸땲??")
            break


def run_button_loop() -> None:
    _report_state(LedState.IDLE)
    last_connectivity_check = 0.0
    last_command_poll = 0.0
    active_conversation_id: int | None = None
    while True:
        try:
            last_connectivity_check = _poll_connectivity(last_connectivity_check)

            now = time.monotonic()
            if now - last_command_poll >= COMMAND_POLL_SECONDS:
                last_command_poll = now
                command = fetch_next_command()
                if command:
                    logger.info("Server recording command received")
                    active_conversation_id = _execute_start_recording_command(command) or active_conversation_id
                    continue

            if nano_adapter is None:
                time.sleep(0.2)
                continue

            if not nano_adapter.read_button_event():
                time.sleep(0.05)
                continue

            logger.info("Nano button pressed; starting voice run")
            fetched_conversation_id = fetch_active_conversation_id()
            if fetched_conversation_id is not None:
                active_conversation_id = fetched_conversation_id
            active_conversation_id = run_once(conversation_id=active_conversation_id) or active_conversation_id
        except KeyboardInterrupt:
            _report_state(LedState.STOPPED)
            print("프로그램을 종료합니다.")
            break
        except Exception as exc:
            logger.exception("Button-triggered run failed")
            _report_state(LedState.ERROR)
            _report_error("BUTTON_TRIGGER_FAILED", exc)
            print(f"오류 발생: {exc}")
            _speak("오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
            _report_state(LedState.IDLE)


def main() -> None:
    global nano_adapter
    configure_logging()
    ensure_runtime_dirs()
    set_connection_state_callback(_handle_connection_state)
    _report_state(LedState.BOOTING)

    try:
        validate_required_environment()
    except RuntimeError as exc:
        _report_state(LedState.ERROR)
        _report_error("CONFIGURATION_ERROR", exc)
        print(exc)
        return

    _init_nano_adapter()
    check_server_health()

    if TRIGGER_MODE == "button":
        run_button_loop()
        return

    if TRIGGER_MODE == "command":
        run_command_loop()
        return

    if not RUN_CONTINUOUSLY:
        run_once()
        _report_state(LedState.STOPPED)
        return

    last_connectivity_check = 0.0
    while True:
        try:
            last_connectivity_check = _poll_connectivity(last_connectivity_check)
            run_once()
        except KeyboardInterrupt:
            _report_state(LedState.STOPPED)
            print("프로그램을 종료합니다.")
            break
        except Exception as exc:
            logger.exception("Unexpected loop error")
            _report_state(LedState.ERROR)
            _report_error("UNEXPECTED_LOOP_ERROR", exc)
            print(f"오류 발생: {exc}")
            _speak("오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")


if __name__ == "__main__":
    main()
