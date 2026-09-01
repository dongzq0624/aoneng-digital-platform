package com.aoneng.rag.audit.vo;

import java.util.List;

/**
 * 审计日志列表响应体。
 *
 * @param items 当前页日志条目
 * @param total 满足条件的总条数
 */
public record AuditLogListVO(List<AuditLogVO> items, int total) {
}
