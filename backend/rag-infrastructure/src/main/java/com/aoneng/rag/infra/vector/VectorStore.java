package com.aoneng.rag.infra.vector;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

/**
 * 向量存储服务接口。
 * 统一封装向量写入、删除和检索能力。
 */
public interface VectorStore {

    /**
     * 写入或更新向量点。
     *
     * @param id      唯一标识符
     * @param vector  嵌入向量
     * @param payload 关联的元数据
     */
    void upsert(long id, List<Float> vector, Map<String, Object> payload);

    /** Upsert a point containing both dense and sparse vectors. */
    default void upsert(long id, List<Float> denseVector, SortedMap<Long, Float> sparseVector,
                        Map<String, Object> payload) {
        upsert(id, denseVector, payload);
    }

    /**
     * 按文档 ID 删除该文档的全部向量点。
     *
     * @param docId 文档 ID
     */
    void deleteByDocument(long docId);

    /**
     * 执行向量检索。
     *
     * @param vector 查询向量
     * @param limit  返回的最大条数
     * @return 命中列表，每项包含 id、score、payload
     */
    List<Map<String, Object>> search(List<Float> vector, int limit);

    /**
     * 执行带过滤条件的向量检索。
     *
     * @param vector       查询向量
     * @param limit       返回的最大条数
     * @param allowedKbIds 允许的知识库 ID 列表（空列表直接返回空结果）
     * @return 命中列表
     */
    List<Map<String, Object>> search(List<Float> vector, int limit, List<Long> allowedKbIds);

    /**
     * 低召回降级自适应检索。
     *
     * @param vector       查询向量
     * @param limit       返回的最大条数
     * @param allowedKbIds 允许的知识库 ID 列表
     * @return 命中列表
     */
    List<Map<String, Object>> searchAdaptive(List<Float> vector, int limit, List<Long> allowedKbIds);

    /** Sparse-vector retrieval for application-side fusion. */
    default List<Map<String, Object>> sparseSearch(SortedMap<Long, Float> sparseVector,
                                                    int limit, List<Long> allowedKbIds) {
        return List.of();
    }

    /** Hybrid dense+sparse search. Legacy stores fall back to dense search. */
    default List<Map<String, Object>> hybridSearch(List<Float> denseVector,
                                                   SortedMap<Long, Float> sparseVector,
                                                   int limit, List<Long> allowedKbIds) {
        return searchAdaptive(denseVector, limit, allowedKbIds);
    }
}
