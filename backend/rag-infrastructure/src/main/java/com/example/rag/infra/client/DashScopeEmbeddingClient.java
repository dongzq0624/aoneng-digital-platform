package com.example.rag.infra.client;

import java.util.List;

public interface DashScopeEmbeddingClient {
    EmbeddingVector embed(String text);
    List<EmbeddingVector> embedAll(List<String> texts);
    record EmbeddingVector(List<Float> vector, int dimensions) {
        public EmbeddingVector {
            vector = vector == null ? List.of() : List.copyOf(vector);
            if (dimensions <= 0) dimensions = vector.size();
        }
    }
}
