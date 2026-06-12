import logging
import shutil
import subprocess
from pathlib import Path

from config import MIC_DEVICE, SPEAKER_DEVICE


logger = logging.getLogger(__name__)


class AudioIOError(RuntimeError):
    """Raised when recording or playback fails."""


def _require_command(command: str) -> None:
    if shutil.which(command) is None:
        raise AudioIOError(f"'{command}' 명령을 찾을 수 없습니다. 필요한 시스템 패키지를 설치해 주세요.")


def _run_audio_command(command: list[str], action: str) -> None:
    logger.debug("Running %s command: %s", action, " ".join(command))

    try:
        subprocess.run(command, check=True, capture_output=True, text=True)
    except FileNotFoundError as exc:
        raise AudioIOError(f"{action} 명령을 실행할 수 없습니다: {command[0]}") from exc
    except subprocess.CalledProcessError as exc:
        stderr = (exc.stderr or "").strip()
        message = stderr if stderr else f"exit code {exc.returncode}"
        raise AudioIOError(f"{action} 실패: {message}") from exc


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
    command.extend(["-f", "cd", "-t", "wav", "-d", str(duration), str(output)])

    _run_audio_command(command, "녹음")

    if not output.exists() or output.stat().st_size == 0:
        raise AudioIOError(f"녹음 파일이 생성되지 않았습니다: {output}")


def _play_wav(audio_path: Path) -> None:
    _require_command("aplay")

    command = ["aplay"]
    if SPEAKER_DEVICE:
        command.extend(["-D", SPEAKER_DEVICE])
    command.append(str(audio_path))

    _run_audio_command(command, "재생")


def _play_with_mpg123(audio_path: Path) -> None:
    _require_command("mpg123")

    command = ["mpg123", "-q", str(audio_path)]
    _run_audio_command(command, "재생")


def _play_with_ffplay(audio_path: Path) -> None:
    _require_command("ffplay")

    command = ["ffplay", "-nodisp", "-autoexit", "-loglevel", "error", str(audio_path)]
    _run_audio_command(command, "재생")


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
