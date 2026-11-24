-- Add the document metadata columns required when upgrading from 543c6c2.
-- Run before enabling the f48b4a4 backend in validate mode.
BEGIN;
SET LOCAL lock_timeout = '5s';

ALTER TABLE public.learning_documents
    ADD COLUMN IF NOT EXISTS source_key varchar(255),
    ADD COLUMN IF NOT EXISTS source_type varchar(20),
    ADD COLUMN IF NOT EXISTS source_sha256 varchar(64),
    ADD COLUMN IF NOT EXISTS processing_token varchar(40),
    ADD COLUMN IF NOT EXISTS processing_started_at timestamptz,
    ADD COLUMN IF NOT EXISTS processing_seconds double precision,
    ADD COLUMN IF NOT EXISTS chunk_count integer,
    ADD COLUMN IF NOT EXISTS processing_error varchar(100),
    ADD COLUMN IF NOT EXISTS processing_warnings varchar(2000);

ALTER TABLE public.document_chunks
    ADD COLUMN IF NOT EXISTS embedding_model varchar(80),
    ADD COLUMN IF NOT EXISTS block_id varchar(100),
    ADD COLUMN IF NOT EXISTS heading_path text,
    ADD COLUMN IF NOT EXISTS provenance_json text;

ALTER TABLE public.messages
    ADD COLUMN IF NOT EXISTS sources_json text,
    ADD COLUMN IF NOT EXISTS response_mode varchar(30);

COMMIT;
