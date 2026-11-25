"""Process debugging and learning documents into source-linked retrieval chunks."""
from __future__ import annotations

import hashlib
import re
import time

import pymupdf
from .normalization import finalize_markdown, build_canonical_blocks
from .pdf_extraction import extract_pdf_pages
from .pdf_layout import locate_pages
from .pdf_vision import PdfVisionError
from .chunking import build_chunks, CHUNKER_VERSION

SCHEMA_VERSION = "ducky-document-v1"
MAX_BYTES = 20 * 1024 * 1024
MAX_PAGES = 200
MAX_CHUNKS = 10000


class ProcessingError(ValueError):
    pass


def process_document(data: bytes, document_id: str, source_type: str, *,
                     source_key: str | None = None,
                     markdown_key: str | None = None) -> dict:
    started = time.perf_counter()
    if not re.fullmatch(r"[1-9][0-9]*", document_id):
        raise ProcessingError("INVALID_DOCUMENT_ID")
    if not data or len(data) > MAX_BYTES:
        raise ProcessingError("FILE_SIZE_LIMIT")
    source_type = source_type.lower()
    warnings = []
    page_count = None
    pdf_pages = []
    scanned = False
    if source_type == "pdf":
        if not data.startswith(b"%PDF-"):
            raise ProcessingError("INVALID_PDF")
        try:
            document = pymupdf.open(stream=data, filetype="pdf")
        except pymupdf.FileDataError as error:
            raise ProcessingError("INVALID_PDF") from error
        with document:
            if document.needs_pass:
                raise ProcessingError("ENCRYPTED_PDF")
            page_count = len(document)
            if page_count > MAX_PAGES:
                raise ProcessingError("PAGE_LIMIT")
            try:
                pdf_pages, warnings, scanned = extract_pdf_pages(document)
            except PdfVisionError as error:
                raise ProcessingError(str(error)) from error
            body = "\n\n".join(p["markdown"] for p in pdf_pages)
    elif source_type in {"txt", "md", "csv", "json"}:
        try:
            body = data.decode("utf-8-sig")
        except UnicodeDecodeError as error:
            raise ProcessingError("UTF8_REQUIRED") from error
    else:
        raise ProcessingError("UNSUPPORTED_FILE_TYPE")
    markdown = finalize_markdown(body, doc_id=document_id,
        source_key=source_key or f"originals/{document_id}/source.{source_type}",
        source_type=source_type)
    blocks, report = build_canonical_blocks(markdown, doc_id=document_id,
        markdown_key=markdown_key or f"markdown/{document_id}/document.md",
        pdf_pages=locate_pages(markdown, body, pdf_pages) if pdf_pages else None)
    visual_pages = {page["page"] for page in pdf_pages if "vision_line_offset" in page}
    searchable_visual_pages = {block["source"]["page"] for block in blocks
                               if block["searchable"] and block["source"].get("extraction") == "vision"}
    if not visual_pages.issubset(searchable_visual_pages):
        raise ProcessingError("PDF_VISION_UNREADABLE")
    report["chunker_version"] = CHUNKER_VERSION
    chunks = build_chunks(blocks, source_type)
    if not chunks:
        raise ProcessingError("OCR_NO_TEXT" if scanned else "NO_SEARCHABLE_TEXT")
    if len(chunks) > MAX_CHUNKS:
        raise ProcessingError("CHUNK_LIMIT")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "documentId": document_id,
        "sourceSha256": hashlib.sha256(data).hexdigest(),
        "markdown": markdown,
        "canonicalBlocks": blocks,
        "report": report,
        "chunks": chunks,
        "warnings": warnings,
        "pageCount": page_count,
        "processingSeconds": round(time.perf_counter() - started, 3),
    }
