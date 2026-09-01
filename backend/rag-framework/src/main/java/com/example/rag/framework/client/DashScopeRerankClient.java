package com.example.rag.framework.client;

import java.util.List;

/**
 * Rerank API 抽象，默认实现对接 DashScope <code>gte-rerank</code> 系列模型。
 */
public interface DashScopeRerankClient {

    /**
     * Rerank the supplied documents against the query and return the new ordering.
     * Implementations should return an empty list when the input is empty.
     */
    List<RerankHit> rerank(String query, List<String> documents, int topN);

    /** A single rerank result. */
    record RerankHit(int index, double score, String text) {
    }
}
