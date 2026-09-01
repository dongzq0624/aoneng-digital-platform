package com.example.rag.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant 向量数据库配置。{@code collection} 与 payload 字段索引需与 {@link com.example.rag.framework.client.QdrantVectorClient} 协同。
 */
@ConfigurationProperties(prefix = "qdrant")
public record QdrantProperties(String host, int port, String collection, double scoreThreshold) {
    public QdrantProperties {
        host = host == null || host.isBlank() ? "localhost" : host;
        if (port <= 0) port = 6333;
        collection = collection == null || collection.isBlank() ? "kb_embeddings" : collection;
    }
}
