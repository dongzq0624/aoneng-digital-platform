-- Use token terminology for knowledge-base parent chunk defaults.
-- The legacy 512-token default is removed by V7; custom values remain untouched.
ALTER TABLE kb_knowledge_base
    ALTER COLUMN chunk_size SET DEFAULT 2000;

ALTER TABLE kb_knowledge_base
    ALTER COLUMN chunk_overlap SET DEFAULT 64;

COMMENT ON COLUMN kb_knowledge_base.chunk_size IS '父块 token 上限';
COMMENT ON COLUMN kb_knowledge_base.chunk_overlap IS '父块重叠 token 数';
