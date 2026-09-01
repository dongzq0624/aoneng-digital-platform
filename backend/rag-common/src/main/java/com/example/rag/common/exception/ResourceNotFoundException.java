package com.example.rag.common.exception;

/**
 * 资源不存在异常，对应 HTTP 404。资源缺失可能是被删除、ID 拼写错误或所属租户不可见，
 * 统一抛出该异常，由 {@code GlobalExceptionHandler} 转换为 {@code 404}。
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * 构造资源不存在异常。
     *
     * @param message 面向用户的提示文本
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}