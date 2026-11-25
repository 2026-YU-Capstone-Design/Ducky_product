"""Select PDF pages that need visual recovery and keep their original numbering."""
from collections import Counter
import os
import time

import pymupdf

from .pdf_layout import PAGE_NUMBER, extract_pages
from .pdf_ocr import OCR_TIME_BUDGET, PdfOcrError, read_scan
from .pdf_vision import MAX_VISION_PAGES, OpenAIPageReader, PdfVisionError

VISUAL_PATHS = {"fill-path", "stroke-path", "fill-shade"}
PDF_TIME_BUDGET = 255


def _has_native_text(text):
    return bool(text and not PAGE_NUMBER.fullmatch(text)
                and sum(char.isalnum() for char in text) > text.count("\ufffd"))


def _is_blank(page):
    # Check real rendered pixels to distinguish blank scans from failed OCR of
    # text or diagrams. Avoid allocating a full-size bitmap for oversized PDFs.
    scale = min(1.0, 1024 / max(page.rect.width, page.rect.height, 1))
    gray = page.get_pixmap(matrix=pymupdf.Matrix(scale, scale),
                           colorspace=pymupdf.csGRAY, annots=False)
    return bool(gray.size) and min(gray.samples, default=0) == 255


def extract_pdf_pages(document):
    started = time.monotonic()
    ocr_deadline = started + OCR_TIME_BUDGET
    vision_deadline = started + PDF_TIME_BUDGET
    model = os.environ.get("RAG_VISION_MODEL", "").strip()
    scans = {}
    recover = {}
    for page in document:
        index = page.number
        try:
            text = page.get_text().strip()
            marks = Counter(kind for kind, _ in page.get_bboxlog())
        except (ValueError, RuntimeError):
            scans[index] = ""
            recover[index] = False
            continue
        paths = sum(marks[kind] for kind in VISUAL_PATHS)
        if _has_native_text(text):
            # Raster images, shadings, and groups of vector paths can carry
            # information beyond the text layer, even if the text parsed.
            if marks["fill-image"] or marks["fill-shade"] or paths >= 3:
                recover[index] = True
            continue
        if not (marks["fill-image"] or paths):
            if text and not PAGE_NUMBER.fullmatch(text):
                scans[index] = ""
                recover[index] = False
            continue
        try:
            scans[index] = read_scan(page) if time.monotonic() < ocr_deadline else ""
        except PdfOcrError:
            scans[index] = ""
        if not scans[index]:
            try:
                if _is_blank(page):
                    continue
            except (ValueError, RuntimeError):
                pass
            recover[index] = False
        elif model:
            # An OCR transcript can still omit relationships from tables,
            # charts or designs embedded in a scan.
            recover[index] = True

    pages, layout_failures = extract_pages(document, scans)
    for index, has_local_text in layout_failures.items():
        recover[index] = has_local_text
    required = [index for index, supplement in sorted(recover.items()) if not supplement]
    supplemental = [index for index, supplement in sorted(recover.items()) if supplement]
    if len(required) > MAX_VISION_PAGES:
        raise PdfVisionError("PDF_VISION_LIMIT")
    selected = required + (supplemental[:MAX_VISION_PAGES - len(required)] if model else [])
    images_not_enriched = len(selected) < len(recover)
    try:
        reader = OpenAIPageReader(model) if selected else None
    except PdfVisionError:
        if required:
            raise
        selected = []
        images_not_enriched = True
    enriched = False
    for index in selected:
        supplement = recover[index]
        try:
            text = reader.read(document[index], supplement=supplement, deadline=vision_deadline)
        except PdfVisionError:
            if not supplement:
                raise
            images_not_enriched = True
            continue
        if text:
            page = pages[index]
            page["vision_line_offset"] = page["markdown"].count("\n") + 2
            page["markdown"] += "\n\n## 이미지에서 읽은 내용\n\n" + text
            enriched = True

    warnings = []
    if any(scans.values()):
        warnings.append("OCR_TEXT_EXTRACTED")
    if enriched:
        warnings.append("PDF_VISION_EXTRACTED")
    if images_not_enriched:
        warnings.append("IMAGES_NOT_ENRICHED")
    return pages, warnings, bool(scans)
