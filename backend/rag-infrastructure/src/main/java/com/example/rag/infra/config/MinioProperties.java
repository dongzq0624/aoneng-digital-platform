package com.example.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** S3 兼容对象存储配置，对应 docker-compose 中的 MinIO 服务。 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(String endpoint, String accessKey, String secretKey, String bucket) {
    public MinioProperties {
        endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:9000" : endpoint;
        accessKey = accessKey == null ? "" : accessKey;
        secretKey = secretKey == null ? "" : secretKey;
        bucket = bucket == null || bucket.isBlank() ? "rag-docs" : bucket;
    }
}
