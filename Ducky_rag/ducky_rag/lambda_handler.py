"""SQS -> versioned S3 source -> existing processor -> S3 -> completion SQS."""
from __future__ import annotations

import json
import logging
import os
import re
from dataclasses import dataclass
from functools import lru_cache
from uuid import uuid4

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

from .aws_contract import (COMPLETION_SCHEMA, MANIFEST_SCHEMA, ContractError,
                           DocumentJob, json_bytes, parse_notification, sha256)
from .chunking import CHUNKER_VERSION
from .normalization import NORMALIZER_VERSION
from .pipeline import MAX_BYTES, SCHEMA_VERSION, ProcessingError, process_document

LOG = logging.getLogger(__name__)
MAX_MANIFEST_BYTES = 64 * 1024


@dataclass(frozen=True)
class Settings:
    bucket: str
    request_queue_arn: str
    completion_queue_url: str
    processor_revision: str
    region: str

    @classmethod
    def from_env(cls) -> Settings:
        values = [os.environ[name] for name in (
            "DOCUMENT_BUCKET", "DOCUMENT_REQUEST_QUEUE_ARN",
            "DOCUMENT_COMPLETION_QUEUE_URL", "RAG_PROCESSOR_REVISION", "AWS_REGION")]
        if any(not value.strip() for value in values):
            raise ContractError("MISSING_CONFIGURATION")
        settings = cls(*values)
        if (not re.fullmatch(r"[A-Za-z0-9._-]{1,128}", settings.processor_revision)
                or not re.fullmatch(
                    rf"arn:aws:sqs:{re.escape(settings.region)}:[0-9]{{12}}:[A-Za-z0-9_-]+",
                    settings.request_queue_arn)
                or not re.fullmatch(
                    rf"https://sqs\.{re.escape(settings.region)}\.amazonaws\.com/"
                    r"[0-9]{12}/[A-Za-z0-9_-]+", settings.completion_queue_url)):
            raise ContractError("INVALID_CONFIGURATION")
        return settings


def _client_error(error: ClientError, *codes: str) -> bool:
    return str(error.response.get("Error", {}).get("Code")) in codes


class DocumentWorker:
    def __init__(self, settings: Settings, s3, sqs):
        self.settings, self.s3, self.sqs = settings, s3, sqs

    def _load_manifest(self, job: DocumentJob) -> tuple[dict, bytes] | None:
        try:
            response = self.s3.get_object(Bucket=job.bucket, Key=job.manifest_key)
        except ClientError as error:
            # AccessDenied must not be interpreted as a missing commit.
            if _client_error(error, "NoSuchKey", "404"):
                return None
            raise
        stream = response["Body"]
        try:
            raw = stream.read(MAX_MANIFEST_BYTES + 1)
        finally:
            stream.close()
        if len(raw) > MAX_MANIFEST_BYTES:
            raise ContractError("MANIFEST_SIZE_LIMIT")
        manifest = json.loads(raw)
        if (manifest.get("schemaVersion") != MANIFEST_SCHEMA
                or any(manifest.get(key) != value for key, value in job.identity.items())
                or manifest.get("source") != job.source
                or manifest.get("resultSchemaVersion") != SCHEMA_VERSION
                or manifest.get("normalizerVersion") != NORMALIZER_VERSION
                or manifest.get("chunkerVersion") != CHUNKER_VERSION
                or manifest.get("status") not in {"processed", "failed"}):
            raise ContractError("MANIFEST_MISMATCH")
        return manifest, raw

    def _read_source(self, job: DocumentJob) -> tuple[bytes, str]:
        if not 0 < job.size <= MAX_BYTES:
            raise ProcessingError("FILE_SIZE_LIMIT")
        response = self.s3.get_object(Bucket=job.bucket, Key=job.key,
            VersionId=job.version_id, IfMatch=f'"{job.etag}"')
        stream = response["Body"]
        try:
            if (response.get("VersionId") != job.version_id
                    or response.get("ETag", "").strip('"') != job.etag
                    or response.get("ContentLength") != job.size):
                raise ContractError("SOURCE_VERSION_MISMATCH")
            expected_hash = response.get("Metadata", {}).get("source-sha256", "")
            if not re.fullmatch(r"[0-9a-f]{64}", expected_hash):
                raise ContractError("SOURCE_HASH_REQUIRED")
            data = stream.read(MAX_BYTES + 1)
        finally:
            stream.close()
        if len(data) != job.size or sha256(data) != expected_hash:
            raise ContractError("SOURCE_HASH_MISMATCH")
        return data, expected_hash

    def _put_artifact(self, job: DocumentJob, key: str, data: bytes,
                      content_type: str) -> dict:
        self.s3.put_object(Bucket=job.bucket, Key=key, Body=data,
                           ContentType=content_type, IfNoneMatch="*")
        return {"key": key, "sha256": sha256(data), "bytes": len(data)}

    def _build_manifest(self, job: DocumentJob) -> dict:
        manifest = {"schemaVersion": MANIFEST_SCHEMA, **job.identity,
            "source": job.source, "sourceSequencer": job.sequencer,
            "resultSchemaVersion": SCHEMA_VERSION,
            "normalizerVersion": NORMALIZER_VERSION,
            "chunkerVersion": CHUNKER_VERSION,
            "status": "failed", "errorCode": None, "artifacts": {}}
        try:
            data, source_hash = self._read_source(job)
            # Unique run paths prevent concurrent deliveries from mixing artifacts.
            run = job.output_prefix + f"runs/{uuid4()}/"
            result = process_document(data, job.document_id, job.source_type,
                source_key=job.key, markdown_key=run + "document.md")
        except ProcessingError as error:
            manifest["errorCode"] = str(error)
            return manifest
        artifacts = {
            "markdown": ("document.md", result["markdown"].encode("utf-8"),
                         "text/markdown; charset=utf-8"),
            "canonical": ("canonical.jsonl", b"".join(
                json_bytes(block) + b"\n" for block in result["canonicalBlocks"]),
                "application/x-ndjson"),
            "result": ("result.json", json_bytes(result), "application/json"),
        }
        for name, (filename, content, content_type) in artifacts.items():
            manifest["artifacts"][name] = self._put_artifact(
                job, run + filename, content, content_type)
        manifest.update(status="processed", sourceSha256=source_hash,
            warnings=result["warnings"], chunkCount=len(result["chunks"]),
            pageCount=result["pageCount"], processingSeconds=result["processingSeconds"])
        return manifest

    def process(self, job: DocumentJob) -> None:
        saved = self._load_manifest(job)
        if saved is None:
            manifest = self._build_manifest(job)
            raw = json_bytes(manifest)
            if len(raw) > MAX_MANIFEST_BYTES:
                raise ContractError("MANIFEST_SIZE_LIMIT")
            try:
                self.s3.put_object(Bucket=job.bucket, Key=job.manifest_key,
                    Body=raw, ContentType="application/json", IfNoneMatch="*")
            except ClientError as error:
                if not _client_error(error, "PreconditionFailed", "412",
                                     "ConditionalRequestConflict", "409"):
                    raise
                # Another invocation won. Publish only its committed manifest.
                saved = self._load_manifest(job)
                if saved is None:
                    raise ContractError("MANIFEST_COMMIT_CONFLICT") from error
        if saved is not None:
            manifest, raw = saved
        completion = {"schemaVersion": COMPLETION_SCHEMA, **job.identity,
            "status": manifest["status"], "errorCode": manifest["errorCode"],
            "source": job.source, "manifest": {"bucket": job.bucket,
                "key": job.manifest_key, "sha256": sha256(raw), "bytes": len(raw)}}
        # If sending fails, fail this SQS item. Redelivery reuses the commit.
        self.sqs.send_message(QueueUrl=self.settings.completion_queue_url,
                              MessageBody=json_bytes(completion).decode("utf-8"))

    def handle(self, event: dict, context=None) -> dict:
        records = event.get("Records")
        if (not isinstance(records, list) or not records
                or any(not isinstance(record, dict)
                       or not isinstance(record.get("messageId"), str)
                       or not record["messageId"] for record in records)):
            raise ContractError("INVALID_SQS_BATCH")
        failures = []
        for record in records:
            try:
                if (record.get("eventSource") != "aws:sqs"
                        or record.get("eventSourceARN") != self.settings.request_queue_arn):
                    raise ContractError("UNEXPECTED_REQUEST_QUEUE")
                # Finish before Lambda's hard timeout to preserve partial results.
                if context and context.get_remaining_time_in_millis() < 15000:
                    raise ContractError("INSUFFICIENT_TIME")
                jobs = parse_notification(record["body"], bucket=self.settings.bucket,
                    region=self.settings.region,
                    processor_revision=self.settings.processor_revision)
                for job in jobs:
                    if context and context.get_remaining_time_in_millis() < 15000:
                        raise ContractError("INSUFFICIENT_TIME")
                    self.process(job)
            except Exception as error:
                # No source text, keys, uploaded names, or exception messages in logs.
                LOG.warning("document_item_failed message_id=%s error_type=%s",
                            record["messageId"], type(error).__name__)
                failures.append({"itemIdentifier": record["messageId"]})
        return {"batchItemFailures": failures}


@lru_cache(maxsize=1)
def _worker() -> DocumentWorker:
    settings = Settings.from_env()
    config = Config(retries={"mode": "standard", "max_attempts": 3},
                    connect_timeout=5, read_timeout=60)
    return DocumentWorker(settings,
        boto3.client("s3", region_name=settings.region, config=config),
        boto3.client("sqs", region_name=settings.region, config=config))


def handler(event, context):
    return _worker().handle(event, context)
