package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 检索管道配置。包含查询改写、重排、稠密 / 关键词召回、候选集与上下文窗口大小等参数。
 * 修改时需同步复核 Milvus 性能与上下文窗口对模型回答质量的影响。
 */
@ConfigurationProperties(prefix = "rag.retrieval")
public record RagRetrievalProperties(boolean queryRewriteEnabled, boolean rerankEnabled,
                                     int maxRewrites, int denseLimit, int keywordLimit,
                                     int candidateLimit, int contextLimit, int rrfConstant) {
    public RagRetrievalProperties {
        // 为空或非正数时回退到默认值，避免运行时出现除零或窗口过小的退化情况。
        maxRewrites = positive(maxRewrites, 3);
        denseLimit = positive(denseLimit, 40);
        keywordLimit = positive(keywordLimit, 40);
        candidateLimit = positive(candidateLimit, 40);
        contextLimit = positive(contextLimit, 8);
        rrfConstant = positive(rrfConstant, 60);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
