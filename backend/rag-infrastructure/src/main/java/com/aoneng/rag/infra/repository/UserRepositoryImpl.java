package com.aoneng.rag.infra.repository;

import com.aoneng.rag.domain.auth.mapper.SysUserMapper;
import com.aoneng.rag.domain.auth.po.SysUserPO;
import com.aoneng.rag.domain.auth.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** MyBatis-backed implementation of the authentication user repository. */
@Repository
public class UserRepositoryImpl implements UserRepository {

    private final SysUserMapper mapper;

    public UserRepositoryImpl(SysUserMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SysUserPO> findActiveUsers() {
        return mapper.selectActiveUsers().stream().map(this::toUser).toList();
    }

    @Override
    public Optional<SysUserPO> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectUserByUsername(username)).map(this::toUser);
    }

    @Override
    public Optional<SysUserPO> findById(long id) {
        return Optional.ofNullable(mapper.selectUserById(id)).map(this::toUser);
    }

    @Override
    public Optional<String> findPasswordHash(String username) {
        return Optional.ofNullable(mapper.selectPasswordHash(username));
    }

    @Override
    public SysUserPO create(SysUserPO user) {
        mapper.insertReturningId(user);
        return user;
    }

    @Override
    public void updateProfile(long id, String realName, String email, String phone, Long deptId, Integer status) {
        mapper.updateProfile(id, realName, email, phone, deptId, status);
    }

    @Override
    public void softDelete(long id) {
        mapper.softDelete(id);
    }

    @Override
    public void resetPassword(long id, String hash) {
        mapper.resetPassword(id, hash);
    }

    private SysUserPO toUser(Map<String, Object> row) {
        SysUserPO user = new SysUserPO();
        user.setId(number(row.get("id")));
        user.setEmployeeNo(string(row.get("employeeNo")));
        user.setUsername(string(row.get("username")));
        user.setPasswordHash(string(row.get("passwordHash")));
        user.setRealName(string(row.get("realName")));
        user.setEmail(string(row.get("email")));
        user.setPhone(string(row.get("phone")));
        user.setDeptId(number(row.get("deptId")));
        user.setAvatar(string(row.get("avatar")));
        user.setStatus(integer(row.get("status")));
        return user;
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
