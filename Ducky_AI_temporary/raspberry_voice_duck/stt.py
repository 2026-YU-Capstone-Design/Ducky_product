import logging
from pathlib import Path

from config import (
    LANGUAGE,
    LOCAL_WHISPER_MODEL,
    LOCAL_WHISPER_VAD_FILTER,
    OPENAI_API_KEY,
    OPENAI_STT_MODEL,
    SPEECH_BACKEND,
    STT_FAILURE_MESSAGE,
)


logger = logging.getLogger(__name__)
_whisper_model = None


def _transcribe_with_openai(audio_path: Path) -> str:
    from openai import OpenAI

    client = OpenAI(api_key=OPENAI_API_KEY)
    with audio_path.open("rb") as audio_file:
        transcription = client.audio.transcriptions.create(
            model=OPENAI_STT_MODEL,
            file=audio_file,
            language=LANGUAGE,
        )

    text = getattr(transcription, "text", "")
    if isinstance(transcription, dict):
        text = transcription.get("text", text)
    return (text or "").strip()


def _get_whisper_model():
    global _whisper_model
    if _whisper_model is None:
        from faster_whisper import WhisperModel

        _whisper_model = WhisperModel(LOCAL_WHISPER_MODEL, device="cpu", compute_type="int8")
    return _whisper_model


def _transcribe_with_whisper(audio_path: Path) -> str:
    model = _get_whisper_model()
    segments, _info = model.transcribe(
        str(audio_path),
        language=LANGUAGE,
        vad_filter=LOCAL_WHISPER_VAD_FILTER,
    )
    return "".join(segment.text for segment in segments).strip()


def transcribe_audio(audio_path: str) -> str:
    """
    Convert a recorded audio file to text.

    On failure this returns a user-facing Korean message so the main loop can
    guide the user without terminating the program.
    """
    path = Path(audio_path)
    if not path.exists() or path.stat().st_size == 0:
        logger.warning("Audio file does not exist or is empty: %s", path)
        return STT_FAILURE_MESSAGE

    try:
        if SPEECH_BACKEND == "local":
            return _transcribe_with_whisper(path)
        return _transcribe_with_openai(path)
    except Exception:
        logger.exception("STT request failed (backend=%s)", SPEECH_BACKEND)
        return STT_FAILURE_MESSAGE
