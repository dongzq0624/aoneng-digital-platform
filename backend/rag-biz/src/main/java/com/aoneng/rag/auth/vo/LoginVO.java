package com.aoneng.rag.auth.vo;

/**
 * 用户登录响应体。
 *
 * @param token JWT 令牌，前端后续请求需放入 Authorization 头
 * @param user  当前登录用户摘要信息
 */
public record LoginVO(String token, UserSummaryVO user) {
}
