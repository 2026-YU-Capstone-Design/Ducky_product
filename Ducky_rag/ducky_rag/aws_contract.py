"""Versioned S3 input and completion contracts; no uploaded identity metadata."""
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from urllib.parse import unquote_plus
from uuid import UUID

from .chunking import CHUNKER_VERSION
from .normalization import NORMALIZER_VERSION
from .pipeline import SCHEMA_VERSION

MANIFEST_SCHEMA = "ducky-document-manifest-v1"
COMPLETION_SCHEMA = "ducky-document-completion-v1"
SOURCE_KEY = re.compile(
    r"originals/(?P<user_id>[1-9][0-9]{0,18})/"
    r"(?P<document_id>[1-9][0-9]{0,18})/"
    r"(?P<attempt_id>[0-9a-f-]{36})/source\.(?P<source_type>pdf|txt|md|csv|json)"
)


class ContractError(ValueError):
    """Invalid infrastructure/job contract; retry then quarantine in the DLQ."""


def json_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      separators=(",", ":"), allow_nan=False).encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


@dataclass(frozen=True)
class DocumentJob:
    bucket: str
    key: str
    version_id: str
    etag: str
    size: int
    sequencer: str
    user_id: str
    document_id: str
    attempt_id: str
    source_type: str
    processor_revision: str

    @property
    def source(self) -> dict:
        return {"bucket": self.bucket, "key": self.key,
                "versionId": self.version_id, "eTag": self.etag,
                "size": self.size}

    @property
    def identity(self) -> dict:
        return {"jobId": self.job_id, "userId": self.user_id,
                "documentId": self.document_id, "attemptId": self.attempt_id,
                "processorRevision": self.processor_revision}

    @property
    def job_id(self) -> str:
        return sha256(json_bytes({"source": self.source,
            "processorRevision": self.processor_revision,
            "resultSchemaVersion": SCHEMA_VERSION,
            "normalizerVersion": NORMALIZER_VERSION,
            "chunkerVersion": CHUNKER_VERSION}))

    @property
    def output_prefix(self) -> str:
        return (f"markdown/{self.user_id}/{self.document_id}/"
                f"{self.attempt_id}/{self.job_id}/")

    @property
    def manifest_key(self) -> str:
        return self.output_prefix + "manifest.json"


def parse_notification(body: str, *, bucket: str, region: str,
                       processor_revision: str) -> list[DocumentJob]:
    """Accept only native S3 notifications delivered by the configured SQS."""
    try:
        event = json.loads(body)
        if (event.get("Event") == "s3:TestEvent"
                and event.get("Service") == "Amazon S3"
                and event.get("Bucket") == bucket):
            return []
        records = event["Records"]
        if not isinstance(records, list) or not records:
            raise ContractError("EMPTY_S3_RECORDS")
        jobs = []
        for record in records:
            version = record["eventVersion"].split(".")
            if (len(version) != 2 or version[0] != "2" or int(version[1]) < 1
                    or record["eventSource"] != "aws:s3"
                    or record["awsRegion"] != region
                    or not record["eventName"].startswith("ObjectCreated:")):
                raise ContractError("INVALID_S3_EVENT")
            s3 = record["s3"]
            obj = s3["object"]
            key = unquote_plus(obj["key"], errors="strict")
            match = SOURCE_KEY.fullmatch(key)
            if (s3["bucket"]["name"] != bucket or not match
                    or str(UUID(match["attempt_id"])) != match["attempt_id"]):
                raise ContractError("INVALID_SOURCE_KEY")
            version_id, etag, size = obj["versionId"], obj["eTag"], obj["size"]
            sequencer = obj["sequencer"]
            if (not isinstance(version_id, str) or not version_id
                    or version_id == "null" or len(version_id) > 1024
                    or not isinstance(etag, str)
                    or not re.fullmatch(r"[0-9a-f]{32}(?:-[1-9][0-9]*)?", etag)
                    or type(size) is not int or size < 0
                    or not isinstance(sequencer, str)
                    or not re.fullmatch(r"[0-9A-Fa-f]+", sequencer)):
                raise ContractError("INVALID_SOURCE_VERSION")
            jobs.append(DocumentJob(bucket, key, version_id, etag, size,
                                    sequencer, **match.groupdict(),
                                    processor_revision=processor_revision))
        return jobs
    except (KeyError, TypeError, AttributeError, ValueError) as error:
        raise ContractError("INVALID_S3_NOTIFICATION") from error
