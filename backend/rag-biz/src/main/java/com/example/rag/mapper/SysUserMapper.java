package com.example.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.rag.po.SysUserPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserPO> {

    /**
     * List active users joined with department and a single role label.
     * Returned columns use camelCase aliases that match the legacy repository contract.
     */
    List<Map<String, Object>> selectActiveUsers();

    Map<String, Object> selectUserByUsername(@Param("username") String username);

    String selectPasswordHash(@Param("username") String username);

    int insertReturningId(SysUserPO user);

    int updateProfile(@Param("id") long id,
                      @Param("realName") String realName,
                      @Param("email") String email,
                      @Param("phone") String phone,
                      @Param("deptId") Long deptId,
                      @Param("status") Integer status);

    int softDelete(@Param("id") long id);

    int resetPassword(@Param("id") long id, @Param("hash") String hash);

    int advanceSequence(@Param("seqName") String seqName);

    /**
     * KbScope projection. Returns a single map with keys: id, dept_id, admin, kb_access, kb_manager.
     */
    Map<String, Object> selectKbScope(@Param("username") String username);

    OffsetDateTime selectLastLoginAt(@Param("id") long id);

    int updateLastLoginAt(@Param("id") long id, @Param("time") OffsetDateTime time);
}
