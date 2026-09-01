package com.example.rag.common.exception;

/**
 * 业务校验失败异常，对应 HTTP 400。用于显式阻断业务流程但不暴露实现细节的场景，
 * 例如字段缺失、状态机不匹配、引用已删除资源等。消息直接面向最终用户，使用清晰中文。
 */
public class BusinessValidationException extends RuntimeException {

    /**
     * 构造业务校验异常。
     *
     * @param message 面向用户的提示文本
     */
    public BusinessValidationException(String message) {
        super(message);
    }
}