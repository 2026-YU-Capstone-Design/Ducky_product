"""Bounded page-aware retrieval units with complete canonical provenance."""
from .normalization import split_large_text

CHUNKER_VERSION = "ducky-page-chunks-v2"
MAX_TEXT = 1400


def build_chunks(blocks, source_type):
    chunks = []
    pending = []

    def emit(items, texts=None):
        if not items:
            return
        texts = texts if texts is not None else [item["text"] for item in items]
        content = "\n\n".join(texts)
        first, last = items[0], items[-1]
        heading = " > ".join(first["heading_path"])
        chunk = {"blockId": first["block_id"], "blockIds": [b["block_id"] for b in items],
                 "chunkKey": f'{first["block_id"]}:{len(chunks)}', "content": content,
                 "headingPath": heading, "blockType": first["type"] if len(items) == 1 else "page_section",
                 "previousBlockId": None, "nextBlockId": None,
                 "pageNumber": first["source"].get("page"),
                 "sourceLineStart": first["source"]["line_start"],
                 "sourceLineEnd": last["source"]["line_end"],
                 "textForEmbedding": f"{heading}\n{content}".strip()}
        chunks.append(chunk)

    def flush():
        nonlocal pending
        emit(pending)
        pending = []

    for block in blocks:
        if not block["searchable"]:
            if block["type"] == "heading":
                flush()
            continue
        parts = ([block["text"]] if block["type"] in {"table_row", "math"}
                 else split_large_text(block["text"], max_len=MAX_TEXT))
        if source_type != "pdf" or len(parts) != 1 or len(parts[0]) > MAX_TEXT:
            flush()
            for part in parts:
                if part.strip():
                    emit([block], [part])
            continue
        if pending and (pending[0]["source"].get("page") != block["source"].get("page")
                        or pending[0]["heading_path"] != block["heading_path"]
                        or sum(len(b["text"]) + 2 for b in pending) + len(block["text"]) > MAX_TEXT):
            flush()
        pending.append(block)
    flush()
    for i, chunk in enumerate(chunks):
        chunk["previousBlockId"] = chunks[i-1]["blockId"] if i else None
        chunk["nextBlockId"] = chunks[i+1]["blockId"] if i+1 < len(chunks) else None
    return chunks
