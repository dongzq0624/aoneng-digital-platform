package com.example.rag.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class ChatHistoryMigration {
    private final DataSource dataSource;

    public ChatHistoryMigration(JdbcTemplate jdbc) {
        this.dataSource = jdbc.getDataSource();
    }

    @PostConstruct
    public void migrate() {
        if (dataSource == null) {
            throw new IllegalStateException("PostgreSQL 数据源未配置，无法初始化会话历史结构");
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V2__chat_conversations.sql"),
                new ClassPathResource("db/migration/V3__retrieval_evaluation.sql"));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
