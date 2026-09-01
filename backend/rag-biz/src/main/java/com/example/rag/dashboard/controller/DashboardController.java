package com.example.rag.dashboard.controller;

import com.example.rag.common.result.Result;
import com.example.rag.dashboard.dto.DashboardSummaryResponse;
import com.example.rag.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仪表盘控制器。负责 HTTP 协议层面的响应封装，
 * 仪表盘统计数据由 {@link DashboardService} 计算并返回。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 获取仪表盘摘要数据。
     *
     * @return 仪表盘摘要（知识库数量、文档数量、今日问答数、命中率等）
     */
    @GetMapping("/summary")
    public Result<DashboardSummaryResponse> summary() {
        return Result.ok(dashboardService.summary());
    }
}
