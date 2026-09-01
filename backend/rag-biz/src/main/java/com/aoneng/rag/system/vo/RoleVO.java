package com.aoneng.rag.system.vo;

/**
 * 角色响应体。
 */
public record RoleVO(
        long id,
        String code,
        String name,
        String remark,
        Integer status,
        Integer userCount,
        Integer menuCount) {
}
