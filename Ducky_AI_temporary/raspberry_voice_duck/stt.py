import logging
from pathlib import Path

from openai import OpenAI

from config import LANGUAGE, OPENAI_API_KEY, OPENAI_STT_MODEL, STT_FAILURE_MESSAGE


logger = logging.getLogger(__name__)


def transcribe_audio(audio_path: str) -> str:
    """
    Convert a recorded audio file to text with OpenAI speech-to-text.

    On failure this returns a user-facing Korean message so the main loop can
    guide the user without terminating the program.
    """
    path = Path(audio_path)
    if not path.exists() or path.stat().st_size == 0:
        logger.warning("Audio file does not exist or is empty: %s", path)
        return STT_FAILURE_MESSAGE

    try:
        client = OpenAI(api_key=OPENAI_API_KEY)
        with path.open("rb") as audio_file:
            transcription = client.audio.transcriptions.create(
                model=OPENAI_STT_MODEL,
                file=audio_file,
                language=LANGUAGE,
            )
    except Exception:
        logger.exception("OpenAI STT request failed")
        return STT_FAILURE_MESSAGE

    text = getattr(transcription, "text", "")
    if isinstance(transcription, dict):
        text = transcription.get("text", text)

    return (text or "").strip()
