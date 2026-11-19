-- Review and run separately before JPA_DDL_AUTO=validate. No Flyway baseline is assumed.
BEGIN;
ALTER TABLE learning_documents ADD COLUMN IF NOT EXISTS attempt_id varchar(36);
ALTER TABLE learning_documents ADD COLUMN IF NOT EXISTS source_bucket varchar(255);
ALTER TABLE learning_documents ADD COLUMN IF NOT EXISTS source_version_id varchar(1024);
ALTER TABLE learning_documents ADD COLUMN IF NOT EXISTS source_etag varchar(128);
ALTER TABLE learning_documents ADD COLUMN IF NOT EXISTS completed_job_id varchar(64);
ALTER TABLE learning_documents ADD COLUMN IF NOT EXISTS processor_revision varchar(255);
CREATE TABLE IF NOT EXISTS document_upload_attempts (
 attempt_id varchar(36) PRIMARY KEY, user_id bigint NOT NULL, document_id bigint NOT NULL,
 source_key varchar(255) NOT NULL, source_bucket varchar(255) NOT NULL,
 source_sha256 varchar(64) NOT NULL, source_version_id varchar(1024), source_etag varchar(128),
 source_size bigint NOT NULL, created_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS document_upload_attempts_owner_doc ON document_upload_attempts(user_id,document_id);
COMMIT;
