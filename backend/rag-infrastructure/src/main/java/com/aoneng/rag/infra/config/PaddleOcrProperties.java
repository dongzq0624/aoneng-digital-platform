package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paddleocr")
public record PaddleOcrProperties(boolean enabled, String endpoint, int timeoutSeconds) {
    public PaddleOcrProperties {
        endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:8091" : endpoint.replaceAll("/+$", "");
        timeoutSeconds = timeoutSeconds <= 0 ? 180 : Math.min(timeoutSeconds, 900);
    }
}
