package com.example.rag.dashboard.service.impl;

import com.example.rag.dashboard.service.DashboardService;
import com.example.rag.dashboard.vo.DashboardSummaryVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 仪表盘服务默认实现。
 * 通过 PostgreSQL 执行 SQL 聚合查询，组装控制器消费的摘要 VO。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private final JdbcTemplate jdbc;

    public DashboardServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public DashboardSummaryVO summary() {
        int kb = jdbc.queryForObject(
                "select count(*) from kb_knowledge_base where deleted=false", Integer.class);
        int docs = jdbc.queryForObject(
                "select count(*) from kb_document where deleted=false", Integer.class);
        Integer qa = jdbc.queryForObject(
                "select count(*) from kb_qa_record where created_at::date=current_date", Integer.class);

        List<DashboardSummaryVO.RecentKnowledgeBaseVO> recent = jdbc.query(
                "select id, name, category, visibility, updated_at, " +
                        "(select count(*) from kb_document d where d.kb_id = k.id and d.deleted = false) as docs " +
                        "from kb_knowledge_base k where deleted = false order by updated_at desc limit 3",
                (rs, rowNum) -> new DashboardSummaryVO.RecentKnowledgeBaseVO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getString("visibility"),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getInt("docs")));

        List<DashboardSummaryVO.TodoItemVO> todos = List.of(
                new DashboardSummaryVO.TodoItemVO("2026 年度目标确认", "人事", "今天 18:00 前", "orange"),
                new DashboardSummaryVO.TodoItemVO("华东区域差旅报销单", "财务", "明天 12:00 前", "blue"),
                new DashboardSummaryVO.TodoItemVO("办公用品领用审批", "行政", "3 月 20 日", "purple"));

        return new DashboardSummaryVO(kb, docs, qa == null ? 0 : qa, 92, todos.size(), recent, todos);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
