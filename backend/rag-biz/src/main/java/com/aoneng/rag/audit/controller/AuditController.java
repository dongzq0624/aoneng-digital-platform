package com.aoneng.rag.audit.controller;

import com.aoneng.rag.audit.service.AuditService;
import com.aoneng.rag.audit.vo.AuditLogListVO;
import com.aoneng.rag.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计日志控制器。负责 HTTP 协议层面的响应封装，
 * 所有日志查询和列表组装业务逻辑委托给 {@link AuditService}。
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 获取最近的审计日志列表。
     *
     * @return 最近的审计日志
     */
    @GetMapping("/logs")
    public Result<AuditLogListVO> logs() {
        return Result.ok(auditService.listRecentLogs());
    }
}
