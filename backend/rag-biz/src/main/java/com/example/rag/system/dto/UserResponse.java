package com.example.rag.system.dto;

import java.time.Instant;

/**
 * 用户响应体。
 *
 * @param id         用户 ID
 * @param employeeNo 工号
 * @param username   用户名（登录账号）
 * @param realName   真实姓名
 * @param deptId     所属部门 ID
 * @param dept       部门名称（冗余字段便于列表展示）
 * @param role       角色名称（冗余字段便于列表展示）
 * @param status     启用状态（1=启用，0=停用）
 * @param createdAt  创建时间
 */
public record UserResponse(
        long id,
        String employeeNo,
        String username,
        String realName,
        Long deptId,
        String dept,
        String role,
        Integer status,
        Instant createdAt) {
}
