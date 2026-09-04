-- Keep existing knowledge-base chunk settings intact while using a moderate
-- parent context size for newly created rows.
ALTER TABLE kb_knowledge_base
    ALTER COLUMN chunk_size SET DEFAULT 1200;

ALTER TABLE kb_knowledge_base
    ALTER COLUMN chunk_overlap SET DEFAULT 64;
