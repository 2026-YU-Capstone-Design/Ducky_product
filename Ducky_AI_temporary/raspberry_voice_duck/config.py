import os
from pathlib import Path

from dotenv import load_dotenv


BASE_DIR = Path(__file__).resolve().parent
AUDIO_DIR = BASE_DIR / "audio"
LOGS_DIR = BASE_DIR / "logs"

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

SPEECH_BACKEND = _get_str("SPEECH_BACKEND", "openai").lower()
LOCAL_WHISPER_MODEL = _get_str("LOCAL_WHISPER_MODEL", "tiny")
LOCAL_WHISPER_VAD_FILTER = _get_bool("LOCAL_WHISPER_VAD_FILTER", False)
LOCAL_TTS_ENGINE = _get_str("LOCAL_TTS_ENGINE", "edge").lower()
LOCAL_EDGE_VOICE = _get_str("LOCAL_EDGE_VOICE", "ko-KR-SunHiNeural")

CHAT_BACKEND = _get_str("CHAT_BACKEND", "auto").lower()
OLLAMA_BASE_URL = _get_str("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = _get_str("OLLAMA_MODEL", "qwen2.5:0.5b")
LOCAL_LLM_SYSTEM_PROMPT = _get_str(
    "LOCAL_LLM_SYSTEM_PROMPT",
    "너는 Ducky야. 사용자가 문제를 말로 풀어 설명하도록 돕는 러버덕 학습 파트너야. "
    "한국어로 자연스럽고 친근하게 답해. 짧게 설명한 뒤 스스로 생각할 수 있는 질문 1개를 덧붙여.",
)

LANGUAGE = _get_str("LANGUAGE", "ko")
SERVER_BASE_URL = _get_str("SERVER_BASE_URL", "http://localhost:8080").rstrip("/")
DEVICE_ID = _get_str("DEVICE_ID", "raspberry-duck-001")
USER_ID = _get_str("USER_ID", "test-user")

RECORD_SECONDS = _get_int("RECORD_SECONDS", 7)
RECORD_FORMAT = _get_str("RECORD_FORMAT", "S16_LE")
RECORD_RATE = _get_int("RECORD_RATE", 16000)
RECORD_CHANNELS = _get_int("RECORD_CHANNELS", 1)
REQUEST_TIMEOUT_SECONDS = _get_float("REQUEST_TIMEOUT_SECONDS", 30.0)
IOT_REPORTING_ENABLED = _get_bool("IOT_REPORTING_ENABLED", True)
IOT_EVENT_TIMEOUT_SECONDS = _get_float("IOT_EVENT_TIMEOUT_SECONDS", 2.0)
IOT_FAILURE_BACKOFF_SECONDS = _get_float("IOT_FAILURE_BACKOFF_SECONDS", 30.0)
TRIGGER_MODE = _get_str("TRIGGER_MODE", "command").lower()
COMMAND_POLL_SECONDS = _get_float("COMMAND_POLL_SECONDS", 1.0)
NANO_SERIAL_PORT = _get_str("NANO_SERIAL_PORT", "/dev/ttyUSB0")
NANO_SERIAL_BAUD_RATE = _get_int("NANO_SERIAL_BAUD_RATE", 115200)
NANO_SERIAL_TIMEOUT_SECONDS = _get_float("NANO_SERIAL_TIMEOUT_SECONDS", 0.1)
NANO_RECONNECT_SECONDS = _get_float("NANO_RECONNECT_SECONDS", 2.0)
OFFLINE_TIMEOUT_SECONDS = _get_float("OFFLINE_TIMEOUT_SECONDS", 15.0)

INPUT_AUDIO_PATH = _get_str("INPUT_AUDIO_PATH", str(AUDIO_DIR / "input.wav"))
RESPONSE_AUDIO_PATH = _get_str("RESPONSE_AUDIO_PATH", str(AUDIO_DIR / "response.mp3"))

MIC_DEVICE = _get_str("MIC_DEVICE")
SPEAKER_DEVICE = _get_str("SPEAKER_DEVICE")

RUN_CONTINUOUSLY = _get_bool("RUN_CONTINUOUSLY", True)

PENDING_QUEUE_PATH = _get_str(
    "PENDING_QUEUE_PATH",
    str(LOGS_DIR / "pending_conversations.jsonl"),
)
LEARNING_STYLE_CACHE_PATH = _get_str(
    "LEARNING_STYLE_CACHE_PATH",
    str(LOGS_DIR / "cached_learning_style.json"),
)

NO_SPEECH_MESSAGE = "음성을 잘 듣지 못했어요. 조금 더 가까이에서 다시 말해 주세요."
STT_FAILURE_MESSAGE = "음성을 텍스트로 바꾸는 중 오류가 발생했어요. 다시 말해 주세요."
TTS_FAILURE_MESSAGE = "응답 음성을 만드는 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
SERVER_FAILURE_MESSAGE = "서버 연결에 실패했습니다. 서버 상태를 확인해 주세요."


def ensure_runtime_dirs() -> None:
    AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    LOGS_DIR.mkdir(parents=True, exist_ok=True)


def validate_required_environment() -> None:
    if SPEECH_BACKEND == "openai" and not OPENAI_API_KEY:
        raise RuntimeError(
            "SPEECH_BACKEND=openai 이면 OPENAI_API_KEY가 필요합니다. "
            "로컬 STT/TTS를 쓰려면 SPEECH_BACKEND=local 로 설정하세요."
        )

    if CHAT_BACKEND == "server" and not SERVER_BASE_URL:
        raise RuntimeError("CHAT_BACKEND=server 이면 SERVER_BASE_URL이 필요합니다.")
