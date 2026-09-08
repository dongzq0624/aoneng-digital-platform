package com.aoneng.rag.infra.repository;

import com.aoneng.rag.domain.chat.mapper.KbChatMessageCitationMapper;
import com.aoneng.rag.domain.chat.mapper.KbChatMessageMapper;
import com.aoneng.rag.domain.chat.mapper.KbConversationMapper;
import com.aoneng.rag.domain.chat.mapper.KbQaRecordMapper;
import com.aoneng.rag.domain.chat.po.KbChatMessageCitationPO;
import com.aoneng.rag.domain.chat.po.KbChatMessagePO;
import com.aoneng.rag.domain.chat.po.KbConversationPO;
import com.aoneng.rag.domain.chat.po.KbQaRecordPO;
import com.aoneng.rag.domain.chat.repository.ChatRepository;
import com.aoneng.rag.domain.kb.mapper.KbRetrievalEvalCaseMapper;
import com.aoneng.rag.domain.kb.po.KbRetrievalEvalCasePO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Repository
public class ChatRepositoryImpl implements ChatRepository {
    private final KbConversationMapper conversationMapper;
    private final KbChatMessageMapper messageMapper;
    private final KbQaRecordMapper qaMapper;
    private final KbChatMessageCitationMapper citationMapper;
    private final KbRetrievalEvalCaseMapper evalMapper;
    private final JdbcTemplate jdbc;

    public ChatRepositoryImpl(KbConversationMapper conversationMapper, KbChatMessageMapper messageMapper,
                              KbQaRecordMapper qaMapper, KbChatMessageCitationMapper citationMapper,
                              KbRetrievalEvalCaseMapper evalMapper, DataSource dataSource) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.qaMapper = qaMapper;
        this.citationMapper = citationMapper;
        this.evalMapper = evalMapper;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public long createConversation(long userId, String title, String selectedKbIds) {
        KbConversationPO p = new KbConversationPO();
        p.setUserId(userId);
        p.setTitle(title);
        p.setSelectedKbIds(selectedKbIds);
        conversationMapper.insertReturningId(p);
        return p.getId();
    }

    @Override
    public Map<String, Object> findConversationById(long id, long userId) {
        return conversationMapper.selectConversation(id, userId);
    }

    @Override
    public List<Map<String, Object>> findConversations(long userId, String cursorTime, Long cursorId, int limit) {
        Timestamp ts = parseTimestamp(cursorTime);
        return conversationMapper.selectConversations(userId, ts, cursorId, limit);
    }

    @Override
    public void updateConversationTitle(long id, long userId, String title) {
        conversationMapper.updateTitle(id, userId, title);
    }

    @Override
    public int softDeleteConversation(long id, long userId) {
        return conversationMapper.softDelete(id, userId);
    }

    @Override
    public Long lockConversation(long id, long userId) {
        return conversationMapper.lockConversation(id, userId);
    }

    @Override
    public void updateSelectedKbIds(long id, long[] kbIds) {
        conversationMapper.updateSelectedKbIds(id, kbIds);
    }

    @Override
    public void touchLastMessage(long id) {
        conversationMapper.touchLastMessage(id);
    }

    @Override
    public long insertMessage(long conversationId, int sequenceNo, String role, String content, String status, String sourceKbIds, String modelName, String errorMessage, Long qaRecordId, boolean completed) {
        KbChatMessagePO p = new KbChatMessagePO();
        p.setConversationId(conversationId);
        p.setSequenceNo(sequenceNo);
        p.setRole(role);
        p.setContent(content);
        p.setStatus(status);
        p.setSourceKbIds(sourceKbIds);
        p.setModelName(modelName);
        p.setErrorMessage(errorMessage);
        p.setQaRecordId(qaRecordId);
        if (completed) p.setCompletedAt(OffsetDateTime.now());
        messageMapper.insertReturningId(p);
        return p.getId();
    }

    @Override
    public List<Map<String, Object>> findMessages(long conversationId, Integer beforeSequence, int limit) {
        return messageMapper.selectMessages(conversationId, beforeSequence, limit);
    }

    @Override
    public List<Map<String, Object>> findRecentHistory(long conversationId, int limit) {
        return messageMapper.selectRecentHistory(conversationId, limit);
    }

    @Override
    public int nextSequenceNo(long conversationId) {
        return messageMapper.nextSequenceNo(conversationId);
    }

    @Override
    public void completeAssistantMessage(long messageId, long conversationId, String answer, long[] chunkIds, String modelName, int latencyMs, Long qaRecordId) {
        messageMapper.completeAssistantMessage(messageId, conversationId, answer, chunkIds, modelName, latencyMs, qaRecordId == null ? 0 : qaRecordId);
    }

    @Override
    public void updateFailure(long messageId, long conversationId, String message) {
        messageMapper.updateFailure(messageId, conversationId, message);
    }

    @Override
    public long insertQaRecord(long userId, String kbIds, String question, String answer, String retrievedChunkIds, String modelName, int latencyMs, long conversationId) {
        KbQaRecordPO p = new KbQaRecordPO();
        p.setUserId(userId);
        p.setKbIds(kbIds);
        p.setQuestion(question);
        p.setAnswer(answer);
        p.setRetrievedChunkIds(retrievedChunkIds);
        p.setModelName(modelName);
        p.setLatencyMs(latencyMs);
        p.setConversationId(conversationId);
        qaMapper.insertReturningId(p);
        return p.getId();
    }

    @Override
    public void updateRerankScores(long id, String scores) {
        qaMapper.updateRerankScores(id, scores);
    }

    @Override
    public void updateAssistantMessageId(long qaId, long messageId) {
        qaMapper.updateAssistantMessageId(qaId, messageId);
    }

    @Override
    public List<Map<String, Object>> findQaRecords(long userId, int limit, int offset) {
        return qaMapper.selectRecords(userId, limit, offset);
    }

    @Override
    public Integer countQaRecordsByUser(long userId) {
        return qaMapper.countByUser(userId);
    }

    @Override
    public Map<String, Object> findQaRecordById(long id, long userId) {
        return qaMapper.selectRecord(id, userId);
    }

    @Override
    public int countQaRecordById(long id, long userId) {
        return qaMapper.existsById(id, userId);
    }

    @Override
    public void insertQaRecord(long id, long userId, String question, String answer, String modelName, int latencyMs) {
        jdbc.update("INSERT INTO kb_qa_record(id,user_id,question,answer,model_name,latency_ms) VALUES(?,?,?,?,?,?) ON CONFLICT (id) DO UPDATE SET answer=EXCLUDED.answer, model_name=EXCLUDED.model_name, latency_ms=EXCLUDED.latency_ms", id, userId, question, answer, modelName, latencyMs);
    }

    @Override
    public void upsertFeedback(long qaRecordId, long userId, int rating, String comment) {
        jdbc.update("INSERT INTO kb_feedback(qa_record_id,user_id,rating,comment) VALUES(?,?,?,?) ON CONFLICT (qa_record_id) DO UPDATE SET user_id=EXCLUDED.user_id,rating=EXCLUDED.rating,comment=EXCLUDED.comment", qaRecordId, userId, rating, comment);
        jdbc.update("UPDATE kb_qa_record SET feedback=? WHERE id=? AND user_id=?", rating, qaRecordId, userId);
    }

    @Override
    public void insertCitation(long messageId, Long chunkId, Long docId, Long kbId, String fileName, String snippet, int rankNo, Double score) {
        KbChatMessageCitationPO p = new KbChatMessageCitationPO();
        p.setMessageId(messageId);
        p.setChunkId(chunkId);
        p.setDocId(docId);
        p.setKbId(kbId);
        p.setFileName(fileName);
        p.setSnippet(snippet);
        p.setRankNo(rankNo);
        p.setScore(score);
        citationMapper.insertCitation(p);
    }

    @Override
    public List<Map<String, Object>> findCitationsByMessage(long messageId) {
        return citationMapper.selectByMessage(messageId).stream().map(this::citationMap).toList();
    }

    @Override
    public List<Map<String, Object>> findAllEvalCases() {
        return evalMapper.selectAllCases();
    }

    @Override
    public Map<String, Object> findEvalCaseById(long id) {
        return evalMapper.selectCaseById(id);
    }

    @Override
    public long createEvalCase(String question, String expectedChunkIds, String referenceAnswer, boolean enabled, String note) {
        KbRetrievalEvalCasePO p = new KbRetrievalEvalCasePO();
        p.setQuestion(question);
        p.setExpectedChunkIds(expectedChunkIds);
        p.setReferenceAnswer(referenceAnswer);
        p.setEnabled(enabled);
        p.setNote(note);
        evalMapper.insertCase(p);
        return p.getId();
    }

    private Map<String, Object> citationMap(KbChatMessageCitationPO p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("messageId", p.getMessageId()); m.put("chunkId", p.getChunkId());
        m.put("docId", p.getDocId()); m.put("kbId", p.getKbId()); m.put("fileName", p.getFileName());
        m.put("snippet", p.getSnippet()); m.put("rankNo", p.getRankNo()); m.put("score", p.getScore());
        return m;
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Timestamp.from(OffsetDateTime.parse(value).toInstant());
        } catch (RuntimeException ignored) {
            return Timestamp.valueOf(value);
        }
    }
}
