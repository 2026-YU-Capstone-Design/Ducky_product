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


OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "").strip()
OPENAI_STT_MODEL = os.getenv("OPENAI_STT_MODEL", "gpt-4o-transcribe").strip()
OPENAI_TTS_MODEL = os.getenv("OPENAI_TTS_MODEL", "gpt-4o-mini-tts").strip()
OPENAI_TTS_VOICE = os.getenv("OPENAI_TTS_VOICE", "marin").strip()

LANGUAGE = os.getenv("LANGUAGE", "ko").strip()
SERVER_BASE_URL = os.getenv("SERVER_BASE_URL", "http://localhost:8080").strip().rstrip("/")
DEVICE_ID = os.getenv("DEVICE_ID", "raspberry-duck-001").strip()
USER_ID = os.getenv("USER_ID", "test-user").strip()

RECORD_SECONDS = _get_int("RECORD_SECONDS", 7)
REQUEST_TIMEOUT_SECONDS = _get_float("REQUEST_TIMEOUT_SECONDS", 8.0)

INPUT_AUDIO_PATH = os.getenv("INPUT_AUDIO_PATH", str(AUDIO_DIR / "input.wav")).strip()
RESPONSE_AUDIO_PATH = os.getenv("RESPONSE_AUDIO_PATH", str(AUDIO_DIR / "response.mp3")).strip()

MIC_DEVICE = os.getenv("MIC_DEVICE", "").strip()
SPEAKER_DEVICE = os.getenv("SPEAKER_DEVICE", "").strip()

RUN_CONTINUOUSLY = _get_bool("RUN_CONTINUOUSLY", True)

LEARNING_TYPE = {
    "processing": os.getenv("LEARNING_PROCESSING", "reflective").strip(),
    "expression": os.getenv("LEARNING_EXPRESSION", "verbal").strip(),
    "structure": os.getenv("LEARNING_STRUCTURE", "sequential").strip(),
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
