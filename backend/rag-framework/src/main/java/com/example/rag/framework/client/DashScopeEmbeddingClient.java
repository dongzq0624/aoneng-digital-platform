package com.example.rag.framework.client;

import java.util.List;

/**
 * DashScope Embedding API 抽象。默认实现通过 OpenAI 兼容的 <code>/embeddings</code> 端点调用，
 * 业务服务应只依赖本接口而非自行构造 HTTP 客户端。
 */
public interface DashScopeEmbeddingClient {

    /**
     * Embed a single text. Convenience wrapper for {@link #embedAll(List)}.
     */
    EmbeddingVector embed(String text);

    /**
     * Embed a batch of texts. Returns the vectors in the same order as the input
     * list. Implementations must return vectors whose length matches the configured
     * embedding dimension.
     */
    List<EmbeddingVector> embedAll(List<String> texts);

    /** Embedding result. */
    record EmbeddingVector(List<Float> vector, int dimensions) {
        public EmbeddingVector {
            vector = vector == null ? List.of() : List.copyOf(vector);
            if (dimensions <= 0) dimensions = vector.size();
        }
    }
}
