package com.aoneng.rag.doc.vo;

/**
 * 删除操作响应体。
 *
 * @param id      被删除资源的 ID
 * @param deleted 是否删除成功（true=成功）
 */
public record DeleteVO(long id, boolean deleted) {
}
