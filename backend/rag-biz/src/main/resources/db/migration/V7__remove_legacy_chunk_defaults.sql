-- Migrate knowledge bases that still carry the former default pair.
-- Custom chunk settings are preserved.
UPDATE kb_knowledge_base
   SET chunk_size = 2000,
       chunk_overlap = 64,
       updated_at = NOW()
 WHERE chunk_size = 512
   AND chunk_overlap = 64;
