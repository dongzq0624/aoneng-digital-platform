package com.aoneng.rag.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求体。
 *
 * @param username 用户名（必填）
 * @param password 密码（必填）
 */
public record LoginDTO(
        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password) {
}
