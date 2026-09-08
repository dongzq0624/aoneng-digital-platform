package com.aoneng.rag.application.repository;

import com.aoneng.rag.domain.auth.OrgRecords;
import com.aoneng.rag.domain.auth.repository.OrgRepository;
import com.aoneng.rag.domain.auth.repository.UserRepository;
import com.aoneng.rag.domain.chat.po.KbChatMessageCitationPO;
import com.aoneng.rag.domain.chat.po.KbChatMessagePO;
import com.aoneng.rag.domain.chat.po.KbConversationPO;
import com.aoneng.rag.domain.chat.po.KbQaRecordPO;
import com.aoneng.rag.domain.chat.repository.ChatRepository;
import com.aoneng.rag.domain.kb.po.KbBasePO;
import com.aoneng.rag.domain.kb.po.KbDocumentPO;
import com.aoneng.rag.domain.kb.po.KbRetrievalEvalCasePO;
import com.aoneng.rag.domain.kb.repository.KbRepository;
import com.aoneng.rag.domain.auth.po.SysDeptPO;
import com.aoneng.rag.domain.auth.po.SysPermissionPO;
import com.aoneng.rag.domain.auth.po.SysRolePO;
import com.aoneng.rag.domain.auth.po.SysUserPO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 业务持久化门面 (Repository Facade)。
 * 兼容历史 controller/service 调用；内部委托给 {@link KbRepository} / {@link OrgRepository} /
 * {@link UserRepository} / {@link ChatRepository} / {@link AuditRepository} 等领域仓储。
 *
 * <p>控制器契约保持不变；该门面承担跨聚合的协调与 map→record 映射。</p>
 */
@Service
public class PlatformRepository {

    private static final String DEFAULT_PASSWORD = "$2a$10$eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.";

    private final UserRepository userRepository;
    private final OrgRepository orgRepository;
    private final KbRepository kbRepository;
    private final ChatRepository chatRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public PlatformRepository(UserRepository userRepository,
                              OrgRepository orgRepository,
                              KbRepository kbRepository,
                              ChatRepository chatRepository,
                              javax.sql.DataSource dataSource) {
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.kbRepository = kbRepository;
        this.chatRepository = chatRepository;
        this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    }

    // ============================================================
    // User / role / dept / permission
    // ============================================================

    public List<OrgRecords.UserRow> users() {
        return userRepository.findActiveUsers().stream().map(PlatformRepository::toUserRow).toList();
    }

    public Optional<OrgRecords.UserRow> userByUsername(String username) {
        return users().stream().filter(u -> username.equals(u.username())).findFirst();
    }

    public Optional<String> passwordHash(String username) {
        return userRepository.findPasswordHash(username).filter(s -> !s.isBlank());
    }

    public OrgRecords.UserRow createUser(Map<String, Object> req) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        long deptId = number(req.get("deptId"), -1L);
        requireDepartment(deptId);
        SysUserPO po = new SysUserPO();
        po.setEmployeeNo(stringOr(req.get("employeeNo"), "E" + suffix));
        po.setUsername(stringOr(req.get("username"), "user" + suffix));
        po.setPasswordHash(DEFAULT_PASSWORD);
        po.setRealName(stringOr(req.getOrDefault("realName", req.getOrDefault("name", "新员工")), "新员工"));
        po.setEmail(stringOrNull(req.get("email")));
        po.setPhone(stringOrNull(req.get("phone")));
        po.setDeptId(deptId);
        po.setStatus(valueOr(req.get("status"), 1));
        return toUserRow(userRepository.create(po));
    }

    public OrgRecords.UserRow updateUser(long id, Map<String, Object> req) {
        if (req.containsKey("deptId")) requireDepartment(number(req.get("deptId"), -1L));
        String realName = req.get("realName") == null && req.get("name") == null ? null
                : stringOr(req.getOrDefault("realName", req.get("name")), null);
        userRepository.updateProfile(id,
                realName,
                stringOrNull(req.get("email")),
                stringOrNull(req.get("phone")),
                numberOrNull(req.get("deptId")),
                valueOrNull(req.get("status")));
        return userById(id);
    }

    public void deleteUser(long id) {
        userRepository.softDelete(id);
    }

    public void resetPassword(long id) {
        userRepository.resetPassword(id, DEFAULT_PASSWORD);
    }

    public OrgRecords.UserRow userById(long id) {
        Optional<SysUserPO> po = userRepository.findById(id);
        if (po.isEmpty()) throw new NoSuchElementException("用户不存在");
        return toUserRow(po.get());
    }

    public List<OrgRecords.DepartmentRow> departments() {
        return orgRepository.findDepartmentOverviews().stream()
                .map(PlatformRepository::toDepartmentRow).toList();
    }

    public OrgRecords.DepartmentRow createDepartment(Map<String, Object> req) {
        String name = stringOr(req.get("name"), "").trim();
        if (name.isBlank()) throw new IllegalArgumentException("部门名称不能为空");
        long parentId = number(req.get("parentId"), 0L);
        if (parentId != 0) requireDepartment(parentId);
        String ancestors = parentId == 0 ? "0," : orgRepository.findAncestors(parentId) + parentId + ",";
        SysDeptPO po = new SysDeptPO();
        po.setParentId(parentId);
        po.setName(name);
        po.setAncestors(ancestors);
        po.setSort(valueOr(req.get("sort"), 0));
        po.setStatus(valueOr(req.get("status"), 1));
        long id = orgRepository.createDepartment(po.getParentId(), po.getName(),
                po.getAncestors(), po.getSort(), po.getStatus());
        return departmentById(id);
    }

    public OrgRecords.DepartmentRow updateDepartment(long id, Map<String, Object> req) {
        if (id == number(req.get("parentId"), -1L)) {
            throw new IllegalArgumentException("部门不能设置为自身的上级");
        }
        long parentId = number(req.get("parentId"), 0L);
        if (parentId != 0) requireDepartment(parentId);
        if (parentId != 0 && orgRepository.countDescendants(parentId, id) > 0) {
            throw new IllegalArgumentException("不能将部门移动到其下级部门中");
        }
        String ancestors = parentId == 0 ? "0," : orgRepository.findAncestors(parentId) + parentId + ",";
        orgRepository.updateDepartment(id,
                req.get("name") == null ? null : String.valueOf(req.get("name")).trim(),
                parentId,
                ancestors,
                valueOrNull(req.get("sort")),
                valueOrNull(req.get("status")));
        return departmentById(id);
    }

    public void deleteDepartment(long id) {
        int children = departments().stream().filter(d -> d.parentId() == id).toList().size();
        Integer usersCount = departments().stream()
                .filter(d -> d.id() == id)
                .map(OrgRecords.DepartmentRow::userCount)
                .findFirst().orElse(0);
        if (children > 0) throw new IllegalStateException("部门下存在子部门，无法删除");
        if (usersCount > 0) throw new IllegalStateException("部门下存在员工，无法删除");
        orgRepository.hardDeleteDepartment(id);
    }

    public OrgRecords.DepartmentRow departmentById(long id) {
        List<OrgRecords.DepartmentRow> matches = departments().stream().filter(d -> d.id() == id).toList();
        if (matches.isEmpty()) throw new NoSuchElementException("部门不存在");
        return matches.get(0);
    }

    public List<OrgRecords.RoleRow> roles() {
        return orgRepository.findRoleOverviews().stream().map(PlatformRepository::toRoleRow).toList();
    }

    public OrgRecords.RoleRow createRole(Map<String, Object> req) {
        String code = stringOr(req.get("code"), "").toUpperCase();
        String name = stringOr(req.get("name"), "").trim();
        if (code.isBlank() || !code.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new IllegalArgumentException("角色编码须为 3-64 位大写字母、数字或下划线");
        }
        if (name.isBlank()) throw new IllegalArgumentException("请输入角色名称");
        if (orgRepository.countByCode(code) > 0) throw new IllegalArgumentException("角色编码已存在");
        SysRolePO po = new SysRolePO();
        po.setCode(code);
        po.setName(name);
        po.setRemark(stringOrNull(req.get("remark")));
        po.setStatus(valueOr(req.get("status"), 1));
        long id = orgRepository.createRole(po.getCode(), po.getName(), po.getRemark(), po.getStatus());
        return role(id);
    }

    public OrgRecords.RoleRow updateRole(long id, Map<String, Object> req) {
        ensureRole(id);
        String code = req.containsKey("code") ? stringOr(req.get("code"), "").toUpperCase() : null;
        if (code != null && !code.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new IllegalArgumentException("角色编码须为 3-64 位大写字母、数字或下划线");
        }
        if (code != null && orgRepository.countByCodeExcluding(code, id) > 0) {
            throw new IllegalArgumentException("角色编码已存在");
        }
        String name = req.containsKey("name") ? stringOr(req.get("name"), "") : null;
        if (name != null && name.isBlank()) throw new IllegalArgumentException("请输入角色名称");
        orgRepository.updateRole(id, code, name, stringOrNull(req.get("remark")), valueOrNull(req.get("status")));
        return role(id);
    }

    public void deleteRole(long id) {
        ensureRole(id);
        if (orgRepository.countUsersWithRole(id) > 0) {
            throw new IllegalStateException("角色已关联用户，无法删除");
        }
        orgRepository.deleteRoleMenus(id);
        orgRepository.deleteRole(id);
    }

    public List<Long> roleMenuIds(long id) {
        ensureRole(id);
        return orgRepository.findMenuIdsByRoleId(id);
    }

    public void updateRoleMenus(long id, Map<String, Object> req) {
        ensureRole(id);
        List<Long> ids = values(req.get("menuIds"));
        if (!ids.isEmpty() && orgRepository.countRolesWithMenu(ids) != ids.size()) {
            throw new IllegalArgumentException("包含不存在的菜单");
        }
        orgRepository.deleteRoleMenus(id);
        for (Long menuId : ids) orgRepository.insertRoleMenu(id, menuId);
    }

    public List<OrgRecords.MenuRow> menus() {
        return orgRepository.findMenuOverviews().stream().map(PlatformRepository::toMenuRow).toList();
    }

    public OrgRecords.MenuRow createMenu(Map<String, Object> req) {
        String name = stringOr(req.get("name"), "").trim();
        if (name.isBlank()) throw new IllegalArgumentException("请输入菜单名称");
        long parentId = number(req.get("parentId"), 0);
        if (parentId != 0) ensureMenu(parentId);
        String perms = nullableText(req.get("perms"));
        ensurePermissionCode(perms, 0);
        SysPermissionPO po = new SysPermissionPO();
        po.setParentId(parentId);
        po.setName(name);
        po.setPerms(perms);
        po.setType(1);
        po.setPath(nullableText(req.get("path")));
        po.setSort(valueOr(req.get("sort"), 0));
        po.setStatus(valueOr(req.get("status"), 1));
        long id = orgRepository.createMenu(parentId, name, perms,
                nullableText(req.get("path")), po.getSort(), po.getStatus());
        return menu(id);
    }

    public OrgRecords.MenuRow updateMenu(long id, Map<String, Object> req) {
        ensureMenu(id);
        long parentId = number(req.get("parentId"), 0);
        if (id == parentId) throw new IllegalArgumentException("菜单不能设置为自身的上级");
        if (parentId != 0) ensureMenu(parentId);
        if (parentId != 0 && orgRepository.countMenuDescendants(parentId, id) > 0) {
            throw new IllegalArgumentException("不能将菜单移动到其下级菜单中");
        }
        String name = req.containsKey("name") ? stringOr(req.get("name"), "") : null;
        if (name != null && name.isBlank()) throw new IllegalArgumentException("请输入菜单名称");
        String perms = req.containsKey("perms") ? nullableText(req.get("perms")) : null;
        ensurePermissionCode(perms, id);
        orgRepository.updateMenu(id, parentId, name, perms,
                stringOrNull(req.get("path")),
                valueOrNull(req.get("sort")),
                valueOrNull(req.get("status")));
        return menu(id);
    }

    public void deleteMenu(long id) {
        ensureMenu(id);
        if (orgRepository.countMenuChildren(id) > 0) throw new IllegalStateException("菜单下存在子菜单，无法删除");
        if (orgRepository.countMenuRoles(id) > 0) throw new IllegalStateException("菜单已分配给角色，无法删除");
        orgRepository.hardDeleteMenu(id);
    }

    public List<Long> userMenuIds(String username) {
        Optional<SysUserPO> user = userRepository.findByUsername(username);
        if (user.isEmpty()) return List.of();
        return orgRepository.findUserMenuIds(user.get().getId());
    }

    // ============================================================
    // Knowledge base / documents / chunks
    // ============================================================

    public List<Map<String, Object>> bases() {
        return kbRepository.findAllBases();
    }

    public KbScope kbScope(String username) {
        Map<String, Object> row = userRepository.findKbScope(username);
        if (row == null || row.isEmpty()) {
            throw new IllegalArgumentException("Current user does not exist or is disabled");
        }
        long userId = ((Number) row.get("id")).longValue();
        Object deptIdRaw = row.get("deptId");
        Long deptId = deptIdRaw instanceof Number ? ((Number) deptIdRaw).longValue() : null;
        return new KbScope(userId, deptId, boolOf(row.get("admin")),
                boolOf(row.get("kbAccess")), boolOf(row.get("kbManager")));
    }

    public List<Map<String, Object>> accessibleBases(KbScope scope) {
        return bases().stream().filter(base -> canReadBase(base, scope))
                .peek(base -> enrichBaseAccess(base, scope)).toList();
    }

    public List<Long> accessibleBaseIds(KbScope scope) {
        return accessibleBases(scope).stream().map(base -> ((Number) base.get("id")).longValue()).toList();
    }

    public boolean canReadBase(long id, KbScope scope) {
        try {
            return canReadBase(base(id), scope);
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean canManageBase(long id, KbScope scope) {
        try {
            return canManageBase(base(id), scope);
        } catch (Exception ignored) {
            return false;
        }
    }

    public Map<String, Object> baseForScope(long id, KbScope scope) {
        Map<String, Object> result = base(id);
        if (!canReadBase(result, scope)) throw new SecurityException("无权访问该知识库");
        enrichBaseAccess(result, scope);
        return result;
    }

    public long createBase(Map<String, Object> r, KbScope scope) {
        requireKbAccess(scope);
        String visibility = String.valueOf(r.getOrDefault("visibility", "DEPT")).toUpperCase();
        if (!Set.of("PRIVATE", "DEPT", "ORG", "PUBLIC").contains(visibility)) {
            throw new IllegalArgumentException("知识库可见范围不合法");
        }
        if (("ORG".equals(visibility) || "PUBLIC".equals(visibility)) && !scope.admin()) {
            throw new SecurityException("仅系统管理员可创建组织级或公开知识库");
        }
        Long deptId = "DEPT".equals(visibility) ? scope.deptId() : null;
        if ("DEPT".equals(visibility) && deptId == null) {
            throw new IllegalArgumentException("当前用户未分配部门，无法创建部门知识库");
        }
        KbBasePO po = new KbBasePO();
        po.setName(stringOr(r.get("name"), "未命名知识库"));
        po.setDescription(stringOr(r.get("description"), ""));
        po.setCategory(stringOr(r.get("category"), ""));
        po.setVisibility(visibility);
        po.setOwnerId(scope.userId());
        po.setDeptId(deptId);
        int chunkSize = normalizedParentChunkTokens(r.get("chunkSize"), 1_200);
        int chunkOverlap = normalizedParentOverlapTokens(r.get("chunkOverlap"), chunkSize, 64);
        po.setChunkSize(chunkSize);
        po.setChunkOverlap(chunkOverlap);
        long id = kbRepository.createBase(po.getName(), po.getDescription(), po.getCategory(),
                po.getVisibility(), po.getOwnerId(), po.getDeptId(), chunkSize, chunkOverlap);
        if (deptId != null) kbRepository.insertAllowedDept(id, deptId);
        return id;
    }

    public Map<String, Object> base(long id) {
        Map<String, Object> result = kbRepository.findBaseById(id);
        if (result == null) throw new IllegalArgumentException("知识库不存在");
        return result;
    }

    public void updateBase(long id, Map<String, Object> r, KbScope scope) {
        Map<String, Object> existing = base(id);
        String visibility = r.containsKey("visibility") ? String.valueOf(r.get("visibility")).toUpperCase()
                : String.valueOf(existing.get("visibility"));
        if (!Set.of("PRIVATE", "DEPT", "ORG", "PUBLIC").contains(visibility)) {
            throw new IllegalArgumentException("知识库可见范围不合法");
        }
        if (("ORG".equals(visibility) || "PUBLIC".equals(visibility)) && !scope.admin()) {
            throw new SecurityException("仅系统管理员可设置组织级或公开范围");
        }
        Long deptId;
        if ("DEPT".equals(visibility)) {
            deptId = scope.admin() ? numberOrNull(existing, "deptId") : scope.deptId();
            // Older administrator-created department knowledge bases may not have
            // a legacy dept_id. Reuse an existing allowed department as the
            // compatibility owner while the junction table remains authoritative.
            if (scope.admin() && deptId == null) {
                List<Long> allowed = allowedDepartmentIds(id);
                if (!allowed.isEmpty()) deptId = allowed.get(0);
            }
        } else {
            deptId = null;
        }
        if ("DEPT".equals(visibility) && deptId == null) {
            throw new IllegalArgumentException("部门知识库必须归属有效部门");
        }
        Integer chunkSize = null;
        Integer chunkOverlap = null;
        if (r.containsKey("chunkSize") || r.containsKey("chunkOverlap")) {
            int effectiveSize = r.containsKey("chunkSize")
                    ? normalizedParentChunkTokens(r.get("chunkSize"), 1_200)
                    : normalizedParentChunkTokens(existing.get("chunkSize"), 1_200);
            int effectiveOverlap = r.containsKey("chunkOverlap")
                    ? normalizedParentOverlapTokens(r.get("chunkOverlap"), effectiveSize, 64)
                    : normalizedParentOverlapTokens(existing.get("chunkOverlap"), effectiveSize, 64);
            chunkSize = effectiveSize;
            chunkOverlap = effectiveOverlap;
        }
        kbRepository.updateBase(id,
                stringOrNull(r.get("name")),
                stringOrNull(r.get("description")),
                visibility,
                deptId, chunkSize, chunkOverlap);
        if ("DEPT".equals(visibility) && allowedDepartmentIds(id).isEmpty()) {
            kbRepository.insertAllowedDept(id, deptId);
        }
    }

    private int normalizedParentChunkTokens(Object value, int fallback) {
        int size = value instanceof Number number ? number.intValue() : fallback;
        if (size < 128 || size > 10_000) {
            throw new IllegalArgumentException("父块 token 数必须在 128 到 10000 之间");
        }
        return size;
    }

    private int normalizedParentOverlapTokens(Object value, int chunkSize, int fallback) {
        int overlap = value instanceof Number number ? number.intValue() : fallback;
        if (overlap < 0 || overlap > chunkSize / 2) {
            throw new IllegalArgumentException("父块重叠 token 数必须在 0 到父块大小一半之间");
        }
        return overlap;
    }

    public List<Long> allowedDepartmentIds(long kbId) {
        return kbRepository.findAllowedDepartmentIds(kbId);
    }

    public void updateAllowedDepartments(long kbId, List<Long> deptIds, KbScope scope) {
        if (!scope.admin()) throw new SecurityException("仅系统管理员可配置知识库部门权限");
        base(kbId);
        List<Long> uniqueIds = deptIds == null ? List.of() : deptIds.stream().distinct().toList();
        if (!uniqueIds.isEmpty() && kbRepository.countActiveDepts(uniqueIds) != uniqueIds.size()) {
            throw new IllegalArgumentException("包含不存在或已停用的部门");
        }
        if ("DEPT".equals(String.valueOf(base(kbId).get("visibility"))) && uniqueIds.isEmpty()) {
            throw new IllegalArgumentException("部门知识库至少需要授权一个部门");
        }
        kbRepository.deleteAllowedDepts(kbId);
        for (Long deptId : uniqueIds) kbRepository.insertAllowedDept(kbId, deptId);
    }

    public void deleteBase(long id) {
        kbRepository.softDeleteBase(id);
    }

    public long createDoc(long kbId, String name, String type, long size, String key, String etag, long uploader) {
        KbDocumentPO po = new KbDocumentPO();
        po.setKbId(kbId);
        po.setFileName(name);
        po.setFileType(type);
        po.setFileSize(size);
        po.setObjectKey(key);
        po.setUploaderId(uploader);
        return kbRepository.createDocument(po.getKbId(), po.getFileName(), po.getFileType(),
                po.getFileSize(), po.getObjectKey(), etag, po.getUploaderId());
    }

    public Map<String, Object> doc(long id) {
        Map<String, Object> result = kbRepository.findDocumentById(id);
        if (result == null) throw new IllegalArgumentException("文档不存在");
        return result;
    }

    public List<Map<String, Object>> docs(long kbId) {
        return kbRepository.findDocumentsByKb(kbId);
    }

    public void status(long id, String parse, String chunk, int count, String error) {
        kbRepository.updateDocumentStatus(id, parse, chunk, count, error);
    }

    public boolean initializeDocumentFingerprint(long id, String etag, long size) {
        return kbRepository.initializeDocumentFingerprint(id, etag, size);
    }

    public boolean markDocumentChanged(long id, String etag, long size) {
        return kbRepository.markDocumentChanged(id, etag, size);
    }

    public void touchDocumentScan(long id) {
        kbRepository.touchDocumentScan(id);
    }

    public void enqueueIndexTask(long docId, int version, String operation) {
        kbRepository.enqueueIndexTask(docId, version, operation);
    }

    public void updateIndexTask(long docId, int version, String operation, String status, String error) {
        kbRepository.updateIndexTask(docId, version, operation, status, error);
    }

    public int resetStaleIndexTasks(int timeoutMinutes) {
        return kbRepository.resetStaleIndexTasks(timeoutMinutes);
    }

    public List<Map<String, Object>> dueIndexTasks(int limit) {
        return kbRepository.dueIndexTasks(limit);
    }

    public void deleteDoc(long id) {
        kbRepository.softDeleteDocument(id);
    }

    @Transactional
    public void clearChunks(long docId) {
        kbRepository.deleteChunksByDoc(docId);
    }

    public long saveBaseChunk(long docId, long kbId, int seq, String content, Integer pageNo,
                              int tokenCount, String blockType, String metadata, boolean tokenEstimated) {
        return kbRepository.saveBaseChunk(docId, kbId, seq, content, pageNo, tokenCount, blockType, metadata, tokenEstimated);
    }

    public void updateParentEmbeddingId(long parentId, String embeddingId) {
        kbRepository.updateParentEmbeddingId(parentId, embeddingId);
    }

    public long saveParentChunk(long docId, long kbId, int seq, String content, Integer pageNo, int tokenCount,
                                String metadata, boolean tokenEstimated) {
        return kbRepository.saveParentChunk(docId, kbId, seq, content, pageNo, tokenCount, metadata, tokenEstimated);
    }

    public long saveChunk(long docId, long kbId, long parentId, int seq, String content, Integer pageNo,
                          int tokenCount, boolean tokenEstimated) {
        return kbRepository.saveChunk(docId, kbId, parentId, seq, content, pageNo, tokenCount, tokenEstimated);
    }

    public void updateChunkEmbeddingId(long chunkId, String embeddingId) {
        kbRepository.updateChunkEmbeddingId(chunkId, embeddingId);
    }

    public void updateChunkMetadata(long chunkId, String metadata) {
        kbRepository.updateChunkMetadata(chunkId, metadata);
    }

    public Map<String, Object> parentChunk(long parentId) {
        return kbRepository.findParentChunk(parentId);
    }

    public List<Map<String, Object>> parentChunks(long docId) {
        return kbRepository.findParentChunksByDoc(docId);
    }

    public List<Map<String, Object>> keywordChunks(List<String> terms, List<Long> allowedKbIds, int limit) {
        if (terms == null || terms.isEmpty() || allowedKbIds == null || allowedKbIds.isEmpty()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return kbRepository.keywordSearch(terms, allowedKbIds, safeLimit).stream()
                .map(row -> keywordChunk(row, terms))
                .sorted(Comparator.comparingDouble(row -> -((Number) row.get("keywordScore")).doubleValue()))
                .limit(safeLimit)
                .toList();
    }

    private Map<String, Object> keywordChunk(Map<String, Object> row, List<String> terms) {
        String content = String.valueOf(row.getOrDefault("content", "")).toLowerCase();
        String fileName = String.valueOf(row.getOrDefault("file_name", "")).toLowerCase();
        int score = 0;
        for (String term : terms) {
            String normalized = term.toLowerCase();
            if (content.contains(normalized)) score += 2;
            if (fileName.contains(normalized)) score += 3;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("score", (double) score);
        result.put("keywordScore", score);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunk_id", row.get("id"));
        if (row.get("parentId") != null) payload.put("parent_id", row.get("parentId"));
        payload.put("doc_id", row.get("docId"));
        payload.put("kb_id", row.get("kbId"));
        payload.put("content", row.get("content"));
        payload.put("file_name", row.get("file_name"));
        if (row.get("pageNo") != null) payload.put("page_no", row.get("pageNo"));
        result.put("payload", payload);
        return result;
    }

    // ============================================================
    // Audit / QA / conversation
    // ============================================================

    public List<Map<String, Object>> retrievalEvalCases() {
        return chatRepository.findAllEvalCases();
    }

    public Map<String, Object> retrievalEvalCase(long id) {
        Map<String, Object> result = chatRepository.findEvalCaseById(id);
        if (result == null) throw new NoSuchElementException("检索评测用例不存在");
        addEvalCaseAggregates(result);
        return result;
    }

    public Map<String, Object> createRetrievalEvalCase(Map<String, Object> request) {
        String question = String.valueOf(request.getOrDefault("question", "")).trim();
        if (question.isBlank()) throw new IllegalArgumentException("评测问题不能为空");
        List<Long> expected = values(request.get("expectedChunkIds"));
        KbRetrievalEvalCasePO po = new KbRetrievalEvalCasePO();
        po.setQuestion(question);
        po.setExpectedChunkIds(arrayLiteral(expected));
        po.setReferenceAnswer(stringOrNull(request.get("referenceAnswer")));
        po.setEnabled(request.get("enabled") == null ? Boolean.TRUE
                : request.get("enabled") instanceof Boolean b ? b
                : Boolean.parseBoolean(String.valueOf(request.get("enabled"))));
        po.setNote(stringOrNull(request.get("note")));
        long id = chatRepository.createEvalCase(question, arrayLiteral(expected), po.getReferenceAnswer(),
                po.getEnabled(), po.getNote());
        return retrievalEvalCase(id);
    }

    private void addEvalCaseAggregates(Map<String, Object> row) {
        Object raw = row.get("expectedChunkIdsRaw");
        if (raw instanceof Array array) {
            row.put("expectedChunkIds", arrayValues(array));
        } else if (raw == null) {
            row.put("expectedChunkIds", List.of());
        }
    }

    public record ChatTurn(long conversationId, long userMessageId, long assistantMessageId) {
    }

    public Map<String, Object> conversations(long userId, String cursor, int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, 50));
        Timestamp cursorTime = null;
        Long cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("会话分页游标无效");
            }
            Map<String, Object> found = chatRepository.findConversationById(cursorId, userId);
            if (found == null) throw new IllegalArgumentException("会话分页游标无效");
            Object lm = found.get("lastMessageAt");
            cursorTime = lm instanceof Timestamp ts ? ts : (lm == null ? null : Timestamp.from(((OffsetDateTime) lm).toInstant()));
            if (cursorTime == null) cursorTime = new Timestamp(System.currentTimeMillis());
        }
        List<Map<String, Object>> rows = chatRepository.findConversations(userId,
                cursorTime == null ? null : cursorTime.toString(), cursorId, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows.remove(rows.size() - 1);
        String nextCursor = hasMore && !rows.isEmpty() ? String.valueOf(rows.get(rows.size() - 1).get("id")) : null;
        rows.forEach(this::enrichConversation);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", hasMore);
        return result;
    }

    public Map<String, Object> conversation(long userId, long conversationId) {
        Map<String, Object> result = chatRepository.findConversationById(conversationId, userId);
        if (result == null) throw new NoSuchElementException("会话不存在或无权访问");
        enrichConversation(result);
        return result;
    }

    @Transactional
    public Map<String, Object> createConversation(long userId, String title, List<Long> selectedKbIds) {
        KbConversationPO po = new KbConversationPO();
        po.setUserId(userId);
        po.setTitle(normalizeConversationTitle(title));
        po.setSelectedKbIds(arrayLiteral(selectedKbIds));
        long id = chatRepository.createConversation(userId, po.getTitle(), arrayLiteral(selectedKbIds));
        return conversation(userId, id);
    }

    @Transactional
    public Map<String, Object> updateConversationTitle(long userId, long conversationId, String title) {
        chatRepository.updateConversationTitle(conversationId, userId, normalizeConversationTitle(title));
        return conversation(userId, conversationId);
    }

    @Transactional
    public void deleteConversation(long userId, long conversationId) {
        int updated = chatRepository.softDeleteConversation(conversationId, userId);
        if (updated == 0) throw new NoSuchElementException("会话不存在或无权访问");
    }

    @Transactional
    public ChatTurn prepareChatTurn(long userId, Long requestedConversationId, String question, List<Long> permittedKbIds) {
        long conversationId;
        if (requestedConversationId == null) {
            KbConversationPO po = new KbConversationPO();
            po.setUserId(userId);
            po.setTitle(titleFromQuestion(question));
            po.setSelectedKbIds(arrayLiteral(permittedKbIds));
            conversationId = chatRepository.createConversation(userId, po.getTitle(), arrayLiteral(permittedKbIds));
        } else {
            conversationId = requestedConversationId;
            Long locked = chatRepository.lockConversation(conversationId, userId);
            if (locked == null) throw new NoSuchElementException("会话不存在或无权访问");
        }
        int nextSeq = chatRepository.nextSequenceNo(conversationId);
        long userMessageId = insertChatMessage(conversationId, nextSeq, "USER", question, "COMPLETED",
                permittedKbIds, null, null, null, true);
        long assistantMessageId = insertChatMessage(conversationId, nextSeq + 1, "ASSISTANT", "", "STREAMING",
                permittedKbIds, null, null, null, false);
        chatRepository.updateSelectedKbIds(conversationId, permittedKbIds.stream().mapToLong(Long::longValue).toArray());
        return new ChatTurn(conversationId, userMessageId, assistantMessageId);
    }

    private long insertChatMessage(long conversationId, int sequenceNo, String role, String content,
                                    String status, List<Long> sourceKbIds, String modelName,
                                    String errorMessage, Long qaRecordId, boolean completed) {
        KbChatMessagePO po = new KbChatMessagePO();
        po.setConversationId(conversationId);
        po.setSequenceNo(sequenceNo);
        po.setRole(role);
        po.setContent(content == null ? "" : content);
        po.setStatus(status);
        po.setSourceKbIds(arrayLiteral(sourceKbIds));
        po.setModelName(modelName);
        po.setErrorMessage(errorMessage);
        po.setQaRecordId(qaRecordId);
        po.setCompletedAt(completed ? OffsetDateTime.now() : null);
        return chatRepository.insertMessage(conversationId, sequenceNo, role, content == null ? "" : content,
                status, arrayLiteral(sourceKbIds), modelName, errorMessage, qaRecordId, completed);
    }

    public Map<String, Object> messages(long userId, long conversationId, String before, int pageSize) {
        conversation(userId, conversationId);
        int limit = Math.max(1, Math.min(pageSize, 50));
        Integer beforeSequence = null;
        if (before != null && !before.isBlank()) {
            long beforeId;
            try {
                beforeId = Long.parseLong(before);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("消息分页游标无效");
            }
            Integer seq = jdbc.query("SELECT sequence_no FROM kb_chat_message WHERE id = ? AND conversation_id = ?",
                    rs -> rs.next() ? rs.getInt(1) : null, beforeId, conversationId);
            if (seq == null) throw new IllegalArgumentException("消息分页游标无效");
            beforeSequence = seq;
        }
        List<Map<String, Object>> rows = chatRepository.findMessages(conversationId, beforeSequence, limit + 1);
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows.remove(rows.size() - 1);
        Collections.reverse(rows);
        for (Map<String, Object> row : rows) row.put("citations", messageCitations(((Number) row.get("id")).longValue()));
        String nextCursor = hasMore && !rows.isEmpty() ? String.valueOf(rows.get(0).get("id")) : null;
        rows.forEach(PlatformRepository::enrichMessage);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", hasMore);
        return result;
    }

    public String recentChatHistory(long userId, long conversationId) {
        conversation(userId, conversationId);
        List<Map<String, Object>> rows = chatRepository.findRecentHistory(conversationId, 8);
        Collections.reverse(rows);
        StringBuilder history = new StringBuilder();
        for (Map<String, Object> row : rows) {
            String content = String.valueOf(row.getOrDefault("content", ""));
            if (content.isBlank()) continue;
            String role = "USER".equals(row.get("role")) ? "用户" : "助手";
            history.append(role).append("：").append(content, 0, Math.min(content.length(), 2000)).append('\n');
        }
        return history.toString();
    }

    @Transactional
    public long completeChatTurn(ChatTurn turn, long userId, String question, String answer, List<Long> permittedKbIds,
                                 List<Long> retrievedChunkIds, List<Map<String, Object>> citations, int latencyMs) {
        return completeChatTurn(turn, userId, question, answer, permittedKbIds, retrievedChunkIds, citations, latencyMs, Map.of());
    }

    @Transactional
    public long completeChatTurn(ChatTurn turn, long userId, String question, String answer, List<Long> permittedKbIds,
                                 List<Long> retrievedChunkIds, List<Map<String, Object>> citations, int latencyMs,
                                 Map<String, Object> retrievalTrace) {
        KbQaRecordPO qa = new KbQaRecordPO();
        qa.setUserId(userId);
        qa.setKbIds(arrayLiteral(permittedKbIds));
        qa.setQuestion(question);
        qa.setAnswer(answer);
        qa.setRetrievedChunkIds(arrayLiteral(retrievedChunkIds));
        qa.setModelName("qwen-plus");
        qa.setLatencyMs(latencyMs);
        qa.setConversationId(turn.conversationId());
        long qaId = chatRepository.insertQaRecord(userId, arrayLiteral(permittedKbIds), question, answer,
                arrayLiteral(retrievedChunkIds), "qwen-plus", latencyMs, turn.conversationId());
        try {
            chatRepository.updateRerankScores(qaId,
                    objectMapper.writeValueAsString(retrievalTrace == null ? Map.of() : retrievalTrace));
        } catch (JsonProcessingException ignored) {
            // 遥测评分序列化失败不影响问答成功响应，仅静默忽略
        }
        long[] chunkIds = retrievedChunkIds == null ? new long[0]
                : retrievedChunkIds.stream().mapToLong(Long::longValue).toArray();
        chatRepository.completeAssistantMessage(turn.assistantMessageId(), turn.conversationId(),
                answer, chunkIds, "qwen-plus", latencyMs, qaId);
        for (int index = 0; index < citations.size(); index++) {
            Map<String, Object> citation = citations.get(index);
            KbChatMessageCitationPO po = new KbChatMessageCitationPO();
            po.setMessageId(turn.assistantMessageId());
            po.setChunkId(longValue(citation.get("chunkId")));
            po.setDocId(longValue(citation.get("docId")));
            po.setKbId(longValue(citation.get("kbId")));
            po.setFileName(String.valueOf(citation.getOrDefault("fileName", "知识库文档")));
            po.setSnippet(String.valueOf(citation.getOrDefault("snippet", "")));
            po.setRankNo(index + 1);
            po.setScore(doubleValue(citation.get("score")));
            chatRepository.insertCitation(po.getMessageId(), po.getChunkId(), po.getDocId(),
                    po.getKbId(), po.getFileName(), po.getSnippet(),
                    po.getRankNo(), po.getScore());
        }
        chatRepository.updateAssistantMessageId(qaId, turn.assistantMessageId());
        chatRepository.touchLastMessage(turn.conversationId());
        return qaId;
    }

    @Transactional
    public void failChatTurn(ChatTurn turn, String message) {
        String safe = message == null || message.isBlank() ? "问答生成失败，请稍后重试"
                : message.substring(0, Math.min(message.length(), 500));
        chatRepository.updateFailure(turn.assistantMessageId(), turn.conversationId(), safe);
        chatRepository.touchLastMessage(turn.conversationId());
    }

    public Map<String, Object> qaRecords(long userId, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int limit = Math.max(1, Math.min(pageSize, 100));
        int offset = (normalizedPage - 1) * limit;
        List<Map<String, Object>> items = chatRepository.findQaRecords(userId, limit, offset);
        Integer total = chatRepository.countQaRecordsByUser(userId);
        return Map.of("items", items, "total", total == null ? 0 : total,
                "page", normalizedPage, "pageSize", limit);
    }

    public Map<String, Object> qaRecord(long userId, long recordId) {
        Map<String, Object> row = chatRepository.findQaRecordById(recordId, userId);
        if (row == null) throw new NoSuchElementException("问答记录不存在或无权访问");
        return row;
    }

    public boolean ownsQaRecord(long userId, long recordId) {
        return chatRepository.countQaRecordById(recordId, userId) > 0;
    }

    public void saveQa(long id, long user, String q, String a, int latency) {
        chatRepository.insertQaRecord(id, user, q, a, "qwen-plus", latency);
    }

    public void feedback(long record, long user, int rating, String comment) {
        chatRepository.upsertFeedback(record, user, rating, comment);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void requireDepartment(long id) {
        if (id <= 0 || orgRepository.countActiveById(id) == 0) {
            throw new IllegalArgumentException("部门不存在或已停用");
        }
    }

    private void requireKbAccess(KbScope scope) {
        if (!scope.admin() && !scope.kbAccess()) {
            throw new SecurityException("当前角色未获知识库访问授权");
        }
    }

    private boolean canReadBase(Map<String, Object> base, KbScope scope) {
        if (scope.admin()) return true;
        if (!scope.kbAccess()) return false;
        String visibility = String.valueOf(base.get("visibility"));
        if ("ORG".equals(visibility) || "PUBLIC".equals(visibility)) return departmentAllowed(base, scope);
        if ("PRIVATE".equals(visibility)) return numberOrNull(base, "ownerId") == scope.userId();
        return "DEPT".equals(visibility) && departmentAllowed(base, scope);
    }

    private boolean canManageBase(Map<String, Object> base, KbScope scope) {
        if (!canReadBase(base, scope)) return false;
        if (scope.admin() || numberOrNull(base, "ownerId") == scope.userId()) return true;
        return scope.kbManager() && "DEPT".equals(String.valueOf(base.get("visibility"))) && departmentAllowed(base, scope);
    }

    private void enrichBaseAccess(Map<String, Object> base, KbScope scope) {
        base.put("allowedDeptIds", allowedDepartmentIds(((Number) base.get("id")).longValue()));
        base.put("canManage", canManageBase(base, scope));
        base.put("canConfigureDepartments", scope.admin());
    }

    private boolean departmentAllowed(Map<String, Object> base, KbScope scope) {
        if (scope.deptId() == null) return false;
        List<Long> allowed = allowedDepartmentIds(((Number) base.get("id")).longValue());
        if (!allowed.isEmpty()) return allowed.contains(scope.deptId());
        String visibility = String.valueOf(base.get("visibility"));
        return !"DEPT".equals(visibility) || Objects.equals(numberOrNull(base, "deptId"), scope.deptId());
    }

    private void ensureRole(long id) {
        if (orgRepository.countById(id) == 0) throw new IllegalArgumentException("角色不存在");
    }

    private void ensureMenu(long id) {
        if (orgRepository.countMenuById(id) == 0) throw new IllegalArgumentException("菜单不存在");
    }

    private void ensurePermissionCode(String perms, long id) {
        if (perms != null && orgRepository.countByPerms(perms, id) > 0) {
            throw new IllegalArgumentException("权限标识已存在");
        }
    }

    private List<Map<String, Object>> messageCitations(long messageId) {
        List<Map<String, Object>> rows = chatRepository.findCitationsByMessage(messageId);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> citation : rows) {
            Long docId = citation.get("docId") == null ? null : ((Number) citation.get("docId")).longValue();
            Long kbId = citation.get("kbId") == null ? null : ((Number) citation.get("kbId")).longValue();
            String fileName = citation.get("fileName") == null ? "" : String.valueOf(citation.get("fileName")).trim();
            if (docId == null || kbId == null
                    || docId <= 0 || kbId <= 0
                    || fileName.isBlank()) continue;
            String key = docId + ":" + kbId + ":" + fileName;
            grouped.computeIfAbsent(key, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("chunkId", citation.get("chunkId"));
                value.put("docId", docId);
                value.put("kbId", kbId);
                value.put("fileName", fileName);
                value.put("snippet", citation.getOrDefault("snippet", ""));
                if (citation.get("score") != null) value.put("score", citation.get("score"));
                return value;
            });
        }
        return new ArrayList<>(grouped.values());
    }

    private void enrichConversation(Map<String, Object> row) {
        row.computeIfAbsent("title", ignored -> "新会话");
        Object raw = row.remove("selectedKbIdsRaw");
        row.put("selectedKbIds", raw == null ? List.of() : parseLongArrayLiteral(raw));
    }

    private static void enrichMessage(Map<String, Object> row) {
        Object src = row.remove("sourceKbIdsRaw");
        Object ret = row.remove("retrievedChunkIdsRaw");
        row.put("sourceKbIds", src == null ? List.of() : parseLongArrayLiteral(src));
        row.put("retrievedChunkIds", ret == null ? List.of() : parseLongArrayLiteral(ret));
        Object role = row.get("role");
        if (role != null) row.put("role", role.toString().toLowerCase());
    }

    private static List<Long> parseLongArrayLiteral(Object value) {
        if (value == null) return List.of();
        if (value instanceof Array array) return arrayValues(array);
        String text = String.valueOf(value);
        if (text.isBlank()) return List.of();
        String trimmed = text.replaceAll("[{}\\s]", "");
        if (trimmed.isBlank()) return List.of();
        List<Long> result = new ArrayList<>();
        for (String part : trimmed.split(",")) {
            if (part.isBlank()) continue;
            try {
                result.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
                // 跳过格式异常的数组元素，保证解析链路的健壮性
            }
        }
        return result;
    }

    private static String arrayLiteral(List<Long> values) {
        if (values == null || values.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Long value : values) {
            if (!first) sb.append(',');
            sb.append(value);
            first = false;
        }
        return sb.append('}').toString();
    }

    private List<Long> values(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast)
                .map(Number::longValue).distinct().toList();
    }

    private static List<Long> arrayValues(Array value) {
        if (value == null) return List.of();
        try {
            Object raw = value.getArray();
            if (!(raw instanceof Object[] values)) return List.of();
            return Arrays.stream(values).filter(Objects::nonNull)
                    .map(item -> ((Number) item).longValue()).toList();
        } catch (SQLException e) {
            throw new IllegalStateException("无法读取会话关联数据", e);
        }
    }

    private long number(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private Long numberOrNull(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private Long numberOrNull(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.longValue() : null;
    }

    private int valueOr(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }

    private Integer valueOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        if (value instanceof Boolean b) return b ? 1 : 0;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String stringOr(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullableText(Object value) {
        String text = stringOr(value, "").trim();
        return text.isBlank() ? null : text;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer positiveInt(Object value) {
        Long number = longValue(value);
        return number != null && number > 0 && number <= Integer.MAX_VALUE ? number.intValue() : null;
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean boolOf(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return false;
    }

    private String normalizeConversationTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("会话标题不能为空");
        return normalized.substring(0, Math.min(normalized.length(), 120));
    }

    private String titleFromQuestion(String question) {
        String normalized = question.trim().replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(normalized.length(), 40));
    }

    // Row mappers
    private static OrgRecords.UserRow toUserRow(SysUserPO po) {
        return new OrgRecords.UserRow(
                po.getId(),
                po.getEmployeeNo(),
                po.getUsername(),
                po.getRealName(),
                po.getDeptId(),
                null,
                null,
                po.getStatus());
    }

    private static OrgRecords.DepartmentRow toDepartmentRow(Map<String, Object> row) {
        return new OrgRecords.DepartmentRow(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("parentId")).longValue(),
                (String) row.get("name"),
                (String) row.get("ancestors"),
                row.get("sort") instanceof Number n ? n.intValue() : null,
                row.get("status") instanceof Number n ? n.intValue() : null,
                row.get("userCount") instanceof Number n ? n.intValue() : 0,
                row.get("childCount") instanceof Number n ? n.intValue() : 0);
    }

    private static OrgRecords.RoleRow toRoleRow(Map<String, Object> row) {
        return new OrgRecords.RoleRow(
                ((Number) row.get("id")).longValue(),
                (String) row.get("code"),
                (String) row.get("name"),
                (String) row.get("remark"),
                row.get("status") instanceof Number n ? n.intValue() : null,
                row.get("userCount") instanceof Number n ? n.intValue() : 0,
                row.get("menuCount") instanceof Number n ? n.intValue() : 0);
    }

    private static OrgRecords.MenuRow toMenuRow(Map<String, Object> row) {
        return new OrgRecords.MenuRow(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("parentId")).longValue(),
                (String) row.get("name"),
                (String) row.get("perms"),
                (String) row.get("path"),
                row.get("sort") instanceof Number n ? n.intValue() : null,
                row.get("status") instanceof Number n ? n.intValue() : null,
                row.get("childCount") instanceof Number n ? n.intValue() : 0,
                row.get("roleCount") instanceof Number n ? n.intValue() : 0);
    }

    private OrgRecords.RoleRow role(long id) {
        List<OrgRecords.RoleRow> matches = roles().stream().filter(r -> r.id() == id).toList();
        if (matches.isEmpty()) throw new NoSuchElementException("角色不存在");
        return matches.get(0);
    }

    private OrgRecords.MenuRow menu(long id) {
        List<OrgRecords.MenuRow> matches = menus().stream().filter(m -> m.id() == id).toList();
        if (matches.isEmpty()) throw new NoSuchElementException("菜单不存在");
        return matches.get(0);
    }
}
