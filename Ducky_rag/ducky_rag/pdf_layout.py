"""Preserve PDF page boundaries and code text-layer reading order, without OCR."""
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


def extract_pages(document):
    converted = pymupdf4llm.to_markdown(document, page_chunks=True, hdr_info=False,
                                       write_images=False, force_text=True)
    pages = []
    for number, (page, item) in enumerate(zip(document, converted), 1):
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
            body = item["text"].strip("\n")
            lines = body.splitlines()
            while lines and (not lines[-1].strip() or lines[-1].strip(" *_") in footers):
                lines.pop()
            if lines and title and lines[0].strip("# *_ ") == title:
                lines.pop(0)
            body = "\n".join(lines).strip("\n")
            if not body.strip() and raw.strip():
                body = raw
        body = body.replace("\r\n", "\n").replace("\r", "\n")
        heading = title or f"PDF page {number}"
        pages.append({"page": number, "title": heading,
                      "markdown": f"# {heading}\n\n{body}".rstrip("\n")})
    return pages


def locate_pages(markdown, body, pages):
    # finalize_markdown adds only its trusted frontmatter and a trailing LF.
    start = markdown[:len(markdown) - len(body) - 1].count("\n") + 1
    ranges = []
    for page in pages:
        length = page["markdown"].count("\n")
        ranges.append({"page": page["page"], "title": page["title"],
                       "line_start": start, "line_end": start + length})
        start += length + 2
    return ranges
