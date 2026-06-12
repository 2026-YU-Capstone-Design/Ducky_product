import os
from pathlib import Path

from dotenv import load_dotenv


BASE_DIR = Path(__file__).resolve().parent
AUDIO_DIR = BASE_DIR / "audio"

load_dotenv(BASE_DIR / ".env")


def _get_int(name: str, default: int) -> int:
    raw_value = os.getenv(name)
    if raw_value is None or raw_value.strip() == "":
        return default

    try:
        return int(raw_value)
    except ValueError:
        return default


def _get_float(name: str, default: float) -> float:
    raw_value = os.getenv(name)
    if raw_value is None or raw_value.strip() == "":
        return default

    try:
        return float(raw_value)
    except ValueError:
        return default


def _get_bool(name: str, default: bool) -> bool:
    raw_value = os.getenv(name)
    if raw_value is None or raw_value.strip() == "":
        return default

    return raw_value.strip().lower() in {"1", "true", "yes", "y", "on"}


def _get_str(name: str, default: str = "") -> str:
    raw_value = os.getenv(name)
    if raw_value is None or raw_value.strip() == "":
        return default
    return raw_value.strip()


OPENAI_API_KEY = _get_str("OPENAI_API_KEY")
OPENAI_STT_MODEL = _get_str("OPENAI_STT_MODEL", "gpt-4o-transcribe")
OPENAI_TTS_MODEL = _get_str("OPENAI_TTS_MODEL", "gpt-4o-mini-tts")
OPENAI_TTS_VOICE = _get_str("OPENAI_TTS_VOICE", "marin")

LANGUAGE = _get_str("LANGUAGE", "ko")
SERVER_BASE_URL = _get_str("SERVER_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_ID = _get_str("DEVICE_ID", "raspberry-duck-001")
USER_ID = _get_str("USER_ID", "test-user")

RECORD_SECONDS = _get_int("RECORD_SECONDS", 7)
REQUEST_TIMEOUT_SECONDS = _get_float("REQUEST_TIMEOUT_SECONDS", 8.0)
IOT_REPORTING_ENABLED = _get_bool("IOT_REPORTING_ENABLED", True)
IOT_EVENT_TIMEOUT_SECONDS = _get_float("IOT_EVENT_TIMEOUT_SECONDS", 2.0)
IOT_FAILURE_BACKOFF_SECONDS = _get_float("IOT_FAILURE_BACKOFF_SECONDS", 30.0)

INPUT_AUDIO_PATH = _get_str("INPUT_AUDIO_PATH", str(AUDIO_DIR / "input.wav"))
RESPONSE_AUDIO_PATH = _get_str("RESPONSE_AUDIO_PATH", str(AUDIO_DIR / "response.mp3"))

MIC_DEVICE = _get_str("MIC_DEVICE")
SPEAKER_DEVICE = _get_str("SPEAKER_DEVICE")

RUN_CONTINUOUSLY = _get_bool("RUN_CONTINUOUSLY", True)

LEARNING_TYPE = {
    "processing": _get_str("LEARNING_PROCESSING", "reflective"),
    "expression": _get_str("LEARNING_EXPRESSION", "verbal"),
    "structure": _get_str("LEARNING_STRUCTURE", "sequential"),
}

NO_SPEECH_MESSAGE = "음성을 잘 듣지 못했어요. 조금 더 가까이에서 다시 말해 주세요."
STT_FAILURE_MESSAGE = "음성을 텍스트로 바꾸는 중 오류가 발생했어요. 다시 말해 주세요."
TTS_FAILURE_MESSAGE = "응답 음성을 만드는 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
SERVER_UNAVAILABLE_MESSAGE = "서버 연결이 불안정해서 기본 응답으로 진행할게요."


def ensure_runtime_dirs() -> None:
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)


def validate_required_environment() -> None:
    if not OPENAI_API_KEY:
        raise RuntimeError(
            "OPENAI_API_KEY가 설정되어 있지 않습니다. "
            "raspberry_voice_duck/.env 파일에 API 키를 추가해 주세요."
        )
