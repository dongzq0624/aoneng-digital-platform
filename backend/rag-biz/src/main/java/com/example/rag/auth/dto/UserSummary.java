package com.example.rag.auth.dto;

/**
 * 当前登录用户摘要信息。
 *
 * @param id       用户 ID
 * @param username 用户名（登录账号）
 * @param realName 真实姓名
 * @param deptId   所属部门 ID（可为 null）
 */
public record UserSummary(Long id, String username, String realName, Long deptId) {
}
