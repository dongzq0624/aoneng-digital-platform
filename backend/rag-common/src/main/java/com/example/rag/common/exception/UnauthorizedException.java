package com.example.rag.common.exception;

/**
 * 未认证异常，对应 HTTP 401。发生于令牌缺失、令牌过期或令牌解析失败时。
 * 框架侧 {@code JwtAuthenticationFilter} 会直接写入 401；
 * 业务侧仅在用户上下文缺失时抛出该异常，由 {@code GlobalExceptionHandler} 兜底处理。
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * 构造未认证异常。
     *
     * @param message 面向用户的提示文本
     */
    public UnauthorizedException(String message) {
        super(message);
    }
}