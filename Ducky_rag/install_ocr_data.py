"""Install the pinned Tesseract language data used by the PDF worker."""
import hashlib
from pathlib import Path
import sys
from urllib.request import urlopen

MODEL_REVISION = "65727574dfcd264acbb0c3e07860e4e9e9b22185"
DATA_FILES = {
    "eng.traineddata": "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2",
    "kor.traineddata": "6b85e11d9bbf07863b97b3523b1b112844c43e713df8b66418a081fd1060b3b2",
    "LICENSE": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
}


def install(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for name, expected in DATA_FILES.items():
        target = destination / name
        if target.is_file() and hashlib.sha256(target.read_bytes()).hexdigest() == expected:
            continue
        url = f"https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/{MODEL_REVISION}/{name}"
        with urlopen(url, timeout=45) as response:
            data = response.read(8 * 1024 * 1024)
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"Unexpected checksum for {name}")
        target.write_bytes(data)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python install_ocr_data.py <tessdata-directory>")
    install(Path(sys.argv[1]))
