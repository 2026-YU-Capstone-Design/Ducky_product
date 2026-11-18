"""Real HTTP integration test. Requires the local worker and rag-local Spring API."""
import argparse
import json
import time
import uuid
import urllib.error
import urllib.request
from pathlib import Path

import pymupdf


def request(base, path, token=None, payload=None, raw=None, content_type=None, method=None, expected=200):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if payload is not None:
        raw = json.dumps(payload).encode()
        content_type = "application/json"
    if content_type:
        headers["Content-Type"] = content_type
    req = urllib.request.Request(base + path, data=raw, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            status, data = response.status, response.read()
    except urllib.error.HTTPError as error:
        status, data = error.code, error.read()
    assert status == expected, (path, status, data[:200])
    return json.loads(data).get("data")


def upload(base, token, filename, data, expected=200):
    boundary = "ducky-" + uuid.uuid4().hex
    body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            'Content-Type: application/pdf\r\n\r\n').encode() + data + f'\r\n--{boundary}--\r\n'.encode()
    return request(base, "/api/documents", token, raw=body,
                   content_type=f"multipart/form-data; boundary={boundary}", expected=expected)


def await_document(base, token, doc_id):
    deadline = time.monotonic() + 90
    seen = []
    while time.monotonic() < deadline:
        doc = request(base, f"/api/documents/{doc_id}", token)
        seen.append(doc["indexingStatus"])
        if doc["indexingStatus"] in {"indexed", "failed"}:
            return doc, sorted(set(seen))
        time.sleep(0.3)
    raise AssertionError("processing did not finish")


def pdf(text):
    with pymupdf.open() as document:
        page = document.new_page()
        page.insert_text((72, 100), text)
        return document.tobytes()


def run(base, output):
    started = time.monotonic()
    tokens = []
    for prefix in ("owner", "other"):
        uid = prefix + uuid.uuid4().hex[:12]
        auth = request(base, "/api/auth/signup", payload={"name": uid, "email": uid + "@example.com",
                       "login_id": uid, "password": "LocalRag123!"})
        tokens.append(auth["accessToken"])
    owner, other = tokens
    request(base, "/api/documents", expected=401)
    data = pdf("Quasar retry budget is exactly seven attempts. Stop processing after attempt seven.")
    if output:
        output.mkdir(parents=True, exist_ok=True)
        (output / "quasar.pdf").write_bytes(data)
    uploaded = upload(base, owner, "same-name.pdf", data)
    assert uploaded["indexingStatus"] == "pending"
    doc_id = uploaded["id"]
    document, states = await_document(base, owner, doc_id)
    assert document["indexingStatus"] == "indexed", document
    assert document["chunkCount"] > 0
    foreign = upload(base, other, "same-name.pdf", pdf("Private nebula launch code belongs exclusively to another user."))
    foreign_doc, _ = await_document(base, other, foreign["id"])
    assert foreign_doc["indexingStatus"] == "indexed"
    query = {"query": "Quasar retry budget", "limit": 3}
    hits = request(base, "/api/documents/search", owner, payload=query)["results"]
    assert hits[0]["documentId"] == doc_id and hits[0]["blockId"]
    assert request(base, "/api/documents/search", other, payload=query)["results"] == []
    assert request(base, "/api/documents/search", owner, payload={**query, "documentIds": []})["results"] == []
    request(base, "/api/documents/search", other, payload={**query, "documentIds": [int(doc_id)]}, expected=404)
    for path, method in [(f"/api/documents/{doc_id}", "GET"), (f"/api/documents/{doc_id}", "DELETE"),
                         (f"/api/documents/{doc_id}/retry", "POST")]:
        request(base, path, other, method=method, expected=404)
    conversation = request(base, "/api/chat/conversations", owner, payload={"title": "RAG integration", "topic": "Quasar"})
    turn = request(base, "/api/chat/messages", owner, payload={"conversationId": int(conversation["id"]),
                    "message": "Quasar retry budget", "inputType": "text"})
    reply = turn["aiResponse"]
    assert "seven" in reply["content"] and reply["responseMode"] == "extractive"
    assert reply["sources"][0]["documentId"] == doc_id
    history = request(base, f'/api/chat/conversations/{conversation["id"]}', owner)
    assert history["messages"][-1]["sources"][0]["blockId"] == hits[0]["blockId"]
    hint = request(base, "/api/chat/hints", owner, payload={"conversationId": int(conversation["id"])})
    assert hint["hintMessage"]["type"] == "hint" and hint["hintMessage"]["responseMode"] == "extractive"
    upload(base, owner, "too-large.pdf", b"x" * (20 * 1024 * 1024 + 1), expected=413)
    bad = upload(base, owner, "broken.pdf", b"%PDF-not-valid")
    failed, _ = await_document(base, owner, bad["id"])
    assert failed["indexingStatus"] == "failed"
    retried = request(base, f'/api/documents/{bad["id"]}/retry', owner, method="POST")
    assert retried["id"] == bad["id"] and retried["indexingStatus"] == "pending"
    again, _ = await_document(base, owner, bad["id"])
    assert again["indexingStatus"] == "failed"
    request(base, f"/api/documents/{doc_id}/retry", owner, method="POST", expected=409)
    request(base, f"/api/documents/{doc_id}", owner, method="DELETE")
    assert request(base, "/api/documents/search", owner, payload=query)["results"] == []
    # Preserve a debugging exercise, including its actual exception, in citations.
    code = "def average(values):\n    return sum(values) / len(values)\n\naverage([])"
    lesson_data = (Path(__file__).resolve().parents[1] / "tests" / "fixtures" / "debugging_session.md").read_bytes()
    lesson = upload(base, owner, "debugging-session.md", lesson_data)
    indexed_lesson, _ = await_document(base, owner, lesson["id"])
    assert indexed_lesson["indexingStatus"] == "indexed"
    lesson_query = {"query": "average ZeroDivisionError", "limit": 3}
    lesson_hits = request(base, "/api/documents/search", owner, payload=lesson_query)["results"]
    assert any(code in hit["content"] for hit in lesson_hits)
    assert any("ZeroDivisionError: division by zero" in hit["content"] for hit in lesson_hits)
    assert request(base, "/api/documents/search", other, payload=lesson_query)["results"] == []
    lesson_reply = request(base, "/api/chat/messages", owner, payload={
        "conversationId": int(conversation["id"]), "message": "average ZeroDivisionError", "inputType": "text"})["aiResponse"]
    assert any(code in source["content"] for source in lesson_reply["sources"])
    assert any("ZeroDivisionError: division by zero" in source["content"] for source in lesson_reply["sources"])
    request(base, f'/api/documents/{lesson["id"]}', owner, method="DELETE")
    report = {"passed": True, "elapsedSeconds": round(time.monotonic() - started, 2),
              "statesObserved": states, "chunkCount": document["chunkCount"],
              "processingSeconds": document["processingSeconds"],
              "checks": ["real PDF upload and worker", "canonical provenance", "sparse vector retrieval",
                         "authenticated user isolation", "foreign ID rejection", "document scope",
                         "extractive answer and hint", "persisted citations", "20MiB size rejection",
                         "failure and retry", "delete removes search results",
                         "debugging exercise preserves code and stack trace through search and citations"]}
    if output:
        (output / "smoke-result.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    run(args.base_url, args.output)
