package com.example.rag.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。密钥长度必须 ≥ 32 字符（由 {@link com.example.rag.framework.security.JwtUtil} 校验），
 * 默认过期时间 24 小时。
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, long expirationMs) {

    public JwtProperties {
        secret = secret == null ? "" : secret;
        if (expirationMs <= 0) expirationMs = 86_400_000L;
    }
}