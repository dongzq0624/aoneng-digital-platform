package com.example.rag.system.vo;

/**
 * 简单操作结果响应体。复用于重置密码、删除用户/部门/角色/菜单、替换角色菜单等
 * 只需要返回受影响资源 ID 与成功标志位的接口。
 *
 * @param id      被操作的资源 ID
 * @param action  操作名称，例如 deleted / reset / updated
 */
public record OperationResultVO(long id, String action) {
}
