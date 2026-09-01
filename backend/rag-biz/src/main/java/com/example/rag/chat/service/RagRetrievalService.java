package com.example.rag.chat.service;

import java.util.List;
import java.util.Map;

/**
 * 检索流水线服务接口。整合查询改写、密集向量检索、关键词检索和可选的重排步骤。
 * 通过 {@code rag.retrieval.*} 配置项进行配置（参见 {@code RagRetrievalProperties}）。
 */
public interface RagRetrievalService {

    /**
     * 在用户允许的知识库范围内执行检索。
     *
     * @param question       用户问题
     * @param permittedKbIds 用户授权的知识库 ID 列表；空列表将短路返回空结果
     * @return 检索结果
     */
    RetrievalResult retrieve(String question, List<Long> permittedKbIds);

    /**
     * 检索结果聚合。{@code hits} 是有序去重后的向量库命中列表（最佳优先）；
     * {@code trace} 记录流水线诊断信息（查询数量、密集/关键词数量、重排状态）用于可观测性。
     *
     * @param hits  检索命中文档列表
     * @param trace 流水线诊断追踪信息
     */
    record RetrievalResult(List<Map<String, Object>> hits, Map<String, Object> trace) {
    }
}
