"""Normalize learning notes for rubber-duck debugging conversations.

Preserve code, equations and short technical terms. Uploaded metadata never
supplies identity or access rights.
"""
from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone

NORMALIZER_VERSION = "ducky-learning-v2"
BLOCK_SCHEMA = "ducky-blocks-v1"
FRONTMATTER = re.compile(r"\A---\n.*?\n---(?:\n|$)", re.S)
HEADING = re.compile(r"^ {0,3}(#{1,6})\s+(.+?)\s*$")
FENCE = re.compile(r"^ {0,3}(`{3,}|~{3,})(.*)$")
IMAGE = re.compile(r"!\[([^\]]*)\]\([^\n]*?\)")
LIST = re.compile(r"^\s*(?:[-*+]|\d+[.)])\s+")


def finalize_markdown(body: str, *, doc_id: str, source_key: str,
                      source_type: str) -> str:
    # Folding arbitrary PDF lines damages code indentation and equation layout.
    # Preserve extracted text until a layout-aware conversion is validated.
    body = body.lstrip("\ufeff").replace("\r\n", "\n").replace("\r", "\n")
    body = FRONTMATTER.sub("", body, count=1).strip("\n")
    metadata = {
        "document_id": doc_id,
        "source_file": source_key.rsplit("/", 1)[-1],
        "source_type": source_type,
        "normalizer_version": NORMALIZER_VERSION,
        "converted_at": datetime.now(timezone.utc).isoformat(),
    }
    header = "\n".join(f"{key}: {json.dumps(value, ensure_ascii=False)}"
                       for key, value in metadata.items())
    return f"---\n{header}\n---\n\n{body}\n"


def _block_id(doc_id: str, ordinal: int, kind: str, text: str, line: int) -> str:
    """Stable citation ID; retain the stored ID format across reprocessing."""
    digest = hashlib.sha1(f"{kind}:{line}:{text}".encode("utf-8")).hexdigest()[:10]
    return f"{doc_id}:{ordinal:05d}:{digest}"


def _push_heading(headings: list[tuple[int, str]], depth: int,
                  title: str) -> list[tuple[int, str]]:
    """Replace sibling/child context when a new Markdown section starts."""
    title = re.sub(r"[ \t]{2,}", " ", title).strip()
    return [(level, text) for level, text in headings if level < depth] + [(depth, title)]


def _cells(line: str) -> list[str]:
    """Split Markdown cells without splitting escaped or inline-code pipes."""
    line = line.strip()
    if line.startswith("|"):
        line = line[1:]
    if line.endswith("|") and not line.endswith("\\|"):
        line = line[:-1]
    cells, current = [], []
    ticks = 0
    i = 0
    while i < len(line):
        if line[i:i + 2] == "\\|":
            current.append("|")
            i += 2
            continue
        if line[i] == "`":
            end = i + 1
            while end < len(line) and line[end] == "`":
                end += 1
            length = end - i
            ticks = 0 if ticks == length else (length if not ticks else ticks)
            current.append(line[i:end])
            i = end
            continue
        if line[i] == "|" and not ticks:
            cells.append("".join(current).strip())
            current = []
        else:
            current.append(line[i])
        i += 1
    cells.append("".join(current).strip())
    return cells


def build_canonical_blocks(markdown: str, *, doc_id: str,
                           markdown_key: str, pdf_pages=None) -> tuple[list[dict], dict]:
    header = FRONTMATTER.match(markdown)
    metadata = {}
    if header:
        for line in header.group().splitlines()[1:-1]:
            key, separator, value = line.partition(": ")
            if separator:
                metadata[key] = json.loads(value)
    body_start = header.end() if header else 0
    first_line = markdown[:body_start].count("\n") + 1
    lines = markdown[body_start:].splitlines()
    blocks = []
    headings = []

    def emit(kind, text, raw, start, end, *, searchable=True, attrs=None, warnings=None):
        role = ("content" if searchable else "provenance")
        if kind == "heading":
            role = "context"
        elif kind == "gfm_table":
            role = "container"
        elif kind == "table_row":
            role = "table_fact"
        block = {
            "schema_version": BLOCK_SCHEMA, "doc_id": doc_id,
            "doc_type": "learning_document", "type": kind,
            "ordinal": len(blocks) + 1,
            "heading_path": [title for _, title in headings],
            "title": headings[-1][1] if headings else metadata.get("source_file", doc_id),
            "text": text, "raw": raw,
            "source": {"markdown_key": markdown_key,
                       "source_file": metadata.get("source_file"),
                       "source_type": metadata.get("source_type"),
                       "line_start": start, "line_end": end},
            "searchable": searchable and bool(text.strip()),
            "search_role": role,
            "attrs": dict(attrs or {}), "warnings": list(warnings or []),
        }
        block["block_id"] = _block_id(doc_id, len(blocks) + 1, kind, text, start)
        if pdf_pages:
            page = next((p for p in pdf_pages if p["line_start"] <= start <= p["line_end"]), None)
            if page:
                block["source"]["page"] = page["page"]
        block["attrs"].update(searchable=block["searchable"], search_role=role)
        blocks.append(block)
        return block

    if header:
        emit("frontmatter", "", metadata, 1, first_line - 1, searchable=False,
             attrs={"fields": metadata})

    source_type = metadata.get("source_type")
    if source_type in {"txt", "csv", "json"}:
        # A '#' in source data is not automatically a Markdown heading.
        raw = "\n".join(lines).strip("\n")
        emit("paragraph" if source_type == "txt" else "code", raw, raw,
             first_line, first_line + len(lines) - 1, attrs={"language": source_type})
        lines = []

    def table_start(index):
        if index + 1 >= len(lines) or "|" not in lines[index]:
            return False
        separators = _cells(lines[index + 1])
        return len(separators) >= 2 and all(re.fullmatch(r":?-{3,}:?", cell) for cell in separators)

    def boundary(index):
        line = lines[index]
        return (not line.strip() or HEADING.match(line) or FENCE.match(line)
                or line.strip() in {"$$", r"\["} or table_start(index)
                or IMAGE.fullmatch(line.strip()))

    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        start = i
        fence = FENCE.match(line)
        if fence:
            delimiter, info = fence.groups()
            close = re.compile(r"^ {0,3}" + re.escape(delimiter[0])
                               + "{" + str(len(delimiter)) + r",}\s*$")
            i += 1
            while i < len(lines) and not close.match(lines[i]):
                i += 1
            text = "\n".join(lines[start + 1:i])
            closed = i < len(lines)
            if closed:
                i += 1
            emit("code", text, "\n".join(lines[start:i]), first_line + start,
                 first_line + i - 1, attrs={"language": info.strip().split()[0] if info.strip() else ""},
                 warnings=[] if closed else ["UNCLOSED_CODE_FENCE"])
            continue
        if line.strip() in {"$$", r"\["}:
            close = "$$" if line.strip() == "$$" else r"\]"
            i += 1
            while i < len(lines) and lines[i].strip() != close:
                i += 1
            closed = i < len(lines)
            if closed:
                i += 1
            raw = "\n".join(lines[start:i])
            emit("math", raw, raw, first_line + start, first_line + i - 1,
                 warnings=[] if closed else ["UNCLOSED_MATH_BLOCK"])
            continue
        heading = HEADING.match(line)
        if heading:
            depth, title = len(heading.group(1)), heading.group(2)
            headings = _push_heading(headings, depth, title)
            emit("heading", title, line, first_line + i, first_line + i,
                 searchable=False, attrs={"depth": depth})
            i += 1
            continue
        if table_start(i):
            headers = _cells(line)
            i += 2
            while i < len(lines) and lines[i].strip() and "|" in lines[i]:
                i += 1
            raw = "\n".join(lines[start:i])
            parent = emit("gfm_table", "", raw, first_line + start, first_line + i - 1,
                          searchable=False, attrs={"headers": headers})
            for row_index in range(start + 2, i):
                cells = _cells(lines[row_index])
                text = " / ".join(f"{headers[j] if j < len(headers) and headers[j] else f'column {j + 1}'}: {value}"
                                  for j, value in enumerate(cells) if value)
                # Keep short values, truth tables and technical symbols as facts.
                block = emit("table_row", IMAGE.sub(r"\1", text), lines[row_index],
                             first_line + row_index, first_line + row_index,
                             attrs={"header": headers, "cells": cells})
                block["parent_id"] = parent["block_id"]
            continue
        if IMAGE.fullmatch(line.strip()):
            emit("image", "", line, first_line + i, first_line + i, searchable=False)
            i += 1
            continue
        i += 1
        while i < len(lines) and not boundary(i):
            i += 1
        raw = "\n".join(lines[start:i])
        text = IMAGE.sub(r"\1", raw)
        page_number = source_type == "pdf" and bool(re.fullmatch(r"\s*(?:-?\s*\d{1,4}\s*-?|page\s+\d{1,4})\s*", text, re.I))
        emit("list" if LIST.match(line) else "paragraph", text, raw,
             first_line + start, first_line + i - 1, searchable=not page_number)

    searchable_count = sum(block["searchable"] for block in blocks)
    report = {
        "schema_version": BLOCK_SCHEMA, "normalizer_version": NORMALIZER_VERSION,
        "doc_id": doc_id, "doc_type": "learning_document", "source_key": markdown_key,
        "block_count": len(blocks), "searchable_count": searchable_count,
        "non_searchable_count": len(blocks) - searchable_count,
        "counts_by_type": dict(Counter(block["type"] for block in blocks)),
        "warning_count": sum(len(block["warnings"]) for block in blocks),
        "warnings": [],
        "pdf_pages": pdf_pages or [],
    }
    return blocks, report


def split_large_text(text: str, *, max_len: int) -> list[str]:
    """Bound chunk size without rewriting indentation, operators or formulas."""
    parts = []
    while len(text) > max_len:
        cut = text.rfind("\n", 0, max_len)
        if cut < max_len // 2:
            cut = text.rfind(" ", 0, max_len)
        cut = cut + 1 if cut >= max_len // 2 else max_len
        parts.append(text[:cut])
        text = text[cut:]
    if text:
        parts.append(text)
    return parts
