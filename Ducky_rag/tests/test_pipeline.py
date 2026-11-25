import hashlib
import base64
import json
from pathlib import Path

import pymupdf
import pytest

from ducky_rag.pipeline import ProcessingError, process_document
from ducky_rag import pdf_extraction, pdf_layout, pdf_vision


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


def add_scanned_page(pdf, text, *, korean=False, page_number=None):
    with pymupdf.open() as source:
        page = source.new_page()
        page.insert_text((60, 110), text, fontname="korea" if korean else "helv", fontsize=30)
        scan = pdf.new_page()
        scan.insert_image(scan.rect, pixmap=page.get_pixmap(dpi=150))
        if page_number is not None:
            scan.insert_text((scan.rect.width / 2, scan.rect.height - 30), str(page_number))


def test_image_only_pdf_reports_missing_ocr_runtime(monkeypatch, tmp_path):
    monkeypatch.setenv("TESSDATA_PREFIX", str(tmp_path))
    monkeypatch.delenv("RAG_VISION_MODEL", raising=False)
    with pymupdf.open() as pdf:
        add_scanned_page(pdf, "Scan has natural language text")
        with pytest.raises(ProcessingError, match="PDF_VISION_REQUIRED"):
            process_document(pdf.tobytes(), "1", "pdf")


@pytest.fixture
def ocr_models():
    try:
        folder = Path(pymupdf.get_tessdata())
    except (RuntimeError, TypeError):
        pytest.skip("Install the pinned OCR language data to run raster PDF regressions")
    if not all((folder / f"{language}.traineddata").is_file() for language in ("eng", "kor")):
        pytest.skip("Install the pinned Korean and English OCR language data")


def test_korean_english_scan_and_native_text_keep_pdf_page_numbers(ocr_models):
    with pymupdf.open() as pdf:
        pdf.new_page().insert_text((72, 100), "Native text uses a stable title and page reference.")
        add_scanned_page(pdf, "요구 사항을 정리합니다", korean=True, page_number=2)
        add_scanned_page(pdf, "Quasar retry budget is seven attempts.")
        pdf.new_page().insert_text((72, 100), "Final native page retains its original position.")
        saved = pdf.tobytes()
    result = process_document(saved, "12", "pdf")
    assert result["pageCount"] == 4
    assert "OCR_TEXT_EXTRACTED" in result["warnings"]
    assert "IMAGES_NOT_ENRICHED" not in result["warnings"]
    by_page = {page: "\n".join(c["content"] for c in result["chunks"] if c["pageNumber"] == page) for page in range(1, 5)}
    assert "Native text" in by_page[1]
    assert "요구" in by_page[2] and "정리" in by_page[2]
    assert "Quasar retry budget" in by_page[3]
    assert "Final native page" in by_page[4]
    assert any(block["source"].get("extraction") == "ocr" and block["source"]["page"] == 2
               for block in result["canonicalBlocks"])


def test_blank_raster_page_does_not_become_searchable(ocr_models):
    with pymupdf.open() as pdf:
        page = pdf.new_page()
        pix = pymupdf.Pixmap(pymupdf.csRGB, (0, 0, 100, 100), False)
        pix.clear_with(255)
        page.insert_image(page.rect, pixmap=pix)
        with pytest.raises(ProcessingError, match="OCR_NO_TEXT"):
            process_document(pdf.tobytes(), "1", "pdf")


@pytest.fixture
def vision_response(monkeypatch, tmp_path):
    monkeypatch.setenv("RAG_VISION_MODEL", "fixture-model")
    monkeypatch.setenv("OPENAI_API_KEY", "synthetic-only")
    monkeypatch.setenv("TESSDATA_PREFIX", str(tmp_path))
    return monkeypatch


def vision_reply(status, markdown):
    return {"status": "completed", "output": [{"type": "message", "role": "assistant",
             "content": [{"type": "output_text", "text": json.dumps(
                 {"status": status, "markdown": markdown})}]}]}


def test_recover_scan_and_mixed_visual_page_without_sending_native_pages(vision_response):
    def fake_api(payload, key, timeout):
        assert key == "synthetic-only" and 0 < timeout <= 20
        assert payload["store"] is False and "tools" not in payload
        request = payload["input"][0]["content"]
        media, jpeg = request[1]["image_url"].split(",", 1)
        assert media == "data:image/jpeg;base64"
        pix = pymupdf.Pixmap(base64.b64decode(jpeg, validate=True))
        assert max(pix.width, pix.height) <= 2304
        calls.append(request[0]["text"])
        text = ("The recovered scan states seven attempts." if len(calls) == 1
                else "| Period | Failure rate |\n| --- | --- |\n| March | 8 percent |")
        return vision_reply("content", text)

    calls = []
    vision_response.setattr(pdf_vision, "_response", fake_api)
    with pymupdf.open() as pdf:
        pdf.new_page().insert_text((72, 100), "Native opening page.")
        add_scanned_page(pdf, "Scanned content unavailable to the local OCR engine.")
        chart = pdf.new_page()
        chart.insert_text((72, 100), "This native paragraph describes the chart.")
        for x in (80, 125, 170):
            chart.draw_rect(pymupdf.Rect(x, 180, x + 15, 230), fill=(0, .4, .8))
        pdf.new_page().insert_text((72, 100), "Native final page.")
        result = process_document(pdf.tobytes(), "9", "pdf")
    assert [call.startswith("Transcribe missing") for call in calls] == [False, True]
    assert result["pageCount"] == 4
    assert result["warnings"] == ["PDF_VISION_EXTRACTED"]
    by_page = {page: "\n".join(c["content"] for c in result["chunks"] if c["pageNumber"] == page)
               for page in range(1, 5)}
    assert "Native opening" in by_page[1]
    assert "seven attempts" in by_page[2]
    assert "native paragraph" in by_page[3] and "8 percent" in by_page[3]
    assert "Native final" in by_page[4]
    visual = [b for b in result["canonicalBlocks"] if b["source"].get("extraction") == "vision"]
    assert {b["source"]["page"] for b in visual} == {2, 3}
    assert all("native paragraph" not in b["text"] for b in visual)


@pytest.mark.parametrize("response", [
    vision_reply("unreadable", ""), vision_reply("no_extra", ""), {"status": "incomplete"},
    vision_reply("content", "## Only a heading"),
])
def test_missing_visual_evidence_blocks_indexing_even_with_other_text(vision_response, response):
    vision_response.setattr(pdf_vision, "_response", lambda *_: response)
    with pymupdf.open() as pdf:
        pdf.new_page().insert_text((72, 100), "Readable but incomplete document.")
        add_scanned_page(pdf, "Do not silently skip this scanned page.")
        with pytest.raises(ProcessingError, match="PDF_VISION_UNREADABLE"):
            process_document(pdf.tobytes(), "1", "pdf")


def test_only_failed_layout_page_uses_vision(vision_response):
    original = pdf_layout._native_markdown
    calls = []

    def convert(document, indexes):
        if 1 in indexes:
            raise RuntimeError("broken layout")
        return original(document, indexes)

    def fake_api(payload, *_):
        calls.append(payload["input"][0]["content"][0]["text"])
        return vision_reply("content", "Diagram detail recovered after the parser failed.")

    vision_response.setattr(pdf_layout, "_native_markdown", convert)
    vision_response.setattr(pdf_vision, "_response", fake_api)
    with pymupdf.open() as pdf:
        for text in ("First page", "Local transcript", "Last page"):
            pdf.new_page().insert_text((72, 100), text)
        result = process_document(pdf.tobytes(), "1", "pdf")
    assert len(calls) == 1
    assert any(c["pageNumber"] == 2 and "Diagram detail" in c["content"] for c in result["chunks"])
    assert any(c["pageNumber"] == 3 and "Last page" in c["content"] for c in result["chunks"])


def test_vision_page_limit_fails_before_sending_any_images(vision_response):
    vision_response.setattr(pdf_extraction, "MAX_VISION_PAGES", 1)
    vision_response.setattr(pdf_vision, "_response", lambda *_: pytest.fail("unexpected API call"))
    with pymupdf.open() as pdf:
        for _ in range(2):
            add_scanned_page(pdf, "Each scan requires visual recovery.")
        with pytest.raises(ProcessingError, match="PDF_VISION_LIMIT"):
            process_document(pdf.tobytes(), "1", "pdf")


def test_vision_budget_prioritizes_required_pages_and_preserves_native_text(vision_response):
    vision_response.setattr(pdf_extraction, "MAX_VISION_PAGES", 1)
    calls = []

    def fake_api(payload, *_):
        calls.append(payload["input"][0]["content"][0]["text"])
        return vision_reply("content", "Required scan recovered.")

    vision_response.setattr(pdf_vision, "_response", fake_api)
    with pymupdf.open() as pdf:
        chart = pdf.new_page()
        chart.insert_text((72, 100), "The chart page has useful native text.")
        for x in (80, 125, 170):
            chart.draw_rect(pymupdf.Rect(x, 180, x + 15, 230), fill=(0, .4, .8))
        add_scanned_page(pdf, "Required scan is the second page.")
        result = process_document(pdf.tobytes(), "1", "pdf")

    assert len(calls) == 1 and calls[0].startswith("Recover")
    assert result["warnings"] == ["PDF_VISION_EXTRACTED", "IMAGES_NOT_ENRICHED"]
    assert any(c["pageNumber"] == 1 and "native text" in c["content"] for c in result["chunks"])
    assert any(c["pageNumber"] == 2 and "Required scan recovered" in c["content"] for c in result["chunks"])


def test_late_unique_raster_is_selected_ahead_of_repeated_images(vision_response):
    vision_response.setattr(pdf_extraction, "MAX_VISION_PAGES", 1)
    inspected = []

    class Reader:
        def __init__(self, model):
            pass

        def read(self, page, *, supplement, deadline):
            assert supplement
            inspected.append(page.number + 1)
            return "The final graph reports a rate of eight percent."

    vision_response.setattr(pdf_extraction, "OpenAIPageReader", Reader)
    with pymupdf.open() as pdf:
        repeat = pymupdf.Pixmap(pymupdf.csRGB, (0, 0, 50, 50), False)
        repeat.clear_with(75)
        unique = pymupdf.Pixmap(pymupdf.csRGB, (0, 0, 55, 55), False)
        unique.clear_with(180)
        for index in range(13):
            page = pdf.new_page()
            page.insert_text((72, 100), f"Native heading and chapter text for slide {index + 1}.")
            page.insert_image(pymupdf.Rect(72, 130, 282, 340), pixmap=repeat)
        page = pdf.new_page()
        page.insert_text((72, 100), "The graphical result is shown in the chart below.")
        page.insert_image(pymupdf.Rect(72, 130, 282, 340), pixmap=unique)
        result = process_document(pdf.tobytes(), "1", "pdf")

    assert inspected == [14]
    assert "IMAGES_NOT_ENRICHED" in result["warnings"]
    assert any(c["pageNumber"] == 14 and "eight percent" in c["content"] for c in result["chunks"])


@pytest.mark.parametrize("has_model", [False, True])
def test_native_text_remains_searchable_when_optional_vision_is_unavailable(vision_response, has_model):
    if not has_model:
        vision_response.delenv("RAG_VISION_MODEL")

    def unavailable(*_):
        assert has_model
        raise pdf_vision.PdfVisionError("PDF_VISION_UNAVAILABLE")

    vision_response.setattr(pdf_vision, "_response", unavailable)
    with pymupdf.open() as pdf:
        chart = pdf.new_page()
        chart.insert_text((72, 100), "The chart page has useful native text.")
        for x in (80, 125, 170):
            chart.draw_rect(pymupdf.Rect(x, 180, x + 15, 230), fill=(0, .4, .8))
        result = process_document(pdf.tobytes(), "1", "pdf")

    assert result["warnings"] == ["IMAGES_NOT_ENRICHED"]
    assert any(c["pageNumber"] == 1 and "native text" in c["content"] for c in result["chunks"])


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
