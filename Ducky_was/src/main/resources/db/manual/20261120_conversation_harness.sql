-- Apply after the document-cloud migration on an existing managed schema.
-- Review separately before JPA_DDL_AUTO=validate; safe to apply again.
BEGIN;
ALTER TABLE conversations ADD COLUMN IF NOT EXISTS selected_document_ids_json text;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS response_strategy varchar(40);
COMMIT;
