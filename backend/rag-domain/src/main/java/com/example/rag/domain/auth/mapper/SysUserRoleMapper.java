package com.example.rag.domain.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.domain.auth.po.SysUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<Object> {

    int insertIfAbsent(@Param("userId") long userId, @Param("roleId") long roleId);

    int deleteByUser(@Param("userId") long userId);
}
