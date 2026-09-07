-- Migrate knowledge bases that still use the former 2000-token default.
-- Rows with other configured sizes are preserved.
UPDATE kb_knowledge_base
   SET chunk_size = 1200,
       updated_at = NOW()
 WHERE chunk_size = 2000
   AND deleted = FALSE;
