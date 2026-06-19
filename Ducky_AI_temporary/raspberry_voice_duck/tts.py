import asyncio
import logging
import shutil
import subprocess
from pathlib import Path

from config import (
    LOCAL_EDGE_VOICE,
    LOCAL_ESPEAK_VOICE,
    LOCAL_PIPER_BIN,
    LOCAL_PIPER_CONFIG,
    LOCAL_PIPER_MODEL,
    LOCAL_TTS_ENGINE,
    OPENAI_API_KEY,
    OPENAI_TTS_MODEL,
    OPENAI_TTS_VOICE,
    REQUEST_TIMEOUT_SECONDS,
    SPEECH_BACKEND,
)


logger = logging.getLogger(__name__)


def _synthesize_with_openai(text: str, output_path: Path) -> None:
    from openai import OpenAI

    client = OpenAI(api_key=OPENAI_API_KEY, timeout=REQUEST_TIMEOUT_SECONDS)
    with client.audio.speech.with_streaming_response.create(
        model=OPENAI_TTS_MODEL,
        voice=OPENAI_TTS_VOICE,
        input=text,
        response_format="mp3",
    ) as response:
        response.stream_to_file(output_path)


def _synthesize_with_edge(text: str, output_path: Path) -> None:
    import edge_tts

    async def _run() -> None:
        communicate = edge_tts.Communicate(text, LOCAL_EDGE_VOICE)
        await communicate.save(str(output_path))

    asyncio.run(_run())


def _synthesize_with_piper(text: str, output_path: Path) -> None:
    if shutil.which(LOCAL_PIPER_BIN) is None:
        raise RuntimeError(
            f"'{LOCAL_PIPER_BIN}' 명령을 찾을 수 없습니다. Piper를 설치해 주세요."
        )

    command = [
        LOCAL_PIPER_BIN,
        "--model",
        LOCAL_PIPER_MODEL,
        "--output_file",
        str(output_path),
    ]
    if Path(LOCAL_PIPER_CONFIG).exists():
        command.extend(["--config", LOCAL_PIPER_CONFIG])

    result = subprocess.run(
        command,
        input=text,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        stderr = (result.stderr or "").strip()
        raise RuntimeError(stderr or f"Piper exited with code {result.returncode}")


def _synthesize_with_espeak(text: str, output_path: Path) -> None:
    if shutil.which("espeak-ng") is None:
        raise RuntimeError("'espeak-ng' 명령을 찾을 수 없습니다.")

    subprocess.run(
        ["espeak-ng", "-v", LOCAL_ESPEAK_VOICE, "-w", str(output_path), text],
        check=True,
        capture_output=True,
        text=True,
    )


def _synthesize_local(text: str, output_path: Path) -> None:
    engine = LOCAL_TTS_ENGINE
    if engine == "edge":
        _synthesize_with_edge(text, output_path)
        return
    if engine == "piper":
        _synthesize_with_piper(text, output_path)
        return
    if engine == "espeak":
        _synthesize_with_espeak(text, output_path)
        return
    raise RuntimeError(
        "LOCAL_TTS_ENGINE는 edge, espeak, piper 중 하나여야 합니다. "
        f"현재 값: {engine!r}"
    )


def synthesize_speech(text: str, output_path: str) -> None:
    """
    Convert Korean text to an audio file.

    SPEECH_BACKEND=openai -> OpenAI TTS (mp3)
    SPEECH_BACKEND=local  -> LOCAL_TTS_ENGINE (edge/espeak/piper)
    """
    if not text or not text.strip():
        raise ValueError("TTS로 변환할 텍스트가 비어 있습니다.")

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    try:
        if SPEECH_BACKEND == "local":
            _synthesize_local(text, output)
        else:
            _synthesize_with_openai(text, output)
    except Exception:
        logger.exception("%s TTS request failed", SPEECH_BACKEND)
        raise

    if not output.exists() or output.stat().st_size == 0:
        raise RuntimeError(f"TTS 출력 파일이 생성되지 않았습니다: {output}")
