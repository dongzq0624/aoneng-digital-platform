package com.example.rag.service;

/**
 * Repository return types for org management (users, depts, roles, menus).
 * Records keep the controller signatures strongly typed and avoid leaking
 * raw {@code Map<String, Object>} across the HTTP boundary.
 */
public final class OrgRecords {
    private OrgRecords() {
    }

    public record UserRow(
            long id,
            String employeeNo,
            String username,
            String realName,
            Long deptId,
            String dept,
            String role,
            Integer status) {
    }

    public record DepartmentRow(
            long id,
            long parentId,
            String name,
            String ancestors,
            Integer sort,
            Integer status,
            Integer userCount,
            Integer childCount) {
    }

    public record RoleRow(
            long id,
            String code,
            String name,
            String remark,
            Integer status,
            Integer userCount,
            Integer menuCount) {
    }

    public record MenuRow(
            long id,
            long parentId,
            String name,
            String perms,
            String path,
            Integer sort,
            Integer status,
            Integer childCount,
            Integer roleCount) {
    }
}
