import hashlib

import pymupdf
import pytest

from ducky_rag.pipeline import ProcessingError, process_document


def make_pdf(text="A binary search reduces the sorted search range by half."):
    with pymupdf.open() as pdf:
        page = pdf.new_page()
        page.insert_text((72, 100), text)
        return pdf.tobytes()


def test_real_pdf_round_trip_and_stable_provenance():
    data = make_pdf()
    result = process_document(data, "42", "pdf")
    second = process_document(data, "42", "pdf")
    assert result["sourceSha256"] == hashlib.sha256(data).hexdigest()
    assert result["pageCount"] == 1
    assert "binary search" in result["markdown"]
    assert result["chunks"][0]["blockId"] == second["chunks"][0]["blockId"]
    assert all(block["doc_id"] == "42" for block in result["canonicalBlocks"])
    assert all(chunk["blockId"] for chunk in result["chunks"])


def test_table_facts_keep_headers_and_no_url_embedding_noise():
    data = b"# Test specifications\n\n| Component | Limit |\n| --- | --- |\n| Ducky upload | 20 MB |\n| Processing | 200 pages |\n\n![A yellow device](https://private.invalid/picture.png)\n"
    result = process_document(data, "7", "md")
    texts = "\n".join(chunk["textForEmbedding"] for chunk in result["chunks"])
    assert "20 MB" in texts
    assert "200 pages" in texts
    assert "private.invalid" not in texts
    assert any(block["type"] == "table_row" for block in result["canonicalBlocks"])


def test_no_silent_hundred_chunk_truncation():
    text = "\n\n".join(f"# Section {i}\n\nUnique concept number {i} has an important definition for retrieval." for i in range(120))
    result = process_document(text.encode(), "8", "md")
    assert len(result["chunks"]) >= 120
    assert "119" in result["chunks"][-1]["content"]


def test_image_only_pdf_requires_ocr():
    with pymupdf.open() as pdf:
        page = pdf.new_page()
        pix = pymupdf.Pixmap(pymupdf.csRGB, (0, 0, 100, 100), False)
        pix.clear_with(255)
        page.insert_image(page.rect, pixmap=pix)
        with pytest.raises(ProcessingError, match="OCR_REQUIRED"):
            process_document(pdf.tobytes(), "1", "pdf")


@pytest.mark.parametrize("data,document_id,kind,error", [
    (b"not a pdf", "1", "pdf", "INVALID_PDF"),
    (b"%PDF-1.7\nsynthetic malformed input\n", "1", "pdf", "INVALID_PDF"),
    (b"some useful text", "../2", "txt", "INVALID_DOCUMENT_ID"),
    (b"", "1", "pdf", "FILE_SIZE_LIMIT"),
    (b"some useful text", "1", "exe", "UNSUPPORTED_FILE_TYPE"),
    (b"\xff\xfe", "1", "txt", "UTF8_REQUIRED"),
])
def test_bad_input_is_explicit(data, document_id, kind, error):
    with pytest.raises(ProcessingError, match=error):
        process_document(data, document_id, kind)


def test_blank_pdf_is_not_indexed():
    with pymupdf.open() as pdf:
        pdf.new_page()
        with pytest.raises(ProcessingError, match="NO_SEARCHABLE_TEXT"):
            process_document(pdf.tobytes(), "1", "pdf")
