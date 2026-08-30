package com.example.rag.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) try {
            Claims c = jwtUtil.parse(h.substring(7));
            var a = new UsernamePasswordAuthenticationToken(c.getSubject(), null, AuthorityUtils.NO_AUTHORITIES);
            a.setDetails(c.get("uid", Long.class));
            SecurityContextHolder.getContext().setAuthentication(a);
        } catch (Exception ignored) {
        }
        chain.doFilter(req, res);
    }
}
