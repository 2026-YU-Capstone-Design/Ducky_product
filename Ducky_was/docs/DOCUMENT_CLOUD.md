# Spring document cloud mode

Default `DOCUMENT_MODE=local` retains local HTTP and synchronous DOCX/PPTX processing.
Cloud mode supports PDF/TXT/MD/CSV/JSON and does not call the Python HTTP worker.

Required explicit settings:
- DOCUMENT_MODE=cloud
- DOCUMENT_BUCKET: dedicated versioned document bucket
- DOCUMENT_REGION=ap-southeast-2
- DOCUMENT_COMPLETION_QUEUE_URL: completion queue, with DLQ/redrive configured by infrastructure owner
- RAG_PROCESSOR_REVISION: immutable Lambda release revision
- DOCUMENT_AWS_CREDENTIALS=instance-role (deployed) or profile + DOCUMENT_AWS_PROFILE (explicit named profile)
- Local emulation only: DOCUMENT_AWS_CREDENTIALS=emulator + DOCUMENT_AWS_ENDPOINT=http://127.0.0.1:<port>; dummy test credentials are used. No default credential chain.
- DOCUMENT_COMPLETION_POLL_ENABLED=false disables consumption for controlled tests.
- Normalizer/chunker defaults match the shared Lambda v2 contract; change only with the release contract.

The deployment Compose file explicitly forwards the required cloud settings and the
completion-poll switch. Its default remains `DOCUMENT_MODE=local`; cloud mode requires
all verified values above and `DOCUMENT_AWS_CREDENTIALS=instance-role`. Copy only the
settings template from `.env.example`, supply secrets through the existing secret
channel, and validate interpolation with `docker compose --env-file .env config --quiet`
before a separately authorized deployment. A host `.env` alone does not inject arbitrary
variables into a container. Named-profile or emulator use needs a separate local setup;
the deployment Compose does not mount developer AWS profiles or forward emulator endpoints.

`document_upload_attempts` commits owner/document/UUID/hash/key/size before S3 upload. Each retry writes a fresh UUID/key and reads the previous exact object version. Upload metadata is committed in a second transaction. Completion before this commit is retried, never acknowledged. A process crash between S3 upload and metadata commit needs operator reconciliation; it does not trust queue metadata as DB truth.

Completion validates immutable source metadata, deterministic job hash, release, manifest and all three artifact hashes/lengths, run prefix, result document/hash/counts and canonical/Markdown correspondence. Failed conversion applies only to the current attempt. Duplicate, deleted document and older attempt deliveries cannot recreate or replace current data. Completion owns a pessimistic row lock through index persistence. Commit failure propagates to the consumer. Only successful return from its independent transaction permits SQS deletion. Errors are logged by exception class, without body, filename or source content.

The index boundary is `DocumentIndexWriter.replace`, called inside the completion transaction. This branch writes the existing develop lexical index. BM25 prerequisite 0f8ecc7 remains separately owned and is not copied/merged here. The team integration must connect its index write to this boundary before `markIndexed` if BM25 persistence is added. Candidate search already queries by authenticated user, then indexed/rag-enabled documents.

Existing HTTP API endpoints remain; response fields added: attemptId, sourceType, sourceSha256, sourceVersionId, sourceETag, retryAllowed. Source download URLs are not exposed. `processed` is conversion only; `indexed` is reached after validated DB index commit. Queue poison messages require the infrastructure DLQ policy. Physical S3/version cleanup is deferred: document deletion removes DB chunks and document visibility but retains attempt audit records and S3 objects. No live cleanup lifecycle is installed here.

Schema: review the existing schema and take a backup before upgrading. Apply the scripts in
`src/main/resources/db/manual/` in order before starting the new backend with
`JPA_DDL_AUTO=validate`.

1. `20261119_document_cloud.sql` adds cloud source fields and upload attempt history.
2. `20261120_conversation_harness.sql` adds conversation selection and response strategy.
3. `20261124_document_legacy_schema_bridge.sql` adds the 15 nullable metadata columns needed by the older production schema.

These are repeatable manual migrations for existing schemas. They leave pre-existing row
values in place and require an explicit run before deployment.

`ManualReleaseSchemaTest` checks the conversation upgrade on H2 and the complete legacy
production upgrade on disposable PostgreSQL 17. Its optional
`RELEASE_SCHEMA_TEST_DB_URL` accepts only a loopback URL for `ducky_release_schema_test`
with fixed synthetic credentials: this test creates/drops its own schema and must never
target a shared database. The Compose configuration tests render synthetic inputs only;
they do not start containers or call AWS/OpenAI.

Tests: `./gradlew test` uses H2 + S3/SQS test doubles, no credentials or network service needed. `CloudDocumentFlowTest` may use an isolated local PostgreSQL with DOCUMENT_TEST_DB_URL; fixed dummy test user/password are ducky_test/local-test-only. Never point this test at a shared/live DB because test profile creates/drops tables. An optional DOCUMENT_PRECURSOR_RESULT points to synthetic process_document JSON produced by the separately owned Lambda image; it substitutes server identity and output paths only to exercise actual result shape, without importing precursor commits. No PDF or private logs are committed.

AWS SDK references: [S3](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/migration-s3-client.html), [SQS](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-sqs-messages.html).
Actual AWS IAM, S3 notifications/versioning, queue routing/DLQ and end-to-end Lambda remain deployment verification tasks. No LLM is involved in ingestion tests.

## Semantic validation follow-up

`DocumentResultValidator` is shared by HTTP processing and the cloud index boundary. Canonical IDs must be nonempty strings, unique within the document, and owned by the result document ID. Each chunk primary ID must exist; an optional blockIds list must be nonempty, contain distinct existing string IDs and start with the primary ID. A chunk's optional pageNumber must agree with source.page on every referenced block. A page is optional when canonical sources have no page (legacy and non-PDF); a present page must be an integer within bounds. Multiple split chunks may reference the same canonical block.

Content is a required nonblank string; textForEmbedding may be absent for legacy results (fallback to content), but when present must be a nonblank string. Required result fields are checked without string/number coercion. Writer validation runs before deleting prior chunks. The retry API and retryAllowed now share execution-mode checks, including disabled local pipeline and local rows viewed in cloud mode.

The opt-in cross-language tests use DOCUMENT_LAMBDA_SNAPSHOT (read-only owner snapshot) and DOCUMENT_LAMBDA_IMAGE (pin an existing local image digest). The Python helper uses `DocumentWorker.handle` with native S3/SQS test doubles under Docker `--network none`, creates both manifest/jobId and all artifact bytes, then passes those unchanged to the actual Java completion transaction. Synthetic long TXT covers repeated split-block references and synthetic two-page PDF covers persisted page provenance. Result JSON, hashes and Python version are stored only under excluded build/cross-language-*.json. This is local emulation, not AWS verification, and does not import owner source into the Git diff.
