package com.example.rag.dashboard.vo;

import java.time.Instant;
import java.util.List;

/**
 * 仪表盘摘要响应体。
 *
 * @param knowledgeBaseCount 知识库总数量
 * @param documentCount      文档总数量
 * @param todayQaCount       今日问答记录数
 * @param hitRate            命中率（百分比，整数）
 * @param pendingCount       待处理任务数量
 * @param recentKbs          最近更新的知识库列表
 * @param todos              待办事项列表
 */
public record DashboardSummaryVO(
        int knowledgeBaseCount,
        int documentCount,
        int todayQaCount,
        int hitRate,
        int pendingCount,
        List<RecentKnowledgeBaseVO> recentKbs,
        List<TodoItemVO> todos) {

    /**
     * 最近更新的知识库摘要。
     */
    public record RecentKnowledgeBaseVO(
            long id,
            String name,
            String category,
            String visibility,
            Instant updatedAt,
            int docs) {
    }

    /**
     * 待办事项条目。
     */
    public record TodoItemVO(String title, String type, String time, String color) {
    }
}
