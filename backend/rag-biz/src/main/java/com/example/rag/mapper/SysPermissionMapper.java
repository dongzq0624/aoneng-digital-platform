package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.po.SysPermissionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermissionPO> {

    /**
     * Menu rows (type=1) enriched with child count and role count.
     */
    List<Map<String, Object>> selectMenuOverviews();

    int insertReturningId(SysPermissionPO permission);

    int updateMenu(@Param("id") long id,
                   @Param("parentId") long parentId,
                   @Param("name") String name,
                   @Param("perms") String perms,
                   @Param("path") String path,
                   @Param("sort") Integer sort,
                   @Param("status") Integer status);

    int hardDelete(@Param("id") long id);

    int countMenuChildren(@Param("id") long id);

    int countMenuRoles(@Param("id") long id);

    int countMenuById(@Param("id") long id);

    int countByPerms(@Param("perms") String perms, @Param("excludeId") long excludeId);

    int isMenuDescendant(@Param("candidate") long candidate, @Param("ancestor") long ancestor);

    int insertRolePermission(@Param("roleId") long roleId, @Param("permissionId") long permissionId);
}
