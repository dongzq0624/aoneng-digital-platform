CREATE OR REPLACE VIEW kb_chunk_quality_metrics AS
SELECT d.id AS doc_id,
       d.file_name,
       COUNT(DISTINCT p.id) AS parent_count,
       COUNT(c.id) AS child_count,
       COALESCE(AVG(p.token_count), 0) AS avg_parent_tokens,
       COALESCE(AVG(c.token_count), 0) AS avg_child_tokens,
       COUNT(*) FILTER (WHERE c.content = p.content) AS identical_child_count,
       COUNT(*) FILTER (WHERE p.embedding_id IS NULL OR p.embedding_id = '') AS parent_without_embedding_count,
       COUNT(DISTINCT p.id) FILTER (WHERE (p.metadata->>'start_page_no') ~ '^[0-9]+$'
                                           AND (p.metadata->>'end_page_no') ~ '^[0-9]+$'
                                           AND (p.metadata->>'start_page_no')::int < (p.metadata->>'end_page_no')::int)
           AS cross_page_parent_count,
       CASE WHEN COUNT(DISTINCT p.id) = 0 THEN 0
            ELSE ROUND(100.0 * COUNT(DISTINCT p.id) FILTER (WHERE (p.metadata->>'start_page_no') ~ '^[0-9]+$'
                                           AND (p.metadata->>'end_page_no') ~ '^[0-9]+$'
                                           AND (p.metadata->>'start_page_no')::int < (p.metadata->>'end_page_no')::int)
                      / COUNT(DISTINCT p.id), 2) END AS cross_page_parent_ratio,
       COUNT(c.id) FILTER (WHERE c.embedding_id IS NULL OR c.embedding_id = '') AS child_without_embedding_count
  FROM kb_document d
  LEFT JOIN kb_parent_chunk p ON p.doc_id = d.id
  LEFT JOIN kb_chunk c ON c.parent_id = p.id
 GROUP BY d.id, d.file_name;
