package com.aoneng.rag.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import com.aoneng.rag.infra.config.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析工具。基于 JJWT 实现 HS256，密钥长度低于 32 字符会启动失败，
 * 以避免弱密钥带来的安全风险。过期时间由 {@link JwtProperties#expirationMs()} 决定。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    /**
     * 构造 JWT 工具类。会强制要求密钥 ≥ 32 字符，否则抛 {@link IllegalStateException}。
     */
    public JwtUtil(JwtProperties properties) {
        String secret = properties.secret();
        if (secret.length() < 32) {
            throw new IllegalStateException("security.jwt.secret must contain at least 32 characters");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.expirationMs();
    }

    /**
     * 签发 JWT。Subject 为用户名，附加 {@code uid} 声明保存数值型用户 ID。
     *
     * @param userId   业务用户 ID
     * @param username 用户名（登录账号）
     * @return 签名后的 JWT 字符串
     */
    public String issue(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验证 JWT 签名。失败时由 JJWT 抛出 {@link io.jsonwebtoken.JwtException}。
     *
     * @param token 不含 {@code Bearer } 前缀的 token
     * @return 解析后的 claims
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
