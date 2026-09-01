package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Milvus connection and vector collection settings. */
@ConfigurationProperties(prefix = "milvus")
public record MilvusProperties(String host, int port, String token, String collection,
                               int dimension, double scoreThreshold) {
    public MilvusProperties {
        host = host == null || host.isBlank() ? "localhost" : host;
        if (port <= 0) port = 19530;
        token = token == null ? "" : token;
        collection = collection == null || collection.isBlank() ? "kb_embeddings" : collection;
        if (dimension <= 0) dimension = 1024;
        scoreThreshold = Math.max(0D, Math.min(1D, scoreThreshold));
    }
}
