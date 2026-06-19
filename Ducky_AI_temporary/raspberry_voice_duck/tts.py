import asyncio
import logging
from pathlib import Path

from config import (
    LOCAL_EDGE_VOICE,
    LOCAL_TTS_ENGINE,
    OPENAI_API_KEY,
    OPENAI_TTS_MODEL,
    OPENAI_TTS_VOICE,
    REQUEST_TIMEOUT_SECONDS,
    SPEECH_BACKEND,
)


logger = logging.getLogger(__name__)


def _synthesize_with_openai(text: str, output: Path) -> None:
    from openai import OpenAI

    client = OpenAI(api_key=OPENAI_API_KEY, timeout=REQUEST_TIMEOUT_SECONDS)
    with client.audio.speech.with_streaming_response.create(
        model=OPENAI_TTS_MODEL,
        voice=OPENAI_TTS_VOICE,
        input=text,
        response_format="mp3",
    ) as response:
        response.stream_to_file(output)


async def _synthesize_with_edge_async(text: str, output: Path) -> None:
    import edge_tts

    communicate = edge_tts.Communicate(text, LOCAL_EDGE_VOICE)
    await communicate.save(str(output))


def _synthesize_with_edge(text: str, output: Path) -> None:
    asyncio.run(_synthesize_with_edge_async(text, output))


def synthesize_speech(text: str, output_path: str) -> None:
    """
    Convert Korean text to an audio file.
    """
    if not text or not text.strip():
        raise ValueError("TTS로 변환할 텍스트가 비어 있습니다.")

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    try:
        if SPEECH_BACKEND == "local" and LOCAL_TTS_ENGINE == "edge":
            _synthesize_with_edge(text, output)
        else:
            _synthesize_with_openai(text, output)
    except Exception:
        logger.exception("TTS request failed (backend=%s)", SPEECH_BACKEND)
        raise

    if not output.exists() or output.stat().st_size == 0:
        raise RuntimeError(f"TTS 출력 파일이 생성되지 않았습니다: {output}")
