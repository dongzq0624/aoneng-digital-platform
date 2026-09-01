package com.aoneng.rag.infra.repository;

import com.aoneng.rag.domain.auth.mapper.SysDeptMapper;
import com.aoneng.rag.domain.auth.mapper.SysPermissionMapper;
import com.aoneng.rag.domain.auth.mapper.SysRoleMapper;
import com.aoneng.rag.domain.auth.po.SysDeptPO;
import com.aoneng.rag.domain.auth.po.SysPermissionPO;
import com.aoneng.rag.domain.auth.po.SysRolePO;
import com.aoneng.rag.domain.auth.repository.OrgRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Repository
public class OrgRepositoryImpl implements OrgRepository {
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    public OrgRepositoryImpl(SysDeptMapper deptMapper, SysRoleMapper roleMapper,
                             SysPermissionMapper permissionMapper) {
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public List<Map<String, Object>> findDepartmentOverviews() {
        return deptMapper.selectDepartmentOverviews();
    }

    @Override
    public Map<String, Object> findDepartmentById(long id) {
        return deptMap(deptMapper.selectById(id));
    }

    @Override
    public int countActiveById(long id) {
        return deptMapper.countActiveById(id);
    }

    @Override
    public long createDepartment(long parentId, String name, String ancestors, int sort, int status) {
        SysDeptPO po = new SysDeptPO();
        po.setParentId(parentId);
        po.setName(name);
        po.setAncestors(ancestors);
        po.setSort(sort);
        po.setStatus(status);
        deptMapper.insertReturningId(po);
        return po.getId();
    }

    @Override
    public void updateDepartment(long id, String name, long parentId, String ancestors, Integer sort, Integer status) {
        deptMapper.updateDept(id, name, parentId, ancestors, sort, status);
    }

    @Override
    public void hardDeleteDepartment(long id) {
        deptMapper.hardDelete(id);
    }

    @Override
    public String findAncestors(long id) {
        return deptMapper.selectAncestors(id);
    }

    @Override
    public int countDescendants(long candidate, long ancestor) {
        return deptMapper.isDescendant(candidate, ancestor);
    }

    @Override
    public List<Map<String, Object>> findRoleOverviews() {
        return roleMapper.selectRoleOverviews();
    }

    @Override
    public Map<String, Object> findRoleById(long id) {
        return roleMap(roleMapper.selectById(id));
    }

    @Override
    public int countById(long id) {
        return roleMapper.countById(id);
    }

    @Override
    public int countByCodeExcluding(String code, long excludeId) {
        return roleMapper.countByCodeExcluding(code, excludeId);
    }

    @Override
    public int countByCode(String code) {
        return roleMapper.countByCode(code);
    }

    @Override
    public long createRole(String code, String name, String remark, int status) {
        SysRolePO po = new SysRolePO();
        po.setCode(code);
        po.setName(name);
        po.setRemark(remark);
        po.setStatus(status);
        roleMapper.insertReturningId(po);
        return po.getId();
    }

    @Override
    public void updateRole(long id, String code, String name, String remark, Integer status) {
        roleMapper.updateRole(id, code, name, remark, status);
    }

    @Override
    public void deleteRole(long id) {
        roleMapper.hardDelete(id);
    }

    @Override
    public void deleteRoleMenus(long roleId) {
        roleMapper.deleteRoleMenus(roleId);
    }

    @Override
    public void insertRoleMenu(long roleId, long menuId) {
        roleMapper.insertRoleMenu(roleId, menuId);
    }

    @Override
    public int countUsersWithRole(long roleId) {
        return roleMapper.countUsersWithRole(roleId);
    }

    @Override
    public int countRolesWithMenu(List<Long> menuIds) {
        return menuIds == null || menuIds.isEmpty() ? 0 : roleMapper.countRolesWithMenu(menuIds);
    }

    @Override
    public List<Map<String, Object>> findMenuOverviews() {
        return permissionMapper.selectMenuOverviews();
    }

    @Override
    public Map<String, Object> findMenuById(long id) {
        return menuMap(permissionMapper.selectById(id));
    }

    @Override
    public int countMenuChildren(long id) {
        return permissionMapper.countMenuChildren(id);
    }

    @Override
    public int countMenuRoles(long id) {
        return permissionMapper.countMenuRoles(id);
    }

    @Override
    public int countMenuById(long id) {
        return permissionMapper.countMenuById(id);
    }

    @Override
    public int countByPerms(String perms, long excludeId) {
        return permissionMapper.countByPerms(perms, excludeId);
    }

    @Override
    public int countMenuDescendants(long candidate, long ancestor) {
        return permissionMapper.isMenuDescendant(candidate, ancestor);
    }

    @Override
    public long createMenu(long parentId, String name, String perms, String path, int sort, int status) {
        SysPermissionPO po = new SysPermissionPO();
        po.setParentId(parentId);
        po.setName(name);
        po.setPerms(perms);
        po.setType(1);
        po.setPath(path);
        po.setSort(sort);
        po.setStatus(status);
        permissionMapper.insertReturningId(po);
        return po.getId();
    }

    @Override
    public void updateMenu(long id, long parentId, String name, String perms, String path, Integer sort, Integer status) {
        permissionMapper.updateMenu(id, parentId, name, perms, path, sort, status);
    }

    @Override
    public void hardDeleteMenu(long id) {
        permissionMapper.hardDelete(id);
    }

    @Override
    public List<Long> findMenuIdsByRoleId(long roleId) {
        return roleMapper.selectMenuIds(roleId);
    }

    @Override
    public List<Long> findUserMenuIds(long userId) {
        return permissionMapper.selectUserMenuIds(userId);
    }

    private Map<String, Object> deptMap(SysDeptPO p) {
        if (p == null) return null;
        Map<String,Object> m = new LinkedHashMap<>(); m.put("id",p.getId()); m.put("parentId",p.getParentId()); m.put("name",p.getName()); m.put("ancestors",p.getAncestors()); m.put("sort",p.getSort()); m.put("status",p.getStatus()); return m;
    }

    private Map<String, Object> roleMap(SysRolePO p) {
        if (p == null) return null;
        Map<String,Object> m = new LinkedHashMap<>(); m.put("id",p.getId()); m.put("code",p.getCode()); m.put("name",p.getName()); m.put("remark",p.getRemark()); m.put("status",p.getStatus()); m.put("userCount",0); m.put("menuCount",0); return m;
    }

    private Map<String, Object> menuMap(SysPermissionPO p) {
        if (p == null) return null;
        Map<String,Object> m = new LinkedHashMap<>(); m.put("id",p.getId()); m.put("parentId",p.getParentId()); m.put("name",p.getName()); m.put("perms",p.getPerms()); m.put("path",p.getPath()); m.put("sort",p.getSort()); m.put("status",p.getStatus()); m.put("childCount",0); m.put("roleCount",0); return m;
    }
}
