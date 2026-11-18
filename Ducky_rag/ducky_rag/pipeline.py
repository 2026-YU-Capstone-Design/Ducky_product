"""Process debugging and learning documents into source-linked retrieval chunks."""
from __future__ import annotations

import hashlib
import re
import time

import pymupdf
import pymupdf4llm

from .normalization import finalize_markdown, build_canonical_blocks, split_large_text

SCHEMA_VERSION = "ducky-document-v1"
MAX_BYTES = 20 * 1024 * 1024
MAX_PAGES = 200
MAX_CHUNKS = 10000


class ProcessingError(ValueError):
    pass


def process_document(data: bytes, document_id: str, source_type: str) -> dict:
    started = time.perf_counter()
    if not re.fullmatch(r"[1-9][0-9]*", document_id):
        raise ProcessingError("INVALID_DOCUMENT_ID")
    if not data or len(data) > MAX_BYTES:
        raise ProcessingError("FILE_SIZE_LIMIT")
    source_type = source_type.lower()
    warnings = []
    page_count = None
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
            pages = pymupdf4llm.to_markdown(document, page_chunks=True,
                                           write_images=False, force_text=True)
            body = "\n\n".join(p["text"] for p in pages)
    elif source_type in {"txt", "md", "csv", "json"}:
        try:
            body = data.decode("utf-8-sig")
        except UnicodeDecodeError as error:
            raise ProcessingError("UTF8_REQUIRED") from error
    else:
        raise ProcessingError("UNSUPPORTED_FILE_TYPE")
    markdown = finalize_markdown(body, doc_id=document_id,
        source_key=f"raw/{document_id}/source.{source_type}",
        source_type=source_type)
    blocks, report = build_canonical_blocks(markdown, doc_id=document_id,
        markdown_key=f"processed/{document_id}/document.md")
    chunks = []
    for index, block in enumerate(blocks):
        if not block["searchable"]:
            continue
        # Keep table facts and display equations intact; preserve code whitespace.
        parts = ([block["text"]] if block["type"] in {"table_row", "math"}
                 else split_large_text(block["text"], max_len=1400))
        for part_index, text in enumerate(parts):
            if not text.strip():
                continue
            heading = " > ".join(block["heading_path"])
            chunks.append({
                "blockId": block["block_id"],
                "chunkKey": f'{block["block_id"]}:{part_index}',
                "content": text,
                "headingPath": heading,
                "blockType": block["type"],
                "previousBlockId": blocks[index - 1]["block_id"] if index else None,
                "nextBlockId": blocks[index + 1]["block_id"] if index + 1 < len(blocks) else None,
                "textForEmbedding": f"{heading}\n{text}".strip(),
            })
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
