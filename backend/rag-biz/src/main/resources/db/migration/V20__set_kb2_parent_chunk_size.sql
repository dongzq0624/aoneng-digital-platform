-- Align the existing employee-policy knowledge base with the current parent
-- chunk default. Keep its configured overlap unchanged.
UPDATE kb_knowledge_base
   SET chunk_size = 1200,
       updated_at = now()
 WHERE id = 2
   AND deleted = false;
