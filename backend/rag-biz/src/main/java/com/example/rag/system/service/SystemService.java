package com.example.rag.system.service;

import com.example.rag.system.dto.CreateDepartmentDTO;
import com.example.rag.system.dto.CreateMenuDTO;
import com.example.rag.system.dto.CreateRoleDTO;
import com.example.rag.system.dto.CreateUserDTO;
import com.example.rag.system.dto.UpdateDepartmentDTO;
import com.example.rag.system.dto.UpdateMenuDTO;
import com.example.rag.system.dto.UpdateRoleMenusDTO;
import com.example.rag.system.dto.UpdateRoleDTO;
import com.example.rag.system.dto.UpdateUserDTO;
import com.example.rag.system.vo.DepartmentVO;
import com.example.rag.system.vo.MenuVO;
import com.example.rag.system.vo.RoleMenuVO;
import com.example.rag.system.vo.RoleVO;
import com.example.rag.system.vo.UserMenuVO;
import com.example.rag.system.vo.UserVO;

import java.util.List;

/**
 * 系统管理服务接口。负责用户、部门、角色、菜单的业务规则处理
 * （权限校验、VO 转换、数据映射）。控制器应依赖此接口而非实现类。
 */
public interface SystemService {

    // -------- 用户管理 --------

    List<UserVO> listUsers();

    UserVO getUser(long id);

    UserVO createUser(CreateUserDTO req);

    UserVO updateUser(long id, UpdateUserDTO req);

    void deleteUser(long id);

    void resetPassword(long id);

    // -------- 部门管理 --------

    List<DepartmentVO> listDepartments();

    DepartmentVO createDepartment(CreateDepartmentDTO req);

    DepartmentVO updateDepartment(long id, UpdateDepartmentDTO req);

    void deleteDepartment(long id);

    // -------- 角色管理 --------

    List<RoleVO> listRoles(String username);

    RoleVO createRole(String username, CreateRoleDTO req);

    RoleVO updateRole(String username, long id, UpdateRoleDTO req);

    void deleteRole(String username, long id);

    RoleMenuVO getRoleMenus(String username, long id);

    void updateRoleMenus(String username, long id, UpdateRoleMenusDTO req);

    // -------- 菜单管理 --------

    List<MenuVO> listMenus(String username);

    UserMenuVO listUserMenuIds(String username);

    MenuVO createMenu(String username, CreateMenuDTO req);

    MenuVO updateMenu(String username, long id, UpdateMenuDTO req);

    void deleteMenu(String username, long id);
}
