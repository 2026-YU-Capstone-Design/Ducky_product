"""Read scanned or outlined PDF pages with bounded, local Korean/English OCR."""
import math
from pathlib import Path

import pymupdf

from .pdf_layout import PAGE_NUMBER

OCR_LANGUAGES = ("kor", "eng")
OCR_TIME_BUDGET = 175
OCR_PIXEL_LIMIT = 4_000_000
OCR_EDGE_LIMIT = 3000


class PdfOcrError(ValueError):
    pass


def _language_data():
    try:
        path = Path(pymupdf.get_tessdata())
    except (TypeError, ValueError, RuntimeError) as error:
        raise PdfOcrError("OCR_REQUIRED") from error
    if not all((path / f"{language}.traineddata").is_file() for language in OCR_LANGUAGES):
        raise PdfOcrError("OCR_REQUIRED")
    return str(path)


def read_scan(page):
    width, height = page.rect.width, page.rect.height
    if width <= 0 or height <= 0:
        raise PdfOcrError("OCR_FAILED")
    dpi = int(min(180, 72 * math.sqrt(OCR_PIXEL_LIMIT / (width * height)), 72 * OCR_EDGE_LIMIT / max(width, height)))
    if dpi < 72:
        raise PdfOcrError("OCR_RESOURCE_LIMIT")
    try:
        textpage = page.get_textpage_ocr(language="+".join(OCR_LANGUAGES), dpi=dpi, full=True, tessdata=_language_data())
        text = page.get_text("text", textpage=textpage, sort=True).strip()
    except PdfOcrError:
        raise
    except (ValueError, RuntimeError) as error:
        raise PdfOcrError("OCR_FAILED") from error
    # A page number by itself is not a searchable scan result.
    return "" if PAGE_NUMBER.fullmatch(text) else text
