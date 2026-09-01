package com.example.rag.chat.service;

import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量存储服务接口。负责向量集合初始化、payload 索引管理、upsert、
 * 按文档删除和四种检索变体（默认检索 + 低召回降级自适应检索）。
 * 所有操作均通过 Qdrant REST API 同步执行。
 */
public interface QdrantService {

    /**
     * 写入或更新向量点。
     *
     * @param id      向量点 ID（与 PostgreSQL 分块主键保持稳定映射）
     * @param vector  嵌入向量
     * @param payload 业务 payload
     */
    void upsert(long id, List<Float> vector, Map<String, Object> payload);

    /**
     * 按文档 ID 删除该文档的全部向量点。
     *
     * @param docId 文档 ID
     */
    void deleteByDocument(long docId);

    /**
     * 在全部可访问范围内执行向量检索。
     *
     * @param vector 查询向量
     * @param limit 返回的最大条数
     * @return 命中列表，按相似度降序
     */
    List<Map<String, Object>> search(List<Float> vector, int limit);

    /**
     * 在指定知识库范围内执行向量检索。
     *
     * @param vector        查询向量
     * @param limit        返回的最大条数
     * @param allowedKbIds 允许检索的知识库 ID 列表
     * @return 命中列表，按相似度降序
     */
    List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds);

    /**
     * 低召回降级检索。当主检索未返回候选时使用更低的阈值进行自适应检索。
     *
     * @param vector        查询向量
     * @param limit        返回的最大条数
     * @param allowedKbIds 允许检索的知识库 ID 列表
     * @return 命中列表
     */
    List<Map<String, Object>> searchAdaptive(List<Float> vector, int limit, List<Long> allowedKbIds);
}
