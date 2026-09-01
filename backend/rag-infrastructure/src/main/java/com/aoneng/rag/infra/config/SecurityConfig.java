package com.aoneng.rag.infra.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.aoneng.rag.infra.security.JwtAuthenticationFilter;

/**
 * 无状态的 JWT 安全配置。匿名端点（auth、actuator、error）放行，其余接口须通过
 * {@link JwtAuthenticationFilter} 校验 Bearer 令牌。
 *
 * <p>{@link EnableMethodSecurity} 开启后，业务方法可使用 {@code @PreAuthorize} 注解进行细粒度权限控制。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
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
                        .requestMatchers("/api/v1/auth/**", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
