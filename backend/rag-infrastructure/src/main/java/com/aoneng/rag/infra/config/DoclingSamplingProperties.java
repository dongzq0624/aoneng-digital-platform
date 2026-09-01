package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Local Docling bridge and complexity-aware sampling settings. */
@ConfigurationProperties(prefix = "docling")
public record DoclingSamplingProperties(boolean enabled, String endpoint,
                                        int timeoutSeconds, long maxBytes, int samplePages,
                                        int sampleRows, boolean fallbackToTika) {
    public DoclingSamplingProperties {
        endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:8090" : endpoint.replaceAll("/+$", "");
        timeoutSeconds = timeoutSeconds <= 0 ? 180 : Math.min(timeoutSeconds, 900);
        maxBytes = maxBytes <= 0 ? 50L * 1024 * 1024 : Math.min(maxBytes, 200L * 1024 * 1024);
        samplePages = samplePages <= 0 ? 8 : Math.min(samplePages, 100);
        sampleRows = sampleRows <= 0 ? 200 : Math.min(sampleRows, 10_000);
    }
}
