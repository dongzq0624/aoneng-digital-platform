package com.aoneng.rag.infra.client;

import java.util.List;

public interface DashScopeRerankClient {
    List<RerankHit> rerank(String query, List<String> documents, int topN);
    record RerankHit(int index, double score, String text) {}
}
