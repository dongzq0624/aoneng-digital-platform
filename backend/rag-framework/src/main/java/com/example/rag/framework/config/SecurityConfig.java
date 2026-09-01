package com.example.rag.framework.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.rag.framework.security.JwtAuthenticationFilter;

/**
 * 无状态的 JWT 安全配置。匿名端点（auth、actuator、error）放行，其余接口须通过
 * {@link JwtAuthenticationFilter} 校验 Bearer 令牌。
 */
@Configuration
public class SecurityConfig {

    /**
     * 密码编码器。业务侧应使用 {@link PasswordEncoder} 加密用户密码，禁止明文入库。
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 过滤器链：禁用 CSRF（前后端分离），启用无状态会话，
     * 把 {@link JwtAuthenticationFilter} 加到用户名密码过滤器之前，
     * 任何未认证请求返回 401。
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized, please sign in")))
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/api/auth/**", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
