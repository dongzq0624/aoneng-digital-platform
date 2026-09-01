package com.example.rag.common;

import com.example.rag.common.exception.ForbiddenException;
import com.example.rag.domain.KbScope;
import org.springframework.stereotype.Component;

/**
 * 知识库与系统权限校验工具类。
 * 统一封装 RAG 业务中常见的访问范围校验逻辑，避免在多个 Service 中重复实现。
 */
@Component
public class ScopeValidator {

    /**
     * 要求用户拥有知识库访问权限（普通用户角色可访问 KB）。
     * 管理员自动通过；普通用户必须拥有 {@code kb:view} 权限。
     *
     * @param scope 用户的知识库范围
     * @return 校验通过的 scope
     * @throws ForbiddenException 未获知识库访问授权时抛出
     */
    public KbScope requireKbAccess(KbScope scope) {
        if (scope == null) {
            throw new ForbiddenException("未识别当前用户，请重新登录");
        }
        if (!scope.admin() && !scope.kbAccess()) {
            throw new ForbiddenException("当前角色未获知识库访问授权");
        }
        return scope;
    }

    /**
     * 要求当前用户为系统管理员。
     *
     * @param scope 用户的知识库范围
     * @return 校验通过的 scope
     * @throws ForbiddenException 非管理员时抛出
     */
    public KbScope requireAdmin(KbScope scope) {
        if (scope == null) {
            throw new ForbiddenException("未识别当前用户，请重新登录");
        }
        if (!scope.admin()) {
            throw new ForbiddenException("仅系统管理员可以执行该操作");
        }
        return scope;
    }

    /**
     * 要求用户对指定知识库有管理权限（创建/更新/删除/上传文档）。
     * 委托给 {@code PlatformRepository.canManageBase} 进行判断。
     *
     * @param kbId  知识库 ID
     * @param scope 用户的知识库范围
     * @param canManage 仓储层返回的是否可管理
     * @throws ForbiddenException 无管理权限时抛出
     */
    public void requireManageBase(long kbId, KbScope scope, boolean canManage) {
        requireKbAccess(scope);
        if (!canManage) {
            throw new ForbiddenException("无权操作该知识库");
        }
    }
}
