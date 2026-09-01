package com.aoneng.rag.chat.vo;

/**
 * 文档片段详情响应体。RagController 中用于兼容历史 chunk 接口。
 *
 * @param chunkId 分块 ID
 * @param docId   所属文档 ID
 * @param kbId    所属知识库 ID
 * @param content 分块内容
 */
public record ChunkVO(long chunkId, long docId, long kbId, String content) {
}
