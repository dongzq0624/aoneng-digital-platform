package com.aoneng.rag.application.retrieval;

import java.util.List;
import java.util.Map;

/**
 * 检索应用服务接口。
 * 整合查询改写、向量检索、关键词检索和重排步骤。
 */
public interface RetrievalService {

    /**
     * 在用户允许的知识库范围内执行检索。
     *
     * @param question       用户问题
     * @param permittedKbIds 用户授权的知识库 ID 列表；空列表将短路返回空结果
     * @return 检索结果
     */
    RetrievalResult retrieve(String question, List<Long> permittedKbIds);

    /**
     * 检索结果聚合。
     *
     * @param hits  检索命中文档列表
     * @param trace 流水线诊断追踪信息
     */
    record RetrievalResult(List<Map<String, Object>> hits, Map<String, Object> trace) {
    }
}
