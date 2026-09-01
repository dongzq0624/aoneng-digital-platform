package com.example.rag.chat.service;

import com.example.rag.chat.dto.CreateEvalCaseRequest;

import java.util.List;
import java.util.Map;

/**
 * 会话、消息和问答记录生命周期服务接口。负责会话、消息、
 * QA 记录和检索评测用例的 CRUD 操作。
 */
public interface ConversationService {

    /**
     * 获取用户的会话列表（游标分页）。
     *
     * @param userId   用户 ID
     * @param cursor   游标
     * @param pageSize 每页数量
     * @return 会话列表
     */
    List<Map<String, Object>> conversations(long userId, String cursor, int pageSize);

    /**
     * 获取会话详情。
     *
     * @param userId        用户 ID
     * @param conversationId 会话 ID
     * @return 会话信息
     */
    Map<String, Object> conversation(long userId, long conversationId);

    /**
     * 创建新会话。
     *
     * @param userId        用户 ID
     * @param title        会话标题
     * @param selectedKbIds 关联的知识库 ID 列表
     * @return 新会话信息
     */
    Map<String, Object> createConversation(long userId, String title, List<Long> selectedKbIds);

    /**
     * 更新会话标题。
     *
     * @param userId        用户 ID
     * @param conversationId 会话 ID
     * @param title         新标题
     * @return 更新后的会话信息
     */
    Map<String, Object> updateTitle(long userId, long conversationId, String title);

    /**
     * 删除会话。
     *
     * @param userId        用户 ID
     * @param conversationId 会话 ID
     */
    void deleteConversation(long userId, long conversationId);

    /**
     * 获取会话消息历史。
     *
     * @param userId        用户 ID
     * @param conversationId 会话 ID
     * @param before       游标（上一页最后一条消息 ID）
     * @param pageSize     每页数量
     * @return 消息历史
     */
    Map<String, Object> messages(long userId, long conversationId, String before, int pageSize);

    /**
     * 获取问答记录列表（分页）。
     *
     * @param userId   用户 ID
     * @param page     页码
     * @param pageSize 每页数量
     * @return 问答记录列表
     */
    Map<String, Object> qaRecords(long userId, int page, int pageSize);

    /**
     * 获取问答记录详情。
     *
     * @param userId  用户 ID
     * @param recordId 记录 ID
     * @return 问答记录详情
     */
    Map<String, Object> qaRecord(long userId, long recordId);

    /**
     * 保存问答反馈。
     *
     * @param userId    用户 ID
     * @param qaRecordId QA 记录 ID
     * @param rating    评分（1 或 -1）
     * @param comment   评语
     * @throws java.util.NoSuchElementException 当记录不属于该用户时
     * @throws IllegalArgumentException 当评分无效时
     */
    void feedback(long userId, long qaRecordId, int rating, String comment);

    /**
     * 获取检索评测用例列表。
     *
     * @return 评测用例列表
     */
    List<Map<String, Object>> retrievalEvaluationCases();

    /**
     * 创建检索评测用例。
     *
     * @param request 创建请求
     * @return 创建的评测用例
     */
    Map<String, Object> createRetrievalEvalCase(CreateEvalCaseRequest request);
}
