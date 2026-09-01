package com.example.rag.framework.client;

import com.example.rag.framework.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;

/**
 * MinIO 对象存储抽象。集中处理对象键命名、错误日志与基础 CRUD，
 * 让业务服务专注于上传与索引流水线，避免直接接触 SDK。
 */
@Component
public class MinioStorageClient {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageClient.class);

    private final MinioClient client;
    private final MinioProperties properties;

    public MinioStorageClient(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 当前 bucket 名称，便于业务层在拼装对象键时复用配置。
     */
    public String bucket() {
        return properties.bucket();
    }

    /**
     * 启动时调用：若配置的 bucket 不存在则创建。失败仅记录调试日志，
     * 避免 MinIO 临时不可用导致整个应用无法启动。
     */
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                log.info("Created MinIO bucket {}", properties.bucket());
            }
        } catch (Exception e) {
            log.debug("MinIO bucket bootstrap skipped: {}", e.getMessage());
        }
    }

    /**
     * 上传对象。{@code objectKey} 原样使用，调用方需负责键的清洗与路径生成。
     * 调用方应传入带缓冲的输入流，避免 SDK 内部再读一次造成双倍内存占用。
     */
    public void putObject(String objectKey, InputStream stream, long size, String contentType)
            throws MinioException, GeneralSecurityException, IOException {
        client.putObject(PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .stream(stream, size, -1)
                .contentType(contentType)
                .build());
    }

    /**
     * 打开对象输入流。对象不存在时返回 {@code null}，由调用方决定如何降级。
     */
    public InputStream getObject(String objectKey)
            throws MinioException, GeneralSecurityException, IOException {
        return client.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectKey)
                .build());
    }

    /**
     * 删除对象。对象不存在时静默忽略，不抛出。
     */
    public void removeObject(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            log.debug("MinIO remove object skipped: key={}, reason={}", objectKey, e.getMessage());
        }
    }

    /**
     * 查询对象元数据（大小、MIME）。对象不存在或查询失败时返回 {@code null}。
     */
    public ObjectMetadata statObject(String objectKey) {
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
            return new ObjectMetadata(response.size(), response.contentType());
        } catch (ErrorResponseException notFound) {
            return null;
        } catch (Exception e) {
            log.warn("MinIO stat failed for key={}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    /** 对象元数据轻量封装，包含大小与 MIME。 */
    public record ObjectMetadata(long size, String contentType) {
    }
}
