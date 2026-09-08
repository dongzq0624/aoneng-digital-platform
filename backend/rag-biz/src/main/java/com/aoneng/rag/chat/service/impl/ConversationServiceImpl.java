package com.aoneng.rag.chat.service.impl;

import com.aoneng.rag.chat.dto.CreateEvalCaseDTO;
import com.aoneng.rag.chat.service.ConversationService;
import com.aoneng.rag.chat.vo.ConversationVO;
import com.aoneng.rag.chat.vo.CursorPageVO;
import com.aoneng.rag.chat.vo.EvalCaseVO;
import com.aoneng.rag.chat.vo.MessageVO;
import com.aoneng.rag.chat.vo.QaRecordDetailVO;
import com.aoneng.rag.chat.vo.QaRecordListItemVO;
import com.aoneng.rag.chat.vo.QaRecordListVO;
import com.aoneng.rag.application.repository.PlatformRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 会话服务默认实现。
 * 持久化操作委托给 {@link PlatformRepository}，
 * 并应用业务规则（标题非空、评分 ∈ {-1, 1}、所有权校验）。
 * 在 service 层把仓库层的 {@code Map<String, Object>} 转换为控制器 VO。
 */
@Service
public class ConversationServiceImpl implements ConversationService {

    private final PlatformRepository repo;

    public ConversationServiceImpl(PlatformRepository repo) {
        this.repo = repo;
    }

    @Override
    public CursorPageVO<ConversationVO> conversations(long userId, String cursor, int pageSize) {
        Map<String, Object> result = repo.conversations(userId, cursor, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        List<ConversationVO> views = items == null ? List.of() : items.stream().map(this::toConversation).toList();
        return new CursorPageVO<>(views,
                result.get("nextCursor") == null ? null : String.valueOf(result.get("nextCursor")),
                Boolean.TRUE.equals(result.get("hasMore")));
    }

    @Override
    public ConversationVO conversation(long userId, long conversationId) {
        return toConversation(repo.conversation(userId, conversationId));
    }

    @Override
    @Transactional
    public ConversationVO createConversation(long userId, String title, List<Long> selectedKbIds) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("会话标题不能为空");
        return toConversation(repo.createConversation(userId, title.trim(), selectedKbIds));
    }

    @Override
    @Transactional
    public ConversationVO updateTitle(long userId, long conversationId, String title) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("会话标题不能为空");
        return toConversation(repo.updateConversationTitle(userId, conversationId, title.trim()));
    }

    @Override
    @Transactional
    public void deleteConversation(long userId, long conversationId) {
        repo.deleteConversation(userId, conversationId);
    }

    @Override
    public CursorPageVO<MessageVO> messages(long userId, long conversationId, String before, int pageSize) {
        Map<String, Object> result = repo.messages(userId, conversationId, before, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        List<MessageVO> views = items == null ? List.of() : items.stream().map(this::toMessage).toList();
        return new CursorPageVO<>(views,
                result.get("nextCursor") == null ? null : String.valueOf(result.get("nextCursor")),
                Boolean.TRUE.equals(result.get("hasMore")));
    }

    @Override
    public QaRecordListVO qaRecords(long userId, int page, int pageSize) {
        Map<String, Object> raw = repo.qaRecords(userId, page, pageSize);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) raw.get("items");
        List<QaRecordListItemVO> views = items == null ? List.of() : items.stream().map(this::toQaListItem).toList();
        return new QaRecordListVO(
                views,
                intOrZero(raw.get("total")),
                intOrZero(raw.get("page")),
                intOrZero(raw.get("pageSize")));
    }

    @Override
    public QaRecordDetailVO qaRecord(long userId, long recordId) {
        return toQaDetail(repo.qaRecord(userId, recordId));
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
    public List<EvalCaseVO> retrievalEvaluationCases() {
        return repo.retrievalEvalCases().stream().map(this::toEvalCase).toList();
    }

    @Override
    public EvalCaseVO createRetrievalEvalCase(CreateEvalCaseDTO req) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("question", req.question());
        payload.put("expectedChunkIds", req.expectedChunkIds());
        payload.put("referenceAnswer", req.referenceAnswer());
        payload.put("note", req.note());
        payload.put("enabled", req.enabled() == null ? Boolean.TRUE : req.enabled());
        return toEvalCase(repo.createRetrievalEvalCase(payload));
    }

    // -------- Map -> VO 转换 --------

    private ConversationVO toConversation(Map<String, Object> row) {
        if (row == null) return null;
        return new ConversationVO(
                longOrZero(row.get("id")),
                longOrZero(row.get("userId")),
                stringOrDefault(row.get("title"), "新会话"),
                longList(row.get("selectedKbIds")),
                toInstant(row.get("lastMessageAt")),
                toInstant(row.get("createdAt")),
                toInstant(row.get("updatedAt")));
    }

    @SuppressWarnings("unchecked")
    private MessageVO toMessage(Map<String, Object> row) {
        if (row == null) return null;
        Object rawCitations = row.get("citations");
        List<Map<String, Object>> citations = rawCitations instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        return new MessageVO(
                longOrZero(row.get("id")),
                intOrZero(row.get("sequenceNo")),
                stringOrNull(row.get("role")),
                stringOrNull(row.get("content")),
                stringOrNull(row.get("status")),
                longList(row.get("sourceKbIds")),
                longList(row.get("retrievedChunkIds")),
                stringOrNull(row.get("modelName")),
                intOrNull(row.get("latencyMs")),
                stringOrNull(row.get("errorMessage")),
                longOrNull(row.get("qaRecordId")),
                citations,
                toInstant(row.get("createdAt")),
                toInstant(row.get("completedAt")));
    }

    private QaRecordListItemVO toQaListItem(Map<String, Object> row) {
        if (row == null) return null;
        return new QaRecordListItemVO(
                longOrZero(row.get("id")),
                longOrZero(row.get("conversationId")),
                longList(row.get("kbIds")),
                stringOrNull(row.get("question")),
                stringOrNull(row.get("answer")),
                stringOrNull(row.get("modelName")),
                intOrNull(row.get("latencyMs")),
                intOrNull(row.get("feedback")),
                toInstant(row.get("createdAt")));
    }

    private QaRecordDetailVO toQaDetail(Map<String, Object> row) {
        if (row == null) return null;
        return new QaRecordDetailVO(
                longOrZero(row.get("id")),
                longOrZero(row.get("userId")),
                longOrZero(row.get("conversationId")),
                longList(row.get("kbIds")),
                stringOrNull(row.get("question")),
                stringOrNull(row.get("answer")),
                longList(row.get("retrievedChunkIds")),
                stringOrNull(row.get("modelName")),
                intOrNull(row.get("latencyMs")),
                intOrNull(row.get("feedback")),
                stringOrNull(row.get("rerankScores")),
                Map.of(),
                toInstant(row.get("createdAt")));
    }

    @SuppressWarnings("unchecked")
    private EvalCaseVO toEvalCase(Map<String, Object> row) {
        if (row == null) return null;
        Object aggregate = row.get("aggregate");
        return new EvalCaseVO(
                longOrZero(row.get("id")),
                stringOrNull(row.get("question")),
                longList(row.get("expectedChunkIds")),
                stringOrNull(row.get("referenceAnswer")),
                row.get("enabled") == null ? Boolean.TRUE : (row.get("enabled") instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(row.get("enabled")))),
                stringOrNull(row.get("note")),
                aggregate instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(),
                toInstant(row.get("createdAt")),
                toInstant(row.get("updatedAt")));
    }

    private static long longOrZero(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static Long longOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int intOrZero(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Integer intOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String stringOrDefault(Object value, String fallback) {
        String s = stringOrNull(value);
        return s == null || s.isBlank() ? fallback : s;
    }

    @SuppressWarnings("unchecked")
    private static List<Long> longList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(java.util.Objects::nonNull).map(o -> {
            if (o instanceof Number n) return n.longValue();
            try {
                return Long.parseLong(String.valueOf(o));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }).filter(java.util.Objects::nonNull).distinct().toList();
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
