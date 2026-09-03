ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS object_etag VARCHAR(128);
ALTER TABLE kb_document ADD COLUMN IF NOT EXISTS last_scanned_at TIMESTAMPTZ;
CREATE INDEX IF NOT EXISTS idx_kb_document_scan ON kb_document(deleted, last_scanned_at);
