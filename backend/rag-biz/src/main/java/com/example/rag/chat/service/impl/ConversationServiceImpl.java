package com.example.rag.chat.service.impl;

import com.example.rag.chat.dto.CreateEvalCaseRequest;
import com.example.rag.chat.service.ConversationService;
import com.example.rag.service.PlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 会话服务默认实现。
 * 持久化操作委托给 {@link PlatformRepository}，
 * 并应用业务规则（标题非空、评分 ∈ {-1, 1}、所有权校验）。
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final PlatformRepository repo;

    public ConversationServiceImpl(PlatformRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<Map<String, Object>> conversations(long userId, String cursor, int pageSize) {
        Map<String, Object> result = repo.conversations(userId, cursor, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        return items;
    }

    @Override
    public Map<String, Object> conversation(long userId, long conversationId) {
        return repo.conversation(userId, conversationId);
    }

    @Override
    @Transactional
    public Map<String, Object> createConversation(long userId, String title, List<Long> selectedKbIds) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("会话标题不能为空");
        return repo.createConversation(userId, title.trim(), selectedKbIds);
    }

    @Override
    @Transactional
    public Map<String, Object> updateTitle(long userId, long conversationId, String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("会话标题不能为空");
        return repo.updateConversationTitle(userId, conversationId, title.trim());
    }

    @Override
    @Transactional
    public void deleteConversation(long userId, long conversationId) {
        repo.deleteConversation(userId, conversationId);
    }

    @Override
    public Map<String, Object> messages(long userId, long conversationId, String before, int pageSize) {
        return repo.messages(userId, conversationId, before, pageSize);
    }

    @Override
    public Map<String, Object> qaRecords(long userId, int page, int pageSize) {
        return repo.qaRecords(userId, page, pageSize);
    }

    @Override
    public Map<String, Object> qaRecord(long userId, long recordId) {
        return repo.qaRecord(userId, recordId);
    }

    @Override
    public void feedback(long userId, long qaRecordId, int rating, String comment) {
        if (!repo.ownsQaRecord(userId, qaRecordId)) {
            throw new NoSuchElementException("问答记录不存在或无权访问");
        }
        if (rating != 1 && rating != -1) {
            throw new IllegalArgumentException("评分必须为 1 或 -1");
        }
        repo.feedback(qaRecordId, userId, rating, comment);
    }

    @Override
    public List<Map<String, Object>> retrievalEvaluationCases() {
        return repo.retrievalEvalCases();
    }

    @Override
    public Map<String, Object> createRetrievalEvalCase(CreateEvalCaseRequest req) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", req.question());
        payload.put("expectedChunkIds", req.expectedChunkIds());
        payload.put("note", req.note());
        payload.put("enabled", req.enabled() == null ? Boolean.TRUE : req.enabled());
        return repo.createRetrievalEvalCase(payload);
    }
}
