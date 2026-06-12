import logging
from typing import Any

from audio_io import record_audio, play_audio
from config import (
    INPUT_AUDIO_PATH,
    NO_SPEECH_MESSAGE,
    RECORD_SECONDS,
    RESPONSE_AUDIO_PATH,
    SERVER_UNAVAILABLE_MESSAGE,
    STT_FAILURE_MESSAGE,
    TTS_FAILURE_MESSAGE,
    ensure_runtime_dirs,
    validate_required_environment,
)
from duck_prompt import generate_local_duck_response
from server_client import check_server_health, save_conversation_log, send_message_to_server
from stt import transcribe_audio
from tts import synthesize_speech


logger = logging.getLogger(__name__)


def configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )


def _get_server_or_fallback_response(user_text: str) -> tuple[str, dict[str, Any], bool]:
    try:
        if check_server_health():
            server_response = send_message_to_server(user_text)
            return server_response["message"].strip(), server_response, True
    except Exception as exc:
        logger.warning("Server conversation failed; using local fallback: %s", exc)

    fallback_response = generate_local_duck_response(user_text)
    return f"{SERVER_UNAVAILABLE_MESSAGE} {fallback_response}", {}, False


def _speak(text: str) -> bool:
    try:
        synthesize_speech(text, RESPONSE_AUDIO_PATH)
        play_audio(RESPONSE_AUDIO_PATH)
    except Exception:
        logger.exception("TTS or playback failed")
        print(TTS_FAILURE_MESSAGE)
        return False

    return True


def run_once() -> None:
    print("듣고 있어요. 문제를 설명해 주세요.")

    record_audio(INPUT_AUDIO_PATH, duration=RECORD_SECONDS)

    stt_success = True
    user_text = transcribe_audio(INPUT_AUDIO_PATH)
    print(f"사용자: {user_text}")

    server_response: dict[str, Any] = {}
    used_server = False

    if user_text == STT_FAILURE_MESSAGE:
        stt_success = False
        response_text = STT_FAILURE_MESSAGE
    elif not user_text.strip():
        stt_success = False
        response_text = NO_SPEECH_MESSAGE
    else:
        response_text, server_response, used_server = _get_server_or_fallback_response(user_text)

    print(f"러버덕: {response_text}")

    tts_success = _speak(response_text)

    if used_server and server_response.get("shouldSaveLog", True):
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


def main() -> None:
    configure_logging()
    ensure_runtime_dirs()

    try:
        validate_required_environment()
    except RuntimeError as exc:
        print(exc)
        return

    while True:
        try:
            run_once()
        except KeyboardInterrupt:
            print("프로그램을 종료합니다.")
            break
        except Exception as exc:
            logger.exception("Unexpected loop error")
            print(f"오류 발생: {exc}")
            _speak("오류가 발생했어요. 잠시 후 다시 시도해 주세요.")


if __name__ == "__main__":
    main()
