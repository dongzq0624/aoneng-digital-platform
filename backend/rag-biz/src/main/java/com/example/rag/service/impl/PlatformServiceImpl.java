package com.example.rag.service.impl;

import com.example.rag.domain.KbScope;
import com.example.rag.domain.auth.OrgRecords;
import com.example.rag.service.PlatformRepository;
import com.example.rag.service.PlatformService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 平台服务实现（阶段 D）。
 * 当前主体仅委托给现有的 {@link PlatformRepository}，
 * 行为保持不变以便分层架构渐进成熟。
 *
 * <p>控制器迁移（阶段 F）完成后，调用方将直接依赖此类，
 * 旧版仓库将被废弃。</p>
 */
@Service
public class PlatformServiceImpl implements PlatformService {

    private final PlatformRepository delegate;

    public PlatformServiceImpl(PlatformRepository delegate) {
        this.delegate = delegate;
    }

    // ---------- Users ----------

    @Override
    public List<OrgRecords.UserRow> users() {
        return delegate.users();
    }

    @Override
    public Optional<OrgRecords.UserRow> userByUsername(String username) {
        return delegate.userByUsername(username);
    }

    @Override
    public Optional<String> passwordHash(String username) {
        return delegate.passwordHash(username);
    }

    @Override
    public OrgRecords.UserRow createUser(Map<String, Object> req) {
        return delegate.createUser(req);
    }

    @Override
    public OrgRecords.UserRow updateUser(long id, Map<String, Object> req) {
        return delegate.updateUser(id, req);
    }

    @Override
    public void deleteUser(long id) {
        delegate.deleteUser(id);
    }

    @Override
    public void resetPassword(long id) {
        delegate.resetPassword(id);
    }

    @Override
    public OrgRecords.UserRow userById(long id) {
        return delegate.userById(id);
    }

    // ---------- Departments ----------

    @Override
    public List<OrgRecords.DepartmentRow> departments() {
        return delegate.departments();
    }

    @Override
    public OrgRecords.DepartmentRow createDepartment(Map<String, Object> req) {
        return delegate.createDepartment(req);
    }

    @Override
    public OrgRecords.DepartmentRow updateDepartment(long id, Map<String, Object> req) {
        return delegate.updateDepartment(id, req);
    }

    @Override
    public void deleteDepartment(long id) {
        delegate.deleteDepartment(id);
    }

    // ---------- Roles ----------

    @Override
    public List<OrgRecords.RoleRow> roles() {
        return delegate.roles();
    }

    @Override
    public OrgRecords.RoleRow createRole(Map<String, Object> req) {
        return delegate.createRole(req);
    }

    @Override
    public OrgRecords.RoleRow updateRole(long id, Map<String, Object> req) {
        return delegate.updateRole(id, req);
    }

    @Override
    public void deleteRole(long id) {
        delegate.deleteRole(id);
    }

    @Override
    public List<Long> roleMenuIds(long id) {
        return delegate.roleMenuIds(id);
    }

    @Override
    public void updateRoleMenus(long id, Map<String, Object> req) {
        delegate.updateRoleMenus(id, req);
    }

    // ---------- Menus ----------

    @Override
    public List<OrgRecords.MenuRow> menus() {
        return delegate.menus();
    }

    @Override
    public OrgRecords.MenuRow createMenu(Map<String, Object> req) {
        return delegate.createMenu(req);
    }

    @Override
    public OrgRecords.MenuRow updateMenu(long id, Map<String, Object> req) {
        return delegate.updateMenu(id, req);
    }

    @Override
    public void deleteMenu(long id) {
        delegate.deleteMenu(id);
    }

    @Override
    public List<Long> userMenuIds(String username) {
        return delegate.userMenuIds(username);
    }

    // ---------- Knowledge base ----------

    @Override
    public List<Map<String, Object>> bases() {
        return delegate.bases();
    }

    @Override
    public KbScope kbScope(String username) {
        return delegate.kbScope(username);
    }

    @Override
    public List<Map<String, Object>> accessibleBases(KbScope scope) {
        return delegate.accessibleBases(scope);
    }

    @Override
    public List<Long> accessibleBaseIds(KbScope scope) {
        return delegate.accessibleBaseIds(scope);
    }

    @Override
    public boolean canReadBase(long id, KbScope scope) {
        return delegate.canReadBase(id, scope);
    }

    @Override
    public boolean canManageBase(long id, KbScope scope) {
        return delegate.canManageBase(id, scope);
    }

    @Override
    public Map<String, Object> baseForScope(long id, KbScope scope) {
        return delegate.baseForScope(id, scope);
    }

    @Override
    public long createBase(Map<String, Object> r, KbScope scope) {
        return delegate.createBase(r, scope);
    }

    @Override
    public Map<String, Object> base(long id) {
        return delegate.base(id);
    }

    @Override
    public void updateBase(long id, Map<String, Object> r, KbScope scope) {
        delegate.updateBase(id, r, scope);
    }

    @Override
    public List<Long> allowedDepartmentIds(long kbId) {
        return delegate.allowedDepartmentIds(kbId);
    }

    @Override
    public void updateAllowedDepartments(long kbId, List<Long> deptIds, KbScope scope) {
        delegate.updateAllowedDepartments(kbId, deptIds, scope);
    }

    @Override
    public void deleteBase(long id) {
        delegate.deleteBase(id);
    }

    // ---------- Documents / chunks ----------

    @Override
    public long createDoc(long kbId, String name, String type, long size, String key, long uploader) {
        return delegate.createDoc(kbId, name, type, size, key, uploader);
    }

    @Override
    public Map<String, Object> doc(long id) {
        return delegate.doc(id);
    }

    @Override
    public List<Map<String, Object>> docs(long kbId) {
        return delegate.docs(kbId);
    }

    @Override
    public void status(long id, String parse, String chunk, int count, String error) {
        delegate.status(id, parse, chunk, count, error);
    }

    @Override
    public void deleteDoc(long id) {
        delegate.deleteDoc(id);
    }

    @Override
    @Transactional
    public void clearChunks(long docId) {
        delegate.clearChunks(docId);
    }

    @Override
    public List<Map<String, Object>> keywordChunks(List<String> terms, List<Long> allowedKbIds, int limit) {
        return delegate.keywordChunks(terms, allowedKbIds, limit);
    }

    // ---------- Audit ----------

    @Override
    public List<PlatformRepository.AuditLogRow> auditLogs() {
        return delegate.auditLogs();
    }

    // ---------- Conversations / RAG ----------

    @Override
    public Map<String, Object> conversations(long userId, String cursor, int pageSize) {
        return delegate.conversations(userId, cursor, pageSize);
    }

    @Override
    public Map<String, Object> conversation(long userId, long conversationId) {
        return delegate.conversation(userId, conversationId);
    }

    @Override
    @Transactional
    public Map<String, Object> createConversation(long userId, String title, List<Long> selectedKbIds) {
        return delegate.createConversation(userId, title, selectedKbIds);
    }

    @Override
    @Transactional
    public Map<String, Object> updateConversationTitle(long userId, long conversationId, String title) {
        return delegate.updateConversationTitle(userId, conversationId, title);
    }

    @Override
    @Transactional
    public void deleteConversation(long userId, long conversationId) {
        delegate.deleteConversation(userId, conversationId);
    }

    @Override
    @Transactional
    public PlatformRepository.ChatTurn prepareChatTurn(long userId, Long requestedConversationId, String question, List<Long> permittedKbIds) {
        return delegate.prepareChatTurn(userId, requestedConversationId, question, permittedKbIds);
    }

    @Override
    public Map<String, Object> messages(long userId, long conversationId, String before, int pageSize) {
        return delegate.messages(userId, conversationId, before, pageSize);
    }

    @Override
    public String recentChatHistory(long userId, long conversationId) {
        return delegate.recentChatHistory(userId, conversationId);
    }

    @Override
    @Transactional
    public long completeChatTurn(PlatformRepository.ChatTurn turn, long userId, String question, String answer,
                                  List<Long> permittedKbIds, List<Long> retrievedChunkIds,
                                  List<Map<String, Object>> citations, int latencyMs) {
        return delegate.completeChatTurn(turn, userId, question, answer, permittedKbIds, retrievedChunkIds, citations, latencyMs);
    }

    @Override
    @Transactional
    public long completeChatTurn(PlatformRepository.ChatTurn turn, long userId, String question, String answer,
                                  List<Long> permittedKbIds, List<Long> retrievedChunkIds,
                                  List<Map<String, Object>> citations, int latencyMs,
                                  Map<String, Object> retrievalTrace) {
        return delegate.completeChatTurn(turn, userId, question, answer, permittedKbIds, retrievedChunkIds, citations, latencyMs, retrievalTrace);
    }

    @Override
    @Transactional
    public void failChatTurn(PlatformRepository.ChatTurn turn, String message) {
        delegate.failChatTurn(turn, message);
    }

    @Override
    public Map<String, Object> qaRecords(long userId, int page, int pageSize) {
        return delegate.qaRecords(userId, page, pageSize);
    }

    @Override
    public Map<String, Object> qaRecord(long userId, long recordId) {
        return delegate.qaRecord(userId, recordId);
    }

    @Override
    public boolean ownsQaRecord(long userId, long recordId) {
        return delegate.ownsQaRecord(userId, recordId);
    }

    @Override
    public void feedback(long record, long user, int rating, String comment) {
        delegate.feedback(record, user, rating, comment);
    }

    // ---------- Evaluation ----------

    @Override
    public List<Map<String, Object>> retrievalEvalCases() {
        return delegate.retrievalEvalCases();
    }

    @Override
    public Map<String, Object> retrievalEvalCase(long id) {
        return delegate.retrievalEvalCase(id);
    }

    @Override
    public Map<String, Object> createRetrievalEvalCase(Map<String, Object> request) {
        return delegate.createRetrievalEvalCase(request);
    }
}
