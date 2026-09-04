package com.aoneng.rag.dashboard.service.impl;

import com.aoneng.rag.dashboard.vo.DashboardSummaryVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private JdbcTemplate jdbc;

    @Test
    void summaryUsesEvaluationRecallAsPercentage() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(AVG("), eq(Number.class)))
                .thenReturn(new BigDecimal("0.873"));
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<DashboardSummaryVO.RecentKnowledgeBaseVO>>any()))
                .thenReturn(List.of());

        DashboardSummaryVO summary = new DashboardServiceImpl(jdbc).summary();

        assertEquals(87, summary.recallRate());
    }

    @Test
    void summaryNormalizesPercentageMetrics() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(AVG("), eq(Number.class)))
                .thenReturn(new BigDecimal("87.6"));
        when(jdbc.query(anyString(), ArgumentMatchers.<RowMapper<DashboardSummaryVO.RecentKnowledgeBaseVO>>any()))
                .thenReturn(List.of());

        DashboardSummaryVO summary = new DashboardServiceImpl(jdbc).summary();

        assertEquals(88, summary.recallRate());
    }
}
