import logging
from pathlib import Path

from openai import OpenAI

from config import OPENAI_API_KEY, OPENAI_TTS_MODEL, OPENAI_TTS_VOICE, REQUEST_TIMEOUT_SECONDS


logger = logging.getLogger(__name__)


def synthesize_speech(text: str, output_path: str) -> None:
    """
    Convert Korean text to an audio file with OpenAI text-to-speech.
    """
    if not text or not text.strip():
        raise ValueError("TTS로 변환할 텍스트가 비어 있습니다.")

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    try:
        client = OpenAI(api_key=OPENAI_API_KEY, timeout=REQUEST_TIMEOUT_SECONDS)
        with client.audio.speech.with_streaming_response.create(
            model=OPENAI_TTS_MODEL,
            voice=OPENAI_TTS_VOICE,
            input=text,
            response_format="mp3",
        ) as response:
            response.stream_to_file(output)
    except Exception:
        logger.exception("OpenAI TTS request failed")
        raise

    if not output.exists() or output.stat().st_size == 0:
        raise RuntimeError(f"TTS 출력 파일이 생성되지 않았습니다: {output}")
