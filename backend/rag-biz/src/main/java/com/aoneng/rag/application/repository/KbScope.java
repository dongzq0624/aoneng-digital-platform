package com.aoneng.rag.application.repository;

import com.aoneng.rag.common.exception.ForbiddenException;

/**
 * 知识库范围记录。
 * 标识当前用户在知识库系统中的访问权限。
 *
 * @param userId     用户 ID
 * @param deptId    用户所属部门 ID（可为 null）
 * @param admin     是否为系统管理员
 * @param kbAccess  是否拥有知识库访问权限（普通用户）
 * @param kbManager 是否为知识库管理员
 */
public record KbScope(long userId, Long deptId, boolean admin, boolean kbAccess, boolean kbManager) {

    public KbScope requireKbAccess() {
        if (!admin && !kbAccess) {
            throw new ForbiddenException("当前角色未获知识库访问授权");
        }
        return this;
    }

    public KbScope requireAdmin() {
        if (!admin) {
            throw new ForbiddenException("仅系统管理员可以执行该操作");
        }
        return this;
    }
}
