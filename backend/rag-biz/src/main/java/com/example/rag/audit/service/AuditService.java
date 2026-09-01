package com.example.rag.audit.service;

import com.example.rag.audit.vo.AuditLogListVO;

/**
 * 审计日志服务接口。负责查询和聚合审计日志数据，
 * 返回 {@link com.example.rag.audit.controller.AuditController} 消费的类型化列表。
 */
public interface AuditService {

    /**
     * 获取最近的审计日志列表。
     *
     * @return 审计日志列表响应
     */
    AuditLogListVO listRecentLogs();
}
