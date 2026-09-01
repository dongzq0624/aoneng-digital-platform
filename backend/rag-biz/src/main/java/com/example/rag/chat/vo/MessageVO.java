package com.example.rag.chat.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话消息响应体（含引用）。
 *
 * @param id                 消息 ID
 * @param sequenceNo         会话内的消息序号
 * @param role               角色（user / assistant / system）
 * @param content            文本内容
 * @param status             状态（PENDING / STREAMING / COMPLETED / FAILED / CANCELLED）
 * @param sourceKbIds        本轮使用的知识库 ID 列表
 * @param retrievedChunkIds  本轮检索到的分块 ID 列表
 * @param modelName          生成模型（可空）
 * @param latencyMs          推理耗时（毫秒，可空）
 * @param errorMessage       错误信息（可空）
 * @param qaRecordId         关联的问答记录 ID（可空）
 * @param citations          引用列表
 * @param createdAt          创建时间
 * @param completedAt        完成时间（可空）
 */
public record MessageVO(
        long id,
        int sequenceNo,
        String role,
        String content,
        String status,
        List<Long> sourceKbIds,
        List<Long> retrievedChunkIds,
        String modelName,
        Integer latencyMs,
        String errorMessage,
        Long qaRecordId,
        List<Map<String, Object>> citations,
        Instant createdAt,
        Instant completedAt) {
}
