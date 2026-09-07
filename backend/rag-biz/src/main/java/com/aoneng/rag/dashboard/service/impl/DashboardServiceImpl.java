package com.aoneng.rag.dashboard.service.impl;

import com.aoneng.rag.dashboard.service.DashboardService;
import com.aoneng.rag.dashboard.vo.DashboardSummaryVO;
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

        Number averageRecall = jdbc.queryForObject(
                "SELECT COALESCE(AVG(" + recallMetricExpression() + "),0) " +
                        "FROM rag_evaluation_task t " +
                        "WHERE t.status='SUCCESS'", Number.class);

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

        return new DashboardSummaryVO(kb, docs, qa == null ? 0 : qa,
                toPercentage(averageRecall), recent);
    }

    /**
     * 评估服务的版本可能使用不同的召回率键名。按优先级读取最具体的 @5 指标，
     * 再兼容通用召回率及 RAGAS 的上下文召回率，避免升级评估服务后仪表盘归零。
     */
    private String recallMetricExpression() {
        return "CASE " +
                "WHEN t.metrics->>'recall_at_5' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recall_at_5')::numeric " +
                "WHEN t.metrics->>'recallAt5' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recallAt5')::numeric " +
                "WHEN t.metrics->>'recall_at_k' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recall_at_k')::numeric " +
                "WHEN t.metrics->>'recallAtK' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recallAtK')::numeric " +
                "WHEN t.metrics->>'recall' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'recall')::numeric " +
                "WHEN t.metrics->>'context_recall' ~ '^[0-9]+(\\.[0-9]+)?$' THEN (t.metrics->>'context_recall')::numeric " +
                "END";
    }

    private int toPercentage(Number raw) {
        if (raw == null) return 0;
        double value = raw.doubleValue();
        if (!Double.isFinite(value)) return 0;
        // RAGAS normally returns [0,1], while some offline evaluators return [0,100].
        double ratio = value > 1D && value <= 100D ? value / 100D : value;
        return (int) Math.round(Math.max(0D, Math.min(1D, ratio)) * 100D);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
