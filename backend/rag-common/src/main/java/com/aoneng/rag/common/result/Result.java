package com.aoneng.rag.common.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * 统一 API 响应包装。控制器通过 {@link Result#ok(Object)} / {@link Result#error(String, String)}
 * 构造响应；{@code GlobalExceptionHandler} 把异常也转换成同结构响应。前端据此判断业务成功与否，
 * 不要直接读取 HTTP 状态码来区分业务错误。
 *
 * @param code      业务状态码（如 {@code OK}、{@code BAD_REQUEST}），见 {@link ResultCode}
 * @param message   面向用户的提示文本，使用清晰中文
 * @param data      业务负载，可能为 {@code null}
 * @param timestamp 服务端响应时间戳，便于日志与前端调试
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Result<T>(String code, String message, T data, Instant timestamp) {

    /**
     * 构造业务成功响应。
     *
     * @param data 业务负载
     * @param <T>  负载类型
     * @return 包装后的成功响应
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>("OK", "success", data, Instant.now());
    }

    /**
     * 构造无负载的成功响应。
     *
     * @param <T> 泛型占位
     * @return 不含数据的成功响应
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * 构造业务失败响应。
     *
     * @param code    业务状态码，必须在 {@link ResultCode} 内
     * @param message 面向用户的提示文本
     * @param <T>     泛型占位
     * @return 包装后的失败响应
     */
    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null, Instant.now());
    }
}