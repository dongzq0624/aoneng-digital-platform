package com.example.rag.auth.dto;

/**
 * 用户登录响应体。
 *
 * @param token JWT 令牌，前端后续请求需放入 Authorization 头
 * @param user  当前登录用户摘要信息
 */
public record LoginResponse(String token, UserSummary user) {
}
