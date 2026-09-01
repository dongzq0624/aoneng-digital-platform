package com.example.rag.auth.service.impl;

import com.example.rag.auth.dto.LoginRequest;
import com.example.rag.auth.dto.LoginResponse;
import com.example.rag.auth.dto.UserSummary;
import com.example.rag.auth.service.AuthService;
import com.example.rag.common.exception.ForbiddenException;
import com.example.rag.common.exception.UnauthorizedException;
import com.example.rag.framework.security.JwtUtil;
import com.example.rag.service.OrgRecords;
import com.example.rag.service.PlatformRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务默认实现。
 * 通过 {@link PlatformRepository} 进行凭证查询，通过 {@link JwtUtil} 签发令牌。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final JwtUtil jwtUtil;
    private final PlatformRepository repository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthServiceImpl(JwtUtil jwtUtil, PlatformRepository repository) {
        this.jwtUtil = jwtUtil;
        this.repository = repository;
    }

    /**
     * 验证登录凭证并签发 JWT。
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录响应（JWT 令牌 + 用户摘要）
     * @throws UnauthorizedException 用户名不存在或密码错误时抛出
     * @throws ForbiddenException  用户已停用时抛出
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        OrgRecords.UserRow user = repository.userByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        String hash = repository.passwordHash(username).orElse("");
        if (hash.isBlank() || !passwordEncoder.matches(request.password(), hash)) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (user.status() != null && user.status() == 0) {
            throw new ForbiddenException("用户已停用");
        }
        return new LoginResponse(jwtUtil.issue(user.id(), username), summary(user));
    }

    /**
     * 根据用户名获取当前用户信息。
     *
     * @param username 从 JWT 解析得到的用户名
     * @return 当前用户摘要信息
     * @throws UnauthorizedException 登录已失效时抛出
     */
    @Override
    public UserSummary currentUser(String username) {
        OrgRecords.UserRow user = repository.userByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("登录已失效"));
        return summary(user);
    }

    /**
     * 将用户行转换为用户摘要 DTO。
     */
    private UserSummary summary(OrgRecords.UserRow user) {
        return new UserSummary(user.id(), user.username(), user.realName(), user.deptId());
    }
}
