package com.example.rag.auth.controller;

import com.example.rag.auth.dto.LoginRequest;
import com.example.rag.auth.dto.LoginResponse;
import com.example.rag.auth.dto.UserSummary;
import com.example.rag.auth.service.AuthService;
import com.example.rag.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器。负责 HTTP 协议层面的请求校验和响应封装，
 * 所有登录、用户信息等业务逻辑委托给 {@link AuthService}。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录接口。
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录成功返回 JWT 令牌和用户摘要信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    /**
     * 获取当前登录用户信息。
     *
     * @param username 从 JWT 解析得到的用户名
     * @return 当前用户详细信息
     */
    @GetMapping("/me")
    public Result<UserSummary> me(@AuthenticationPrincipal String username) {
        return Result.ok(authService.currentUser(username));
    }
}
