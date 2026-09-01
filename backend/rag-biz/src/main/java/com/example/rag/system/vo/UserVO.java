package com.example.rag.system.vo;

import java.time.Instant;

/**
 * 用户响应体。
 */
public record UserVO(
        long id,
        String employeeNo,
        String username,
        String realName,
        Long deptId,
        String dept,
        String role,
        Integer status,
        Instant createdAt) {
}
