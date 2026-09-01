package com.aoneng.rag.infra.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 挂在 {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken}
 * 上的附加载荷，由 {@link JwtAuthenticationFilter} 填充。
 * 业务代码可通过 {@link #currentUserId()} 直接拿到数值型用户 ID，无需再次解析 JWT。
 */
public record JwtAuthentication(Long userId) {

    public JwtAuthentication {
        // 允许 userId 为 null；record 字段仍为 final。
    }

    /**
     * 从当前 SecurityContext 中提取数值型用户 ID。未走 JWT 认证或 token 未携带
     * {@code uid} 声明时返回 {@code null}。
     */
    public static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        if (authentication.getDetails() instanceof JwtAuthentication payload) {
            return payload.userId();
        }
        return null;
    }
}
