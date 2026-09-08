package com.aoneng.rag.domain.chat.repository;

import java.util.List;
import java.util.Map;

/**
 * 对话仓储接口（chat bounded context）。
 * 定义会话、消息、QA 记录等对话数据的查询与变更操作。
 */
public interface ChatRepository {

    // -------- Conversation --------

    /** 创建会话。 */
    long createConversation(long userId, String title, String selectedKbIds);

    /** 查询会话。 */
    Map<String, Object> findConversationById(long id, long userId);

    /** 查询用户会话列表。 */
    List<Map<String, Object>> findConversations(long userId, String cursorTime, Long cursorId, int limit);

    /** 更新会话标题。 */
    void updateConversationTitle(long id, long userId, String title);

    /** 软删除会话。 */
    int softDeleteConversation(long id, long userId);

    /** 锁定会话（用于 prepareChatTurn）。 */
    Long lockConversation(long id, long userId);

    /** 更新会话关联的知识库。 */
    void updateSelectedKbIds(long id, long[] kbIds);

    /** 更新最后消息时间。 */
    void touchLastMessage(long id);

    // -------- Message --------

    /** 插入聊天消息。 */
    long insertMessage(long conversationId, int sequenceNo, String role, String content,
                       String status, String sourceKbIds, String modelName,
                       String errorMessage, Long qaRecordId, boolean completed);

    /** 查询消息历史。 */
    List<Map<String, Object>> findMessages(long conversationId, Integer beforeSequence, int limit);

    /** 查询最近对话历史（用于构造 RAG 上下文）。 */
    List<Map<String, Object>> findRecentHistory(long conversationId, int limit);

    /** 查询下一条消息序号。 */
    int nextSequenceNo(long conversationId);

    /** 完成助手消息。 */
    void completeAssistantMessage(long messageId, long conversationId, String answer,
                                  long[] chunkIds, String modelName, int latencyMs, Long qaRecordId);

    /** 更新失败消息。 */
    void updateFailure(long messageId, long conversationId, String message);

    // -------- QA Record --------

    /** 插入 QA 记录。 */
    long insertQaRecord(long userId, String kbIds, String question, String answer,
                         String retrievedChunkIds, String modelName, int latencyMs,
                         long conversationId);

    /** 更新重排评分。 */
    void updateRerankScores(long id, String scores);

    /** 更新助手消息 ID。 */
    void updateAssistantMessageId(long qaId, long messageId);

    /** 查询 QA 记录分页列表。 */
    List<Map<String, Object>> findQaRecords(long userId, int limit, int offset);

    /** 统计用户 QA 记录数。 */
    Integer countQaRecordsByUser(long userId);

    /** 查询单条 QA 记录。 */
    Map<String, Object> findQaRecordById(long id, long userId);

    /** 统计 QA 记录是否存在。 */
    int countQaRecordById(long id, long userId);

    /** 插入 QA 记录（用于补录）。 */
    void insertQaRecord(long id, long userId, String question, String answer, String modelName, int latencyMs);

    /** 插入或更新反馈。 */
    void upsertFeedback(long qaRecordId, long userId, int rating, String comment);

    // -------- Citation --------

    /** 插入引用。 */
    void insertCitation(long messageId, Long chunkId, Long docId, Long kbId,
                          String fileName, String snippet, int rankNo, Double score);

    /** 查询消息的引用列表。 */
    List<Map<String, Object>> findCitationsByMessage(long messageId);

    // -------- Evaluation --------

    /** 查询所有评测用例。 */
    List<Map<String, Object>> findAllEvalCases();

    /** 查询评测用例。 */
    Map<String, Object> findEvalCaseById(long id);

    /** 创建评测用例。 */
    long createEvalCase(String question, String expectedChunkIds, String referenceAnswer, boolean enabled, String note);
}
