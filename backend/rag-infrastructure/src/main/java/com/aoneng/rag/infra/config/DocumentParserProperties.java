package com.aoneng.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared limits and thresholds for parser routing. */
@ConfigurationProperties(prefix = "document-parser")
public record DocumentParserProperties(long maxBytes, int pdfMinTextChars,
                                       double pdfNativePageRatio) {
    public DocumentParserProperties {
        maxBytes = maxBytes <= 0 ? 50L * 1024 * 1024 : Math.min(maxBytes, 200L * 1024 * 1024);
        pdfMinTextChars = pdfMinTextChars <= 0 ? 20 : Math.min(pdfMinTextChars, 10_000);
        pdfNativePageRatio = pdfNativePageRatio <= 0 || pdfNativePageRatio > 1 ? 0.6D : pdfNativePageRatio;
    }
}
