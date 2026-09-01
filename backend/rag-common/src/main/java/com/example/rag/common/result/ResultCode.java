package com.example.rag.common.result;

/**
 * 业务状态码常量。前端基于该值判断业务结果而不是 HTTP 状态码；
 * 新增错误类型时应同时在 {@link com.example.rag.common.exception}
 * 下新增对应的异常类并在此登记，避免散落字符串字面量。
 */
public final class ResultCode {

    /** 请求成功。 */
    public static final String OK = "OK";
    /** 请求参数不合法。 */
    public static final String BAD_REQUEST = "BAD_REQUEST";
    /** 未认证或令牌失效。 */
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    /** 已认证但当前角色无权访问资源。 */
    public static final String FORBIDDEN = "FORBIDDEN";
    /** 资源不存在或已被删除。 */
    public static final String NOT_FOUND = "NOT_FOUND";
    /** 业务冲突，如唯一键冲突、并发占用。 */
    public static final String CONFLICT = "CONFLICT";
    /** 服务器内部错误，对应 5xx。 */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ResultCode() {
    }
}