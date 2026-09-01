package com.example.rag.dashboard.service.impl;

import com.example.rag.dashboard.dto.DashboardSummaryResponse;
import com.example.rag.dashboard.service.DashboardService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 仪表盘服务默认实现。
 * 通过 PostgreSQL 执行 SQL 聚合查询，组装控制器消费的摘要 DTO。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 获取仪表盘摘要数据。
     * 统计知识库数量、文档数量、今日问答数、命中率等指标，
     * 并返回最近更新的知识库和待办事项。
     *
     * @return 仪表盘摘要响应
     */
    @Override
    public DashboardSummaryResponse summary() {
        // 统计知识库总数
        int kb = jdbc.queryForObject(
                "select count(*) from kb_knowledge_base where deleted=false", Integer.class);
        // 统计文档总数
        int docs = jdbc.queryForObject(
                "select count(*) from kb_document where deleted=false", Integer.class);
        // 统计今日问答数
        Integer qa = jdbc.queryForObject(
                "select count(*) from kb_qa_record where created_at::date=current_date", Integer.class);

        // 查询最近更新的 3 个知识库
        List<DashboardSummaryResponse.RecentKnowledgeBase> recent = jdbc.query(
                "select id, name, category, visibility, updated_at, " +
                        "(select count(*) from kb_document d where d.kb_id = k.id and d.deleted = false) as docs " +
                        "from kb_knowledge_base k where deleted = false order by updated_at desc limit 3",
                (rs, rowNum) -> new DashboardSummaryResponse.RecentKnowledgeBase(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("visibility"),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getInt("docs")));

        // 待办事项（示例数据）
        List<DashboardSummaryResponse.TodoItem> todos = List.of(
                new DashboardSummaryResponse.TodoItem("2026 年度目标确认", "人事", "今天 18:00 前", "orange"),
                new DashboardSummaryResponse.TodoItem("华东区域差旅报销单", "财务", "明天 12:00 前", "blue"),
                new DashboardSummaryResponse.TodoItem("办公用品领用审批", "行政", "3 月 20 日", "purple"));

        return new DashboardSummaryResponse(kb, docs, qa == null ? 0 : qa, 92, todos.size(), recent, todos);
    }

    /**
     * 将 SQL 时间戳转换为 Instant。
     */
    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
