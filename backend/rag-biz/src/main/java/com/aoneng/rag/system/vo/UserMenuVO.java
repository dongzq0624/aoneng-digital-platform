package com.aoneng.rag.system.vo;

import java.util.List;

/**
 * 当前登录用户可见菜单 ID 列表响应体。
 *
 * @param menuIds 当前用户通过角色获得的菜单 ID 列表
 */
public record UserMenuVO(List<Long> menuIds) {
}
