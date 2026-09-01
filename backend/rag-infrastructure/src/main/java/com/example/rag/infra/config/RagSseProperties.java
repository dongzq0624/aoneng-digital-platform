package com.example.rag.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * SSE 流式问答配置属性类。
 *
 * <ul>
 *   <li>超时时间：SSE 连接最大保持时长（秒），超时后自动断开</li>
 *   <li>历史消息数：构造模型上下文时回溯的对话历史条数</li>
 *   <li>最大引用数：答案中最多引用的文档块数量</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "rag.sse")
@Validated
public record RagSseProperties(int timeoutSeconds, int historyMessageCount, int maxCitations) {

    public RagSseProperties {
        if (timeoutSeconds <= 0) timeoutSeconds = 120;
        if (historyMessageCount < 0) historyMessageCount = 8;
        if (maxCitations <= 0) maxCitations = 3;
    }
}
