"""Keep the code, observations and questions needed for debugging practice."""
from pathlib import Path

import pytest

from ducky_rag.pipeline import process_document


def searchable(result):
    return "\n".join(chunk["content"] for chunk in result["chunks"])


@pytest.mark.parametrize("text", [
    "`retry_count = max_attempts - 1`",
    r"$T(n) = 2T(n/2) + n$",
    "README.md",
    "x < y",
    "HTTP 404",
    "주소 인코딩 예: %EA%B0%80",
])
def test_short_technical_terms_and_formulas_remain_searchable(text):
    result = process_document(text.encode(), "42", "md")
    assert text in searchable(result)


@pytest.mark.parametrize("fence", ["```", "~~~"])
def test_code_keeps_indentation_blank_lines_symbols_and_heading_context(fence):
    code = 'def retry_count(x):\n    if x < 3:\n        return x + 1\n\n    return "https://example.test/a_b"'
    body = f"# 재시도 예제\n\n{fence}python\n{code}\n{fence}\n"
    result = process_document(body.encode(), "42", "md")
    assert code in result["markdown"]
    block = next(b for b in result["canonicalBlocks"] if b["type"] == "code")
    assert block["text"] == code
    assert block["heading_path"] == ["재시도 예제"]
    assert block["attrs"]["language"] == "python"
    assert code in searchable(result)


def test_truth_table_keeps_small_numeric_values_with_headers():
    body = "# AND\n\n| A | B | A & B |\n| --- | --- | --- |\n| 0 | 1 | 0 |\n| 1 | 1 | 1 |"
    result = process_document(body.encode(), "42", "md")
    text = searchable(result)
    assert "A: 0" in text and "B: 1" in text and "A & B: 0" in text
    assert len([b for b in result["canonicalBlocks"] if b["type"] == "table_row"]) == 2


def test_debugging_labels_remain_content_without_implicit_classification():
    result = process_document("# 디버깅 학습\n\n재현 조건\n\n빈 배열을 전달했을 때의 실행 결과를 설명합니다.".encode(), "42", "md")
    assert result["report"]["doc_type"] == "learning_document"
    assert "재현 조건" in searchable(result)
    assert all(b["doc_type"] == "learning_document" for b in result["canonicalBlocks"])


def test_document_identity_comes_from_server_not_uploaded_metadata():
    result = process_document(b'---\ndocument_id: 999\nuser_id: 999\n---\n\nretry_count = 3', "42", "md")
    assert 'document_id: "42"' in result["markdown"]
    assert "user_id: 999" not in result["markdown"]
    assert all(b["doc_id"] == "42" for b in result["canonicalBlocks"])


def test_long_code_can_be_reconstructed_from_chunks_without_losing_whitespace():
    code = "\n".join(f"    output_{i} = input_value + {i}" for i in range(130))
    result = process_document(f"```python\n{code}\n```".encode(), "42", "md")
    assert len(result["chunks"]) > 1
    assert "".join(c["content"] for c in result["chunks"]) == code
    assert all(len(c["content"]) <= 1400 for c in result["chunks"])


def test_code_pipe_in_table_does_not_shift_column_values():
    body = "| Operator | Meaning |\n| --- | --- |\n| `a | b` | bitwise OR |"
    text = searchable(process_document(body.encode(), "42", "md"))
    assert "Operator: `a | b`" in text
    assert "Meaning: bitwise OR" in text


@pytest.mark.parametrize("source_type,body", [
    ("txt", "#include <stdio.h>\n#define MAX_RETRY 3"),
    ("csv", "value,label\n0,false\n1,true"),
    ("json", '{\n  "retry_count": 3,\n  "endpoint": "https://example.test/a_b"\n}'),
])
def test_plain_and_structured_sources_keep_their_literal_contents(source_type, body):
    result = process_document(body.encode(), "42", source_type)
    assert body in searchable(result)


def test_display_math_keeps_operators_subscripts_and_line_breaks():
    body = "$$\nx_i < y_i\nT(n) = 2T(n/2) + n\n$$"
    result = process_document(body.encode(), "42", "md")
    assert searchable(result) == body
    assert any(block["type"] == "math" for block in result["canonicalBlocks"])


def test_debugging_notes_keep_reproduction_trace_expectation_and_questions():
    data = (Path(__file__).parent / "fixtures" / "debugging_session.md").read_bytes()
    result = process_document(data, "42", "md")
    blocks = result["canonicalBlocks"]
    code = next(b for b in blocks if b["type"] == "code" and b["attrs"]["language"] == "python")
    trace = next(b for b in blocks if b["type"] == "code" and b["attrs"]["language"] == "text")
    assert code["heading_path"] == ["빈 리스트 평균 계산 디버깅", "재현 코드"]
    assert "    return sum(values) / len(values)" in code["text"]
    assert trace["heading_path"] == ["빈 리스트 평균 계산 디버깅", "실행 결과"]
    assert 'File "average.py", line 2, in average' in trace["text"]
    assert "ZeroDivisionError: division by zero" in trace["text"]
    for section in ("기대 동작", "확인 질문"):
        assert any(b["searchable"] and b["heading_path"][-1:] == [section] for b in blocks)
    lines = result["markdown"].splitlines()
    for block in (code, trace):
        source = block["source"]
        assert "\n".join(lines[source["line_start"] - 1:source["line_end"]]) == block["raw"]
    assert "정답을 바로 수정하기 전에" in searchable(result)


def test_heading_context_resets_for_a_new_debugging_example():
    data = "# 빈 리스트\n\n## 재현\n\naverage([])\n\n# 인덱스 오류\n\n## 재현\n\nvalues[3]".encode()
    first = process_document(data, "42", "md")
    other = process_document(data, "43", "md")
    assert first["chunks"][-1]["headingPath"] == "인덱스 오류 > 재현"
    assert first["chunks"][-1]["content"] == "values[3]"
    assert {c["blockId"] for c in first["chunks"]}.isdisjoint(c["blockId"] for c in other["chunks"])
