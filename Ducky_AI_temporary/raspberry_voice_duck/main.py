import logging
import time
from typing import Any

from audio_io import play_audio, record_audio
from config import (
    CHAT_BACKEND,
    COMMAND_POLL_SECONDS,
    INPUT_AUDIO_PATH,
    NANO_RECONNECT_SECONDS,
    NANO_SERIAL_BAUD_RATE,
    NANO_SERIAL_PORT,
    NANO_SERIAL_TIMEOUT_SECONDS,
    NO_SPEECH_MESSAGE,
    OFFLINE_TIMEOUT_SECONDS,
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
from local_llm import save_local_conversation_log
from nano_serial import NanoSerialAdapter
from server_client import (
    complete_command,
    fetch_next_command,
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


def _nano_led_label_for_state(state: LedState) -> str:
    if state in {LedState.RECORDING, LedState.TRANSCRIBING}:
        return "LISTENING"
    if state == LedState.SPEAKING:
        return "RESPONDING"
    if state == LedState.OFFLINE:
        return "OFFLINE"
    return "IDLE"


def _report_state(state: LedState) -> None:
    global _is_offline_led_active
    if nano_adapter is not None:
        nano_adapter.send_led_state(_nano_led_label_for_state(state))
    _is_offline_led_active = state == LedState.OFFLINE
    report_iot_state(state)


def _handle_connection_state(online: bool, failed_seconds: float) -> None:
    global _is_offline_led_active
    if nano_adapter is None:
        return
    if online:
        # 정상 통신 복구 시점에는 현재 음성 처리 상태를 덮어쓰지 않는다.
        # 다음 _report_state 호출에서 자연스럽게 LED가 갱신된다.
        _is_offline_led_active = False
        return

    if failed_seconds >= OFFLINE_TIMEOUT_SECONDS and not _is_offline_led_active:
        nano_adapter.send_led_state("OFFLINE")
        _is_offline_led_active = True


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
        play_audio(RESPONSE_AUDIO_PATH)
    except Exception as exc:
        logger.exception("TTS or playback failed")
        _report_state(LedState.ERROR)
        _report_error("TTS_PLAYBACK_FAILED", exc)
        print(TTS_FAILURE_MESSAGE)
        return False

    return True


def run_once(conversation_id: int | None = None) -> bool:
    _report_state(LedState.RECORDING)
    print("듣고 있어요. 문제를 설명해 주세요.")
    _speak("듣고 있어요. 문제를 설명해 주세요.")

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
        _report_state(LedState.LOGGING)
        if CHAT_BACKEND == "local":
            saved = save_local_conversation_log(
                user_message=user_text,
                assistant_message=response_text,
                stt_success=stt_success,
                tts_success=tts_success,
            )
        else:
            conversation_id = server_response.get("conversationId")
            if conversation_id is not None and not isinstance(conversation_id, int):
                logger.warning("Invalid conversationId from server: %r", conversation_id)
                conversation_id = None

            saved = save_conversation_log(
                conversation_id=conversation_id,
                user_message=user_text,
                assistant_message=response_text,
                stt_success=stt_success,
                tts_success=tts_success,
            )
        if not saved:
            logger.info("Conversation log was not saved.")
            _report_error("CONVERSATION_LOG_SAVE_FAILED", "Conversation log save failed")

    _report_state(LedState.IDLE)
    return True


def run_command_loop() -> None:
    _report_state(LedState.IDLE)
    while True:
        try:
            command = fetch_next_command()
            if not command:
                time.sleep(COMMAND_POLL_SECONDS)
                continue

            command_id = _command_id_from_command(command)
            command_type = command.get("commandType") or command.get("command_type")
            if command_type != "START_RECORDING":
                logger.warning("Unknown command type from server: %r", command_type)
                if command_id is not None:
                    complete_command(command_id, False, f"Unknown command type: {command_type}")
                continue

            try:
                run_once(conversation_id=_conversation_id_from_command(command))
            except Exception as exc:
                logger.exception("Command execution failed")
                _report_state(LedState.ERROR)
                _report_error("COMMAND_EXECUTION_FAILED", exc)
                if command_id is not None:
                    complete_command(command_id, False, str(exc))
                print(f"Command failed: {exc}")
                _speak("오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                continue

            if command_id is not None:
                complete_command(command_id, True)
        except KeyboardInterrupt:
            _report_state(LedState.STOPPED)
            print("?꾨줈洹몃옩??醫낅즺?⑸땲??")
            break


def run_button_loop() -> None:
    _report_state(LedState.IDLE)
    while True:
        try:
            if nano_adapter is None:
                time.sleep(0.2)
                continue

            if not nano_adapter.read_button_event():
                time.sleep(0.05)
                continue

            logger.info("Nano button pressed; starting voice run")
            run_once()
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

    if TRIGGER_MODE == "button":
        nano_adapter = NanoSerialAdapter(
            port=NANO_SERIAL_PORT,
            baud_rate=NANO_SERIAL_BAUD_RATE,
            timeout_seconds=NANO_SERIAL_TIMEOUT_SECONDS,
            reconnect_seconds=NANO_RECONNECT_SECONDS,
        )
        run_button_loop()
        return

    if TRIGGER_MODE == "command":
        run_command_loop()
        return

    if not RUN_CONTINUOUSLY:
        run_once()
        _report_state(LedState.STOPPED)
        return

    while True:
        try:
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
