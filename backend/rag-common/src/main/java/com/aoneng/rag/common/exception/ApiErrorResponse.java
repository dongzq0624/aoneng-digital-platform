package com.aoneng.rag.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * 错误响应体。{@code GlobalExceptionHandler} 把控制器抛出的异常统一转换为该结构，
 * 返回给前端时序列化字段被截断为 {@code NON_NULL}。{@code details} 用于携带字段级校验错误，
 * 例如 {@code {"fileName": "文件名不能为空"}}。
 *
 * @param timestamp 异常发生时间
 * @param status    HTTP 状态码（与 {@code code} 共同定位错误类别）
 * @param code      业务状态码，见 {@link com.aoneng.rag.common.result.ResultCode}
 * @param message   面向用户的提示文本
 * @param path      出错的请求路径，便于前端日志关联
 * @param details   字段级错误详情，可空
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(Instant timestamp, int status, String code, String message,
                               String path, Map<String, String> details) {
}