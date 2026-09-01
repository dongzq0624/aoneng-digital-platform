package com.aoneng.rag.domain.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aoneng.rag.domain.auth.po.SysUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserPO> {

    /**
     * 查询所有活跃用户（已联表部门名称和主角色名称）。
     * 返回字段使用 camelCase 别名，与遗留 repository 契约保持一致。
     */
    List<Map<String, Object>> selectActiveUsers();

    /**
     * 按用户名查询用户（联表部门）。
     */
    Map<String, Object> selectUserByUsername(@Param("username") String username);

    /**
     * 按 ID 查询用户（联表部门名称和主角色名称）。
     */
    Map<String, Object> selectUserById(@Param("id") long id);

    /**
     * 查询用户密码哈希（仅在登录时使用）。
     */
    String selectPasswordHash(@Param("username") String username);

    /**
     * 插入用户并返回自动生成的主键 ID。
     */
    int insertReturningId(SysUserPO user);

    /**
     * 更新用户资料（姓名、邮箱、电话、部门、状态）。
     */
    int updateProfile(@Param("id") long id,
                      @Param("realName") String realName,
                      @Param("email") String email,
                      @Param("phone") String phone,
                      @Param("deptId") Long deptId,
                      @Param("status") Integer status);

    /**
     * 软删除用户。
     */
    int softDelete(@Param("id") long id);

    /**
     * 重置用户密码。
     */
    int resetPassword(@Param("id") long id, @Param("hash") String hash);

    /**
     * 推进序列（数据库序列管理）。
     */
    int advanceSequence(@Param("seqName") String seqName);

    /**
     * 查询用户的知识库权限范围（KbScope 投影）。
     * 返回字段：id, dept_id, admin, kb_access, kb_manager。
     */
    Map<String, Object> selectKbScope(@Param("username") String username);

    /**
     * 查询用户最近登录时间。
     */
    OffsetDateTime selectLastLoginAt(@Param("id") long id);

    /**
     * 更新用户最近登录时间。
     */
    int updateLastLoginAt(@Param("id") long id, @Param("time") OffsetDateTime time);
}
