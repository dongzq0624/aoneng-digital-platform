package com.example.rag.domain.auth.repository;

import com.example.rag.domain.auth.po.SysUserPO;

import java.util.List;
import java.util.Optional;

/**
 * 用户仓储接口（auth bounded context）。
 * 定义用户数据的查询与变更操作，供应用服务层调用。
 */
public interface UserRepository {

    /**
     * 查询所有活跃用户。
     */
    List<SysUserPO> findActiveUsers();

    /**
     * 按用户名查询。
     */
    Optional<SysUserPO> findByUsername(String username);

    /**
     * 按 ID 查询。
     */
    Optional<SysUserPO> findById(long id);

    /**
     * 获取密码哈希（仅登录时使用）。
     */
    Optional<String> findPasswordHash(String username);

    /**
     * 创建用户。
     */
    SysUserPO create(SysUserPO user);

    /**
     * 更新用户资料。
     */
    void updateProfile(long id, String realName, String email, String phone, Long deptId, Integer status);

    /**
     * 软删除用户。
     */
    void softDelete(long id);

    /**
     * 重置密码。
     */
    void resetPassword(long id, String hash);
}
