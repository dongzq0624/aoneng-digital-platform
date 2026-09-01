package com.aoneng.rag.system.vo;

/**
 * 菜单响应体。
 */
public record MenuVO(
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
