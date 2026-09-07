-- text-embedding-v4 is the embedding model used by DashScope and the Qwen3 tokenizer.
UPDATE kb_knowledge_base
   SET embedding_model = 'text-embedding-v4',
       updated_at = NOW()
 WHERE embedding_model IS NULL OR embedding_model = '' OR embedding_model = 'bge-m3';
