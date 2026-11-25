"""Preserve PDF page boundaries, text-layer layout, and OCR provenance."""
import re
from collections import Counter

import pymupdf4llm

CODE_LINE = re.compile(
    r"^\s*(?://|/\*|[{}]\s*(?:while|;|$)|"
    r"(?:while|for|if|switch|return|public|private|class|def|void|boolean|int)\b.*[();:{]|"
    r"[A-Za-z_]\w*(?:\[[^\]]+\])?\s*(?:=|\+\+|--|[+*/-]=))")
PAGE_NUMBER = re.compile(r"(?:\d+[.-])*\d+|page\s+\d+", re.I)


def page_title(page):
    lines = [line for block in page.get_text("dict")["blocks"] if block["type"] == 0
             for line in block["lines"]]
    sizes = Counter()
    for line in lines:
        for span in line["spans"]:
            sizes[round(span["size"])] += len(span["text"].strip())
    if not sizes:
        return "", []
    body_size = sizes.most_common(1)[0][0]
    top = [line for line in lines if line["bbox"][1] < page.rect.height * .22]
    largest = max((span["size"] for line in top for span in line["spans"]), default=0)
    if largest < body_size * 1.2:
        return "", []
    title_lines = [line for line in top if max(s["size"] for s in line["spans"]) >= largest * .9]
    title_lines.sort(key=lambda line: (line["bbox"][1], line["bbox"][0]))
    title = " ".join("".join(span["text"] for span in line["spans"]).strip() for line in title_lines)
    # A title can share its block with a smaller subscript or subtitle. Recover
    # the whole short header block rather than deleting these smaller spans.
    header_blocks = [block for block in page.get_text("blocks") if block[6] == 0
                     and block[3] < page.rect.height * .22
                     and any(abs(block[1] - line["bbox"][1]) < 2
                             and block[0] < line["bbox"][2] and block[2] > line["bbox"][0]
                             for line in title_lines)]
    if header_blocks:
        full_title = " ".join(re.sub(r"\s+", " ", block[4]).strip() for block in header_blocks)
        if len(full_title) <= 240:
            title = full_title
    return (title, [line["bbox"] for line in title_lines]) if len(title) <= 240 else ("", [])


def _native_markdown(document, indexes):
    chunks = pymupdf4llm.to_markdown(document, pages=indexes, page_chunks=True, hdr_info=False,
                                    write_images=False, force_text=True)
    if len(chunks) != len(indexes) or any(not isinstance(chunk.get("text"), str) for chunk in chunks):
        raise ValueError("Incomplete PDF layout")
    return {index: chunk["text"] for index, chunk in zip(indexes, chunks)}


def _text_key(text):
    # Layout can add emphasis and HTML line breaks inside a native text line.
    text = re.sub(r"<br\s*/?>", "", text, flags=re.I)
    return "".join(char.casefold() for char in text if char.isalnum())


def _has_omitted_text(raw, markdown):
    rendered = _text_key(markdown)
    return any(key and key not in rendered for line in raw.splitlines()
               if (key := _text_key(line)))


def extract_pages(document, scans):
    native_pages = [index for index in range(len(document)) if index not in scans]
    converted = {}
    if native_pages:
        try:
            converted = _native_markdown(document, native_pages)
        except Exception:
            # A broken page must not force visual recovery of unaffected pages.
            for index in native_pages:
                try:
                    converted.update(_native_markdown(document, [index]))
                except Exception:
                    continue

    failures = {}
    pages = []
    for number, page in enumerate(document, 1):
        heading = f"PDF page {number}"
        if page.number in scans:
            pages.append({"page": number, "title": heading, "extraction": "ocr",
                          "markdown": f"# {heading}\n\n{scans[page.number]}".rstrip("\n")})
            continue
        if page.number not in converted:
            try:
                raw = page.get_text("text", sort=True).strip()
            except (ValueError, RuntimeError):
                raw = ""
            failures[page.number] = bool(raw and not PAGE_NUMBER.fullmatch(raw))
            pages.append({"page": number, "title": heading,
                          "markdown": f"# {heading}\n\n{raw}".rstrip("\n")})
            continue
        title, title_boxes = page_title(page)
        body_blocks = []
        footers = []
        for block in page.get_text("blocks"):
            if block[6] != 0:
                continue
            text = block[4].strip("\n")
            if block[1] > page.rect.height * .92 and PAGE_NUMBER.fullmatch(text.strip()):
                footers.append(text.strip())
                continue
            title_text = re.sub(r"\s+", " ", title).strip()
            block_text = re.sub(r"\s+", " ", text).strip()
            if (title_boxes and block_text and block_text in title_text
                    and any(abs(block[1] - box[1]) < 2 for box in title_boxes)):
                continue
            body_blocks.append(text)
        raw = "\n\n".join(body_blocks).strip("\n")
        code_lines = sum(bool(CODE_LINE.match(line)) for line in raw.splitlines())
        if code_lines >= 2:
            # get_text blocks retain the PDF content stream's column order;
            # Markdown layout can move a closing brace before the code body.
            width = max([2] + [len(run) for run in re.findall(r"`+", raw)]) + 1
            fence = "`" * width
            body = f"{fence}text\n{raw}\n{fence}"
        else:
            body = converted[page.number].strip("\n")
            lines = body.splitlines()
            while lines and (not lines[-1].strip() or lines[-1].strip(" *_") in footers):
                lines.pop()
            if lines and title and lines[0].strip("# *_ ") == title:
                lines.pop(0)
            body = "\n".join(lines).strip("\n")
            if not body.strip() and raw.strip():
                failures[page.number] = True
                body = raw
            elif _has_omitted_text(raw, f"{title}\n{body}"):
                # Vector-backed boxes/tables can disappear from an otherwise
                # successful layout result. Retain its tables, then preserve a
                # literal, position-sorted transcript of the same page. Do not
                # invent table headers or label/value relationships from it.
                transcript = page.get_text("text", sort=True).strip() or raw
                fence = "`" * (max([2] + [len(run) for run in re.findall(r"`+", transcript)]) + 1)
                body += f"\n\n## 원문 텍스트\n\n{fence}text\n{transcript}\n{fence}"
        body = body.replace("\r\n", "\n").replace("\r", "\n")
        heading = title or f"PDF page {number}"
        pages.append({"page": number, "title": heading,
                      "markdown": f"# {heading}\n\n{body}".rstrip("\n")})
    return pages, failures


def locate_pages(markdown, body, pages):
    # finalize_markdown adds only its trusted frontmatter and a trailing LF.
    start = markdown[:len(markdown) - len(body) - 1].count("\n") + 1
    ranges = []
    for page in pages:
        length = page["markdown"].count("\n")
        source = {"page": page["page"], "title": page["title"], "extraction": page.get("extraction"),
                  "line_start": start, "line_end": start + length}
        if "vision_line_offset" in page:
            source["vision_line_start"] = start + page["vision_line_offset"]
        ranges.append(source)
        start += length + 2
    return ranges
