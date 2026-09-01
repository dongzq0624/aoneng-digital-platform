package com.example.rag.infra.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端 Bean。把 SDK 实例与业务解耦，业务代码只依赖 {@link com.example.rag.infra.client.MinioStorageClient} 包装层。
 */
@Configuration
public class MinioConfig {

    /**
     * 构建 MinIO 客户端。Endpoint、AccessKey、SecretKey 来自 {@link MinioProperties}。
     */
    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
    }
}
