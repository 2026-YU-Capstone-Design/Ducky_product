"""Private local worker. Serialize conversions: PyMuPDF is not thread safe."""
import argparse
import json
import os
import secrets
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlsplit

from .pipeline import MAX_BYTES, ProcessingError, process_document


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self.reply(200 if self.path == "/health" else 404,
                   {"service": "ducky-rag", "schemaVersion": "ducky-document-v1"})

    def do_POST(self):
        expected = os.environ.get("RAG_WORKER_TOKEN", "")
        if expected and not secrets.compare_digest(
                self.headers.get("Authorization", ""), f"Bearer {expected}"):
            self.reply(401, {"error": "UNAUTHORIZED"})
            return
        parsed = urlsplit(self.path)
        if parsed.path != "/process":
            self.reply(404, {"error": "NOT_FOUND"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_BYTES:
                self.reply(413, {"error": "FILE_SIZE_LIMIT"})
                return
            self.connection.settimeout(30)
            data = self.rfile.read(length)
            if len(data) != length:
                raise ProcessingError("INCOMPLETE_UPLOAD")
            query = parse_qs(parsed.query)
            result = process_document(data, query.get("documentId", [""])[0],
                                      query.get("sourceType", [""])[0])
            self.reply(200, result)
        except ProcessingError as error:
            self.reply(422, {"error": str(error)})
        except Exception:
            # Do not put document contents or library exception details in logs.
            self.reply(422, {"error": "CONVERSION_FAILED"})

    def log_message(self, format, *args):
        pass


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8091)
    args = parser.parse_args()
    if args.host not in {"127.0.0.1", "localhost"} and not os.environ.get("RAG_WORKER_TOKEN"):
        parser.error("RAG_WORKER_TOKEN is required on a non-loopback interface")
    print(f"Ducky RAG worker listening on {args.host}:{args.port}", flush=True)
    HTTPServer((args.host, args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
