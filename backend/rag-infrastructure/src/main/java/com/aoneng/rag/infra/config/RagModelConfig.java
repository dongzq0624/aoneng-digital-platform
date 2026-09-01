package com.aoneng.rag.infra.config;

import org.springframework.context.annotation.Configuration;

/**
 * 模型层 Bean 装配占位。当前 DashScope 客户端通过 {@code @Component} 自装配，
 * 后续若引入 Spring AI 等 SDK，可在此处集中声明 ChatClient / EmbeddingClient。
 */
@Configuration
public class RagModelConfig {
}
