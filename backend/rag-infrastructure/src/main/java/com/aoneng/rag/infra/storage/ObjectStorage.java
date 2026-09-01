package com.aoneng.rag.infra.storage;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 对象存储抽象接口。
 * 统一封装文件的上传、下载、删除和预签名 URL 能力。
 */
public interface ObjectStorage {

    /**
     * 上传对象。
     *
     * @param objectName  对象名称
     * @param data        输入流
     * @param size        大小（字节）
     * @param contentType MIME 类型
     */
    void upload(String objectName, InputStream data, long size, String contentType);

    /**
     * 下载对象到内存流。
     *
     * @param objectName 对象名称
     * @return 输入流，调用方负责关闭
     */
    InputStream download(String objectName);

    /**
     * 下载对象到输出流。
     *
     * @param objectName 对象名称
     * @param output     输出流
     */
    void download(String objectName, OutputStream output);

    /**
     * 删除对象。
     *
     * @param objectName 对象名称
     */
    void delete(String objectName);

    /**
     * 生成预签名 URL。
     *
     * @param objectName    对象名称
     * @param expiryMinutes 有效期（分钟）
     * @return 预签名 URL
     */
    String getPresignedUrl(String objectName, int expiryMinutes);

    /**
     * 检查对象是否存在。
     *
     * @param objectName 对象名称
     * @return 是否存在
     */
    boolean exists(String objectName);
}
