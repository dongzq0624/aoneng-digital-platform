package com.example.rag.audit.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 单条审计日志响应体。
 *
 * @param id        日志 ID
 * @param createdAt 操作发生时间
 * @param username  操作人用户名
 * @param action    操作行为（如登录、创建知识库、上传文档等）
 * @param module    操作模块（如 auth、kb、rag、system 等）
 * @param detail    附加详情键值对（操作相关上下文）
 * @param result    操作结果（1=成功，0=失败）
 */
public record AuditLogResponse(
        long id,
        Instant createdAt,
        String username,
        String action,
        String module,
        Map<String, Object> detail,
        int result) {
}
