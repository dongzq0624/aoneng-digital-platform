package com.example.rag.dashboard.dto;

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
public record DashboardSummaryResponse(
        int knowledgeBaseCount,
        int documentCount,
        int todayQaCount,
        int hitRate,
        int pendingCount,
        List<RecentKnowledgeBase> recentKbs,
        List<TodoItem> todos) {

    /**
     * 最近更新的知识库摘要。
     *
     * @param id         知识库 ID
     * @param name       知识库名称
     * @param category   知识库分类
     * @param visibility 可见性（PUBLIC=公开，DEPARTMENT=部门内，PRIVATE=私有）
     * @param updatedAt  最近更新时间
     * @param docs       该知识库下的文档数量
     */
    public record RecentKnowledgeBase(
            long id,
            String name,
            String category,
            String visibility,
            Instant updatedAt,
            int docs) {
    }

    /**
     * 待办事项条目。
     *
     * @param title  事项标题
     * @param type   事项类型/所属模块
     * @param time   截止时间描述
     * @param color  前端标签颜色
     */
    public record TodoItem(String title, String type, String time, String color) {
    }
}
