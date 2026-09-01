package com.example.rag.framework.client;

import java.util.List;
import java.util.Map;

/**
 * 向量检索命中的统一视图，作为框架层数据类型承载，不携带业务语义（业务映射在 rag-biz 完成）。
 *
 * @param id      Qdrant 点 ID（字符串形式，避免数值溢出）
 * @param score   相似度分数
 * @param payload 业务 payload（含 chunk_id、doc_id、kb_id、content、file_name、visibility、dept_id 等）
 */
public record VectorHit(String id, double score, Map<String, Object> payload) {
}
