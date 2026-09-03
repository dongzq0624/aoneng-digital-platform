ALTER TABLE kb_qa_record ADD COLUMN IF NOT EXISTS conversation_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_qa_conversation_time ON kb_qa_record(conversation_id, created_at DESC)
    WHERE conversation_id IS NOT NULL;
