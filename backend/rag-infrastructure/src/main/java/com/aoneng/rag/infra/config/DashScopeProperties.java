package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DashScope 模型网关配置。涵盖 API Key、Base URL、嵌入 / 聊天 / 重排模型以及向量维度。
 * 配置错误（例如维度与 Qdrant collection 不一致）应在启动期通过单元测试发现，而不是线上运行时。
 */
@ConfigurationProperties(prefix = "dashscope")
public record DashScopeProperties(String apiKey, String baseUrl, String rerankBaseUrl,
                                  String embeddingModel, String chatModel, String rerankModel,
                                  int embeddingDimensions) {
    public DashScopeProperties {
        apiKey = apiKey == null ? "" : apiKey;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : baseUrl;
        rerankBaseUrl = rerankBaseUrl == null || rerankBaseUrl.isBlank() ? "https://dashscope.aliyuncs.com" : rerankBaseUrl;
        embeddingModel = embeddingModel == null || embeddingModel.isBlank() ? "text-embedding-v4" : embeddingModel;
        chatModel = chatModel == null || chatModel.isBlank() ? "qwen-plus" : chatModel;
        rerankModel = rerankModel == null || rerankModel.isBlank() ? "qwen3-rerank" : rerankModel;
        if (embeddingDimensions <= 0) embeddingDimensions = 1024;
    }
}
