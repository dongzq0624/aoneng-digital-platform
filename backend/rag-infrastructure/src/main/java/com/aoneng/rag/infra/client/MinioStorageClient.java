package com.aoneng.rag.infra.client;

import com.aoneng.rag.infra.config.MinioProperties;
import com.aoneng.rag.infra.storage.ObjectStorage;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储客户端实现。
 */
@Component
public class MinioStorageClient implements ObjectStorage {
    private static final Logger log = LoggerFactory.getLogger(MinioStorageClient.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioStorageClient(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void upload(String objectName, InputStream data, long size, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectName)
                .stream(data, size, -1)
                .contentType(contentType)
                .build());
            log.debug("上传对象成功: bucket={} object={}", properties.bucket(), objectName);
        } catch (Exception e) {
            log.error("上传对象失败: bucket={} object={}", properties.bucket(), objectName, e);
            throw new RuntimeException("MinIO 上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectName)
                .build());
        } catch (Exception e) {
            log.error("下载对象失败: bucket={} object={}", properties.bucket(), objectName, e);
            throw new RuntimeException("MinIO 下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void download(String objectName, OutputStream output) {
        try (InputStream in = download(objectName)) {
            in.transferTo(output);
        } catch (Exception e) {
            log.error("下载对象到流失败: bucket={} object={}", properties.bucket(), objectName, e);
            throw new RuntimeException("MinIO 下载失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectName)
                .build());
            log.debug("删除对象成功: bucket={} object={}", properties.bucket(), objectName);
        } catch (Exception e) {
            log.error("删除对象失败: bucket={} object={}", properties.bucket(), objectName, e);
            throw new RuntimeException("MinIO 删除失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedUrl(String objectName, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .bucket(properties.bucket())
                .object(objectName)
                .expiry(expiryMinutes, TimeUnit.MINUTES)
                .method(Method.GET)
                .build());
        } catch (Exception e) {
            log.error("生成预签名 URL 失败: bucket={} object={}", properties.bucket(), objectName, e);
            throw new RuntimeException("MinIO 预签名 URL 生成失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String objectName) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                .bucket(properties.bucket())
                .object(objectName)
                .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
