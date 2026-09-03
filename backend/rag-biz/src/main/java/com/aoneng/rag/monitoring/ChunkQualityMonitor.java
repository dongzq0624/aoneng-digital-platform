package com.aoneng.rag.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically reports chunk/index quality issues for operational alerting. */
@Component
public class ChunkQualityMonitor {
    private static final Logger log = LoggerFactory.getLogger(ChunkQualityMonitor.class);
    private final JdbcTemplate jdbc;

    public ChunkQualityMonitor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(initialDelayString = "${rag.quality-monitor.initial-delay-ms:60000}",
            fixedDelayString = "${rag.quality-monitor.poll-ms:300000}")
    public void check() {
        try {
            jdbc.query("SELECT doc_id, identical_child_count, parent_without_embedding_count, cross_page_parent_ratio "
                            + "FROM kb_chunk_quality_metrics "
                            + "WHERE identical_child_count > 0 OR parent_without_embedding_count > 0 "
                            + "OR cross_page_parent_ratio >= 80",
                    (org.springframework.jdbc.core.RowCallbackHandler) rs -> log.warn("Chunk quality alert: docId={}, identicalChildren={}, parentsWithoutEmbedding={}, crossPageRatio={}%%",
                            rs.getLong("doc_id"), rs.getLong("identical_child_count"),
                            rs.getLong("parent_without_embedding_count"), rs.getBigDecimal("cross_page_parent_ratio")));
        } catch (Exception failure) {
            log.warn("Chunk quality monitor unavailable", failure);
        }
    }
}
