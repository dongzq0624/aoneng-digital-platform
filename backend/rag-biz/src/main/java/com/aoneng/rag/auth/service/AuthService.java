package com.aoneng.rag.auth.service;

import com.aoneng.rag.auth.dto.LoginDTO;
import com.aoneng.rag.auth.vo.LoginVO;
import com.aoneng.rag.auth.vo.UserSummaryVO;

/**
 * 认证服务接口。负责用户登录验证、JWT 签发和当前用户信息解析。
 */
public interface AuthService {

    /**
     * 验证登录凭证，生成 JWT 并返回用户摘要信息。
     *
     * @param request 登录请求（用户名 + 密码）
     * @return 登录响应（JWT 令牌 + 用户摘要）
     */
    LoginVO login(LoginDTO request);

    /**
     * 根据用户名（JWT subject）解析当前用户信息。
     *
     * @param username 从 JWT 解析得到的用户名
     * @return 当前用户摘要信息
     */
    UserSummaryVO currentUser(String username);
}
