import logging
import shutil
import subprocess
import time
from pathlib import Path

from config import (
    MIC_CHANNELS,
    MIC_DEVICE,
    MIC_RECORD_FORMAT,
    MIC_SAMPLE_RATE,
    PLAYBACK_TIMEOUT_SECONDS,
    RECORD_RETRY_COUNT,
    RECORD_RETRY_DELAY_SECONDS,
    RECORD_TIMEOUT_BUFFER_SECONDS,
    SPEAKER_DEVICE,
)


logger = logging.getLogger(__name__)


class AudioIOError(RuntimeError):
    """Raised when recording or playback fails."""


def _require_command(command: str) -> None:
    if shutil.which(command) is None:
        raise AudioIOError(f"'{command}' 명령을 찾을 수 없습니다. 필요한 시스템 패키지를 설치해 주세요.")


def _wrap_with_command_timeout(command: list[str], timeout_seconds: int) -> list[str]:
    if shutil.which("timeout") is None:
        return command
    return [
        "timeout",
        "--signal=TERM",
        "--kill-after=1",
        str(timeout_seconds),
        *command,
    ]


def _run_audio_command(
    command: list[str],
    action: str,
    timeout: float | None = None,
) -> None:
    logger.info("%s 시작: %s", action, " ".join(command))

    try:
        subprocess.run(
            command,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        raise AudioIOError(f"{action} 명령을 실행할 수 없습니다: {command[0]}") from exc
    except subprocess.TimeoutExpired as exc:
        raise AudioIOError(
            f"{action} 응답 없음 (대기 한도 {timeout:.0f}초). "
            "오디오 장치가 멈춰 있을 수 있습니다."
        ) from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        message = stderr if stderr else f"exit code {exc.returncode}"
        raise AudioIOError(f"{action} 실패: {message}") from exc

    logger.info("%s 완료", action)


def record_audio(output_path: str, duration: int = 7) -> None:
    """
    Record microphone input for duration seconds and write a wav file.

    Raspberry Pi uses arecord through subprocess so ALSA device settings can be
    handled outside Python when needed.
    """
    if duration <= 0:
        raise ValueError("duration은 1초 이상이어야 합니다.")

    _require_command("arecord")

    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)

    command = ["arecord"]
    if MIC_DEVICE:
        command.extend(["-D", MIC_DEVICE])
    record_format = MIC_RECORD_FORMAT.strip().lower()
    if record_format not in {"", "none", "off"}:
        command.extend(["-f", MIC_RECORD_FORMAT.strip()])
    else:
        command.extend(
            [
                "-f",
                "S16_LE",
                "-r",
                str(MIC_SAMPLE_RATE),
                "-c",
                str(MIC_CHANNELS),
            ]
        )
    command.extend(["-t", "wav", "-d", str(duration), str(output)])
    command = _wrap_with_command_timeout(command, duration + 2)

    attempts = max(1, RECORD_RETRY_COUNT)
    last_error: AudioIOError | None = None
    record_timeout = duration + RECORD_TIMEOUT_BUFFER_SECONDS
    for attempt in range(1, attempts + 1):
        try:
            _run_audio_command(command, "녹음", timeout=record_timeout)
            last_error = None
            break
        except AudioIOError as exc:
            last_error = exc
            if attempt >= attempts:
                raise AudioIOError(
                    f"녹음 실패: arecord가 {duration}초 안에 끝나지 않았습니다. "
                    f"MIC_DEVICE({MIC_DEVICE or 'default'})와 MIC_RECORD_FORMAT({MIC_RECORD_FORMAT or 'S16_LE'})를 확인해 주세요."
                ) from exc
            logger.warning(
                "녹음 실패 (%d/%d), %.1f초 후 재시도: %s",
                attempt,
                attempts,
                RECORD_RETRY_DELAY_SECONDS,
                exc,
            )
            time.sleep(RECORD_RETRY_DELAY_SECONDS)

    if last_error is not None:
        raise last_error

    if not output.exists() or output.stat().st_size == 0:
        raise AudioIOError(f"녹음 파일이 생성되지 않았습니다: {output}")


def _play_wav(audio_path: Path) -> None:
    _require_command("aplay")

    command = ["aplay"]
    if SPEAKER_DEVICE:
        command.extend(["-D", SPEAKER_DEVICE])
    command.append(str(audio_path))

    _run_audio_command(command, "재생", timeout=PLAYBACK_TIMEOUT_SECONDS)


def _play_with_mpg123(audio_path: Path) -> None:
    _require_command("mpg123")

    command = ["mpg123", "-q"]
    if SPEAKER_DEVICE:
        command.extend(["-a", SPEAKER_DEVICE])
    command.append(str(audio_path))
    _run_audio_command(command, "재생", timeout=PLAYBACK_TIMEOUT_SECONDS)


def _play_with_ffplay(audio_path: Path) -> None:
    _require_command("ffplay")

    command = ["ffplay", "-nodisp", "-autoexit", "-loglevel", "error"]
    if SPEAKER_DEVICE:
        command.extend(["-ao", f"alsa:{SPEAKER_DEVICE}"])
    command.append(str(audio_path))
    _run_audio_command(command, "재생", timeout=PLAYBACK_TIMEOUT_SECONDS)


def play_audio(audio_path: str) -> None:
    """
    Play an audio file through the configured speaker.

    wav files use aplay. mp3 files prefer mpg123 and fall back to ffplay.
    """
    path = Path(audio_path)
    if not path.exists() or path.stat().st_size == 0:
        raise AudioIOError(f"재생할 음성 파일이 없습니다: {path}")

    suffix = path.suffix.lower()
    if suffix == ".wav":
        _play_wav(path)
        return

    if suffix == ".mp3":
        if shutil.which("mpg123") is not None:
            _play_with_mpg123(path)
            return
        _play_with_ffplay(path)
        return

    _play_with_ffplay(path)
