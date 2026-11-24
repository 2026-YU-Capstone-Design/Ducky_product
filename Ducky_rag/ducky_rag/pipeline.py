"""Process debugging and learning documents into source-linked retrieval chunks."""
from __future__ import annotations

import hashlib
import re
import time

import pymupdf
from .normalization import finalize_markdown, build_canonical_blocks
from .pdf_layout import extract_pages, locate_pages
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
    if source_type == "pdf":
        if not data.startswith(b"%PDF-"):
            raise ProcessingError("INVALID_PDF")
        with pymupdf.open(stream=data, filetype="pdf") as document:
            if document.needs_pass:
                raise ProcessingError("ENCRYPTED_PDF")
            page_count = len(document)
            if page_count > MAX_PAGES:
                raise ProcessingError("PAGE_LIMIT")
            # A scanned/empty page must not silently become an indexed document.
            missing = [i + 1 for i, page in enumerate(document)
                       if not page.get_text().strip() and page.get_images()]
            if missing:
                raise ProcessingError("OCR_REQUIRED")
            image_count = sum(len(page.get_images()) for page in document)
            if image_count:
                warnings.append("IMAGES_NOT_ENRICHED")
            # Extract text first; image content needs a separate OCR path.
            pdf_pages = extract_pages(document)
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
    report["chunker_version"] = CHUNKER_VERSION
    chunks = build_chunks(blocks, source_type)
    if not chunks:
        raise ProcessingError("NO_SEARCHABLE_TEXT")
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
