package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.PostConstruct;
import java.util.regex.Pattern;

/**
 * JWT 配置属性类。
 *
 * <ul>
 *   <li>密钥长度必须 ≥ 32 字符，启动时校验并拒绝默认值</li>
 *   <li>默认过期时间 24 小时</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "security.jwt")
@Validated
public record JwtProperties(String secret, long expirationMs) {

    private static final int MIN_SECRET_LENGTH = 32;
    private static final Pattern DEFAULT_SECRET_PATTERN =
            Pattern.compile("(?i)(change|dev|test|default|sample|example).*");

    public JwtProperties {
        if (expirationMs <= 0) expirationMs = 86_400_000L;
    }

    /**
     * 启动时校验密钥强度。开发环境默认值会被拒绝，确保生产环境配置正确。
     *
     * @throws IllegalStateException 密钥不符合强度要求
     */
    @PostConstruct
    public void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 密钥未配置：security.jwt.secret 不能为空");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "JWT 密钥长度不足：security.jwt.secret 长度必须 ≥ " + MIN_SECRET_LENGTH + " 字符");
        }
        if (DEFAULT_SECRET_PATTERN.matcher(secret).matches()) {
            throw new IllegalStateException(
                    "JWT 密钥使用了开发环境默认值，请配置安全的随机密钥");
        }
    }
}
