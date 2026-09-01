package com.example.rag.kb.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 异步文档处理服务接口。负责单个上传文档的解析→分块→嵌入→向量库写入流水线。
 * 控制器通过一次性 {@code start(...)} 调用（返回任务是否被接受）
 * 或 SSE 订阅者（用于接收进度事件）来使用。
 */
public interface DocumentProcessingService {

    /**
     * 启动文档处理任务。
     *
     * @param docId     文档 ID
     * @param kbId      知识库 ID
     * @param objectKey MinIO 对象键
     * @return 如果任务被接受返回 {@code true}，如果文档正在处理中则返回 {@code false}
     */
    boolean start(long docId, long kbId, String objectKey);

    /**
     * 订阅文档处理进度事件。发射器先同步发送最新快照，然后以 SSE 事件发送实时进度。
     *
     * @param docId 文档 ID
     * @return SSE 事件发射器
     */
    SseEmitter subscribe(long docId);
}
