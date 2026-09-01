package com.example.rag.infra.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Bearer 令牌过滤器。校验 Authorization 头中的 JWT，把用户身份写入 Spring Security 上下文。
 * 校验失败仅记录 DEBUG 日志并不阻断过滤器链，由下游授权规则按需拒绝匿名请求。
 *
 * <p>绑定的 principal 直接是 subject（用户名），保留现有控制器形参
 * <code>@AuthenticationPrincipal String username</code> 的写法。
 * 数值型用户 ID 通过 {@link JwtAuthentication} 详情对象传递，
 * 业务代码可通过 {@link JwtAuthentication#currentUserId()} 获取，避免重复解析 JWT。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final SecurityContextHolderStrategy STRATEGY = SecurityContextHolder.getContextHolderStrategy();

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * 过滤器主流程：解析 Authorization 头 → 校验 JWT → 写入 SecurityContext → 放行请求。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                String subject = claims.getSubject();
                if (subject == null || subject.isBlank()) {
                    throw new JwtException("JWT subject is missing");
                }
                Long userId = extractUserId(claims);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(subject, null, AuthorityUtils.NO_AUTHORITIES);
                authentication.setDetails(new JwtAuthentication(userId));
                STRATEGY.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException exception) {
                STRATEGY.clearContext();
                log.debug("JWT 校验失败，path={}", req.getRequestURI());
            }
        }
        chain.doFilter(req, res);
    }

    /**
     * 解析 JWT 中的 {@code uid} 声明为 Long。缺失或非法时返回 {@code null}。
     */
    private static Long extractUserId(Claims claims) {
        Object value = claims.get("uid");
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
