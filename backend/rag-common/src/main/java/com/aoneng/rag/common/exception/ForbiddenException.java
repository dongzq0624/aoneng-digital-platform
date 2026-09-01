package com.aoneng.rag.common.exception;

/**
 * 越权访问异常，对应 HTTP 403。用于已认证用户尝试访问其角色 / 部门 / 知识库范围之外的资源。
 * 与 {@link UnauthorizedException}（未认证）的区别在于本异常发生在身份已确定后。
 */
public class ForbiddenException extends RuntimeException {

    /**
     * 构造越权访问异常。
     *
     * @param message 面向用户的提示文本
     */
    public ForbiddenException(String message) {
        super(message);
    }
}