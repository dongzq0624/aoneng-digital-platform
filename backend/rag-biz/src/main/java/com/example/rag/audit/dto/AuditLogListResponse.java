package com.example.rag.audit.dto;

import java.util.List;

/**
 * 审计日志列表响应体。
 *
 * @param items 当前页日志条目
 * @param total 满足条件的总条数
 */
public record AuditLogListResponse(List<AuditLogResponse> items, int total) {
}
