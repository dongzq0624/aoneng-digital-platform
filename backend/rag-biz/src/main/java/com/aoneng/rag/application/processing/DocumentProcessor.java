package com.aoneng.rag.application.processing;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 文档处理应用服务接口。
 * 编排解析 → 分块 → 嵌入 → 向量写入的异步流水线，并通过 SSE 流式传输进度事件。
 */
public interface DocumentProcessor {

    /**
     * 启动文档处理任务。
     *
     * @param docId 文档 ID
     * @param kbId  知识库 ID
     * @param objectKey MinIO 对象键
     * @return 是否成功启动
     */
    boolean start(long docId, long kbId, String objectKey);

    /** Remove all persisted chunks and vector points for a document before deletion. */
    void deleteIndex(long docId);

    /**
     * 订阅文档处理进度事件。
     *
     * @param docId 文档 ID
     * @return SSE 发射器
     */
    SseEmitter subscribe(long docId);
}
