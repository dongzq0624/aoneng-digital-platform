package com.example.rag.service;

import com.example.rag.domain.KbScope;
import com.example.rag.domain.auth.OrgRecords;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service contract for org management (users / departments / roles / menus) and knowledge base
 * operations. The implementation lives under {@code com.example.rag.service.impl}.
 *
 * <p>Existing controllers still depend on {@link PlatformRepository}; this interface is the
 * first step in the layered refactor (Phase D) and will be adopted once the controllers have been
 * migrated in Phase F.</p>
 */
public interface PlatformService {

    // Users
    List<OrgRecords.UserRow> users();

    Optional<OrgRecords.UserRow> userByUsername(String username);

    Optional<String> passwordHash(String username);

    OrgRecords.UserRow createUser(Map<String, Object> req);

    OrgRecords.UserRow updateUser(long id, Map<String, Object> req);

    void deleteUser(long id);

    void resetPassword(long id);

    OrgRecords.UserRow userById(long id);

    // Departments
    List<OrgRecords.DepartmentRow> departments();

    OrgRecords.DepartmentRow createDepartment(Map<String, Object> req);

    OrgRecords.DepartmentRow updateDepartment(long id, Map<String, Object> req);

    void deleteDepartment(long id);

    // Roles
    List<OrgRecords.RoleRow> roles();

    OrgRecords.RoleRow createRole(Map<String, Object> req);

    OrgRecords.RoleRow updateRole(long id, Map<String, Object> req);

    void deleteRole(long id);

    List<Long> roleMenuIds(long id);

    void updateRoleMenus(long id, Map<String, Object> req);

    // Menus
    List<OrgRecords.MenuRow> menus();

    OrgRecords.MenuRow createMenu(Map<String, Object> req);

    OrgRecords.MenuRow updateMenu(long id, Map<String, Object> req);

    void deleteMenu(long id);

    List<Long> userMenuIds(String username);

    // Knowledge base
    List<Map<String, Object>> bases();

    KbScope kbScope(String username);

    List<Map<String, Object>> accessibleBases(KbScope scope);

    List<Long> accessibleBaseIds(KbScope scope);

    boolean canReadBase(long id, KbScope scope);

    boolean canManageBase(long id, KbScope scope);

    Map<String, Object> baseForScope(long id, KbScope scope);

    long createBase(Map<String, Object> r, KbScope scope);

    Map<String, Object> base(long id);

    void updateBase(long id, Map<String, Object> r, KbScope scope);

    List<Long> allowedDepartmentIds(long kbId);

    void updateAllowedDepartments(long kbId, List<Long> deptIds, KbScope scope);

    void deleteBase(long id);

    // Documents / chunks
    long createDoc(long kbId, String name, String type, long size, String key, long uploader);

    Map<String, Object> doc(long id);

    List<Map<String, Object>> docs(long kbId);

    void status(long id, String parse, String chunk, int count, String error);

    void deleteDoc(long id);

    void clearChunks(long docId);

    List<Map<String, Object>> keywordChunks(List<String> terms, List<Long> allowedKbIds, int limit);

    // Audit
    List<PlatformRepository.AuditLogRow> auditLogs();

    // Conversation / RAG
    Map<String, Object> conversations(long userId, String cursor, int pageSize);

    Map<String, Object> conversation(long userId, long conversationId);

    Map<String, Object> createConversation(long userId, String title, List<Long> selectedKbIds);

    Map<String, Object> updateConversationTitle(long userId, long conversationId, String title);

    void deleteConversation(long userId, long conversationId);

    PlatformRepository.ChatTurn prepareChatTurn(long userId, Long requestedConversationId, String question, List<Long> permittedKbIds);

    Map<String, Object> messages(long userId, long conversationId, String before, int pageSize);

    String recentChatHistory(long userId, long conversationId);

    long completeChatTurn(PlatformRepository.ChatTurn turn, long userId, String question, String answer,
                          List<Long> permittedKbIds, List<Long> retrievedChunkIds,
                          List<Map<String, Object>> citations, int latencyMs);

    long completeChatTurn(PlatformRepository.ChatTurn turn, long userId, String question, String answer,
                          List<Long> permittedKbIds, List<Long> retrievedChunkIds,
                          List<Map<String, Object>> citations, int latencyMs,
                          Map<String, Object> retrievalTrace);

    void failChatTurn(PlatformRepository.ChatTurn turn, String message);

    Map<String, Object> qaRecords(long userId, int page, int pageSize);

    Map<String, Object> qaRecord(long userId, long recordId);

    boolean ownsQaRecord(long userId, long recordId);

    void feedback(long record, long user, int rating, String comment);

    // Evaluation
    List<Map<String, Object>> retrievalEvalCases();

    Map<String, Object> retrievalEvalCase(long id);

    Map<String, Object> createRetrievalEvalCase(Map<String, Object> request);
}
