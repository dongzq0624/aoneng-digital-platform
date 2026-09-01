package com.example.rag.dashboard.service;

import com.example.rag.dashboard.dto.DashboardSummaryResponse;

/**
 * 仪表盘数据聚合服务接口。负责计算并返回仪表盘统计数据，
 * 返回 {@link com.example.rag.dashboard.controller.DashboardController} 消费的类型化摘要。
 */
public interface DashboardService {

    /**
     * 获取仪表盘摘要数据。
     *
     * @return 仪表盘摘要响应
     */
    DashboardSummaryResponse summary();
}
