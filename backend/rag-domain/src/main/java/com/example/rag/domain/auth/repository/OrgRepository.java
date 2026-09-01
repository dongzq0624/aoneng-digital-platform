package com.example.rag.domain.auth.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 组织架构仓储接口（auth bounded context）。
 * 定义部门、角色、菜单等组织数据的查询与变更操作。
 */
public interface OrgRepository {

    // -------- Department --------

    /** 查询所有部门（带用户数和子部门数）。 */
    List<Map<String, Object>> findDepartmentOverviews();

    /** 按 ID 查询部门。 */
    Map<String, Object> findDepartmentById(long id);

    /** 统计活跃部门数。 */
    int countActiveById(long id);

    /** 创建部门。 */
    long createDepartment(long parentId, String name, String ancestors, int sort, int status);

    /** 更新部门。 */
    void updateDepartment(long id, String name, long parentId, String ancestors, Integer sort, Integer status);

    /** 物理删除部门。 */
    void hardDeleteDepartment(long id);

    /** 查询部门祖先路径。 */
    String findAncestors(long id);

    /** 递归检查是否为下级。 */
    int countDescendants(long candidate, long ancestor);

    // -------- Role --------

    /** 查询所有角色（带用户数和菜单数）。 */
    List<Map<String, Object>> findRoleOverviews();

    /** 按 ID 查询角色。 */
    Map<String, Object> findRoleById(long id);

    /** 统计角色。 */
    int countById(long id);

    /** 统计角色编码重复数（排除指定 ID）。 */
    int countByCodeExcluding(String code, long excludeId);

    /** 统计编码重复数。 */
    int countByCode(String code);

    /** 创建角色。 */
    long createRole(String code, String name, String remark, int status);

    /** 更新角色。 */
    void updateRole(long id, String code, String name, String remark, Integer status);

    /** 删除角色。 */
    void deleteRole(long id);

    /** 删除角色菜单。 */
    void deleteRoleMenus(long roleId);

    /** 插入角色菜单。 */
    void insertRoleMenu(long roleId, long menuId);

    /** 统计角色关联用户数。 */
    int countUsersWithRole(long roleId);

    /** 统计角色关联菜单数。 */
    int countRolesWithMenu(List<Long> menuIds);

    // -------- Permission / Menu --------

    /** 查询所有菜单（带子菜单数和角色数）。 */
    List<Map<String, Object>> findMenuOverviews();

    /** 按 ID 查询菜单。 */
    Map<String, Object> findMenuById(long id);

    /** 统计菜单子级。 */
    int countMenuChildren(long id);

    /** 统计菜单关联角色数。 */
    int countMenuRoles(long id);

    /** 统计菜单是否存在。 */
    int countMenuById(long id);

    /** 统计权限标识重复（排除指定 ID）。 */
    int countByPerms(String perms, long excludeId);

    /** 递归检查菜单是否为下级。 */
    int countMenuDescendants(long candidate, long ancestor);

    /** 创建菜单。 */
    long createMenu(long parentId, String name, String perms, String path, int sort, int status);

    /** 更新菜单。 */
    void updateMenu(long id, long parentId, String name, String perms, String path, Integer sort, Integer status);

    /** 物理删除菜单。 */
    void hardDeleteMenu(long id);

    /** 查询角色菜单 ID 列表。 */
    List<Long> findMenuIdsByRoleId(long roleId);

    /** 查询用户菜单 ID 列表（通过角色）。 */
    List<Long> findUserMenuIds(long userId);
}
