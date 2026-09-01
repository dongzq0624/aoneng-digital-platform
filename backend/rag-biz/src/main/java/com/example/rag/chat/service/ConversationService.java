package com.example.rag.chat.service;

import com.example.rag.chat.dto.CreateEvalCaseDTO;
import com.example.rag.chat.vo.ConversationVO;
import com.example.rag.chat.vo.CursorPageVO;
import com.example.rag.chat.vo.EvalCaseVO;
import com.example.rag.chat.vo.MessageVO;
import com.example.rag.chat.vo.QaRecordDetailVO;
import com.example.rag.chat.vo.QaRecordListVO;

import java.util.List;

/**
 * 会话、消息和问答记录生命周期服务接口。负责会话、消息、
 * QA 记录和检索评测用例的 CRUD 操作。返回类型为 VO，
 * 持久层 {@code Map<String, Object>} 到 VO 的转换在 impl 层完成。
 */
public interface ConversationService {

    /**
     * 获取用户的会话列表（游标分页）。
     */
    CursorPageVO<ConversationVO> conversations(long userId, String cursor, int pageSize);

    /**
     * 获取会话详情。
     */
    ConversationVO conversation(long userId, long conversationId);

    /**
     * 创建新会话。
     */
    ConversationVO createConversation(long userId, String title, List<Long> selectedKbIds);

    /**
     * 更新会话标题。
     */
    ConversationVO updateTitle(long userId, long conversationId, String title);

    /**
     * 删除会话。
     */
    void deleteConversation(long userId, long conversationId);

    /**
     * 获取会话消息历史（游标分页）。
     */
    CursorPageVO<MessageVO> messages(long userId, long conversationId, String before, int pageSize);

    /**
     * 获取问答记录列表（页码分页）。
     */
    QaRecordListVO qaRecords(long userId, int page, int pageSize);

    /**
     * 获取问答记录详情。
     */
    QaRecordDetailVO qaRecord(long userId, long recordId);

    /**
     * 保存问答反馈。
     */
    void feedback(long userId, long qaRecordId, int rating, String comment);

    /**
     * 获取检索评测用例列表。
     */
    List<EvalCaseVO> retrievalEvaluationCases();

    /**
     * 创建检索评测用例。
     */
    EvalCaseVO createRetrievalEvalCase(CreateEvalCaseDTO request);
}
