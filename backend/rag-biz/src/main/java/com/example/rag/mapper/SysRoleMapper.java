package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.po.SysRolePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRolePO> {

    /**
     * Role rows with user count and menu count, aliased to match the legacy contract.
     */
    List<Map<String, Object>> selectRoleOverviews();

    int insertReturningId(SysRolePO role);

    int updateRole(@Param("id") long id,
                   @Param("code") String code,
                   @Param("name") String name,
                   @Param("remark") String remark,
                   @Param("status") Integer status);

    int hardDelete(@Param("id") long id);

    int countById(@Param("id") long id);

    int countByCode(@Param("code") String code);

    int countByCodeExcluding(@Param("code") String code, @Param("id") long id);

    List<Long> selectMenuIds(@Param("roleId") long roleId);

    int deleteRoleMenus(@Param("roleId") long roleId);

    int insertRoleMenu(@Param("roleId") long roleId, @Param("menuId") long menuId);

    int countRolesWithMenu(@Param("menuIds") List<Long> menuIds);

    int deleteUserRolesByRole(@Param("roleId") long roleId);

    int countUsersWithRole(@Param("roleId") long roleId);
}
