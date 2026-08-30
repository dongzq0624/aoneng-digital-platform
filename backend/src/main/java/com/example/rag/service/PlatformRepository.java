package com.example.rag.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Array;
import java.sql.SQLException;
import java.util.*;

@Repository
public class PlatformRepository {
    private static final String DEFAULT_PASSWORD = "$2a$10$eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.";
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlatformRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        ensureSeed();
    }

    private void ensureSeed() {
        try {
            jdbc.update("update sys_dept set name='总经办' where id=1 and name in ('产品中心','Product Center')");
            jdbc.update("update sys_dept set name='技术支持中心' where id=2 and name in ('Technology Center','技术中心')");
            jdbc.update("update sys_dept set name='营销中心' where id=3 and name in ('Human Resources','人力资源部')");
            jdbc.update("insert into sys_dept(id,parent_id,name,ancestors) values(1,0,'总经办','0,'),(2,0,'技术支持中心','0,'),(3,0,'营销中心','0,'),(4,0,'研发中心','0,'),(5,0,'制造中心','0,'),(6,0,'海外销售部','0,'),(7,0,'职能中心','0,'),(8,0,'工艺品质中心','0,'),(9,0,'行政中心','0,') on conflict (id) do nothing");
            if (count("sys_user") == 0)
                jdbc.update("insert into sys_user(id,employee_no,username,password_hash,real_name,dept_id) values(1,'E2024018','admin',?,'管理员',1),(2,'E2023012','dongzhiqiang',?,'董志强',4),(3,'E2022056','yuchunyi',?,'喻春意',3),(4,'E2021008','sunkeping',?,'孙可平',9)", DEFAULT_PASSWORD, DEFAULT_PASSWORD, DEFAULT_PASSWORD, DEFAULT_PASSWORD);
            if (count("sys_role") == 0)
                jdbc.update("insert into sys_role(id,code,name) values(1,'ROLE_ADMIN','系统管理员'),(2,'ROLE_KB_MANAGER','知识库管理员'),(3,'ROLE_EMPLOYEE','普通员工')");
            jdbc.update("insert into sys_user_role(user_id,role_id) values(1,1),(2,2),(3,3),(4,3) on conflict do nothing");
            if (count("sys_permission") == 0) {
                jdbc.update("insert into sys_permission(id,parent_id,name,perms,type,path,sort,status) values " +
                        "(1,0,'工作台','dashboard:view',1,'/dashboard',10,1)," +
                        "(2,0,'知识库管理','kb:view',1,'/kb',20,1)," +
                        "(3,0,'智能问答','rag:chat',1,'/chat',30,1)," +
                        "(4,0,'系统管理','system:view',1,null,40,1)," +
                        "(5,4,'用户管理','system:user:view',1,'/system/users',10,1)," +
                        "(6,4,'部门管理','system:dept:view',1,'/system/depts',20,1)," +
                        "(7,4,'角色管理','system:role:view',1,'/system/roles',30,1)," +
                        "(8,4,'菜单管理','system:menu:view',1,'/system/menus',40,1)," +
                        "(9,0,'审计日志','audit:view',1,'/audit',50,1)");
            }
            jdbc.update("update sys_permission set name='用户管理' where id=5 and perms='system:user:view' and path='/system/users'");
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select 1,id from sys_permission on conflict do nothing");
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select 2,id from sys_permission where id in (1,2,3) on conflict do nothing");
            jdbc.update("insert into sys_role_permission(role_id,permission_id) select 3,id from sys_permission where id in (1,2,3) on conflict do nothing");
            if (count("kb_knowledge_base") == 0)
                jdbc.update("insert into kb_knowledge_base(id,name,description,category,visibility,owner_id,dept_id,chunk_size,chunk_overlap) values(1,'产品与设计','产品方法论、设计规范与研发协作流程','产品','DEPT',1,4,512,64),(2,'员工制度与福利','入职、考勤、福利与员工服务制度','人事','ORG',1,null,512,64),(3,'客户成功案例库','客户案例、解决方案与行业最佳实践','案例','PRIVATE',1,3,512,64),(4,'品牌内容资产','品牌视觉、内容模板与对外传播资料','品牌','PUBLIC',1,null,512,64)");
            if (count("audit_log") == 0)
                jdbc.update("insert into audit_log(user_id,username,action,module,detail,result) values(1,'管理员','LOGIN','系统管理','{\"message\":\"网页登录\"}'::jsonb,1),(2,'董志强','DOC_UPLOAD','知识库','{\"file\":\"设计规范v2.3.pdf\"}'::jsonb,1),(3,'喻春意','QA_ASK','智能问答','{\"question\":\"产品发布流程\"}'::jsonb,1),(4,'孙可平','DOC_DELETE','知识库','{\"documentId\":182}'::jsonb,0)");
            jdbc.queryForObject("select setval(pg_get_serial_sequence('sys_dept','id'), coalesce((select max(id) from sys_dept), 1))", Long.class);
            jdbc.queryForObject("select setval(pg_get_serial_sequence('sys_user','id'), coalesce((select max(id) from sys_user), 1))", Long.class);
            jdbc.queryForObject("select setval(pg_get_serial_sequence('sys_role','id'), coalesce((select max(id) from sys_role), 1))", Long.class);
            jdbc.queryForObject("select setval(pg_get_serial_sequence('sys_permission','id'), coalesce((select max(id) from sys_permission), 1))", Long.class);
            jdbc.queryForObject("select setval(pg_get_serial_sequence('kb_knowledge_base','id'), coalesce((select max(id) from kb_knowledge_base), 1))", Long.class);
        } catch (Exception ignored) { /* schema may not be ready during early startup */ }
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }

    public List<Map<String, Object>> users() {
        return jdbc.queryForList("select u.id,u.employee_no as employeeNo,u.username,u.real_name as realName,u.dept_id as deptId,d.name as dept,coalesce((select r.name from sys_role r join sys_user_role ur on ur.role_id=r.id where ur.user_id=u.id limit 1),'普通员工') as role,u.status from sys_user u left join sys_dept d on d.id=u.dept_id where u.deleted=false order by u.id");
    }

    public Optional<Map<String, Object>> userByUsername(String username) {
        return users().stream().filter(u -> username.equals(u.get("username"))).findFirst();
    }

    public Optional<String> passwordHash(String username) {
        return jdbc.query("select password_hash from sys_user where username=? and deleted=false", rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(), username);
    }

    public Map<String, Object> createUser(Map<String, Object> req) {
        String suffix = String.valueOf(System.currentTimeMillis()).substring(7);
        long deptId = number(req.get("deptId"), -1L);
        requireDepartment(deptId);
        jdbc.queryForObject("select setval('sys_user_id_seq', (select coalesce(max(id),0)+1 from sys_user), false)", Long.class);
        long id = jdbc.queryForObject("insert into sys_user(employee_no,username,password_hash,real_name,email,phone,dept_id,status) values(?,?,?,?,?,?,?,?) returning id", Long.class,
                req.getOrDefault("employeeNo", "E" + suffix), req.getOrDefault("username", "user" + suffix), DEFAULT_PASSWORD, req.getOrDefault("realName", req.getOrDefault("name", "新员工")), req.get("email"), req.get("phone"), deptId, req.getOrDefault("status", 1));
        return users().stream().filter(x -> ((Number) x.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    public Map<String, Object> updateUser(long id, Map<String, Object> req) {
        if (req.containsKey("deptId")) requireDepartment(number(req.get("deptId"), -1L));
        jdbc.update("update sys_user set real_name=coalesce(?,real_name),email=coalesce(?,email),phone=coalesce(?,phone),dept_id=coalesce(?,dept_id),status=coalesce(?,status),updated_at=now() where id=? and deleted=false", req.getOrDefault("realName", req.get("name")), req.get("email"), req.get("phone"), req.get("deptId"), req.get("status"), id);
        return users().stream().filter(x -> ((Number) x.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    public void deleteUser(long id) {
        jdbc.update("update sys_user set deleted=true,updated_at=now() where id=?", id);
    }

    public void resetPassword(long id) {
        jdbc.update("update sys_user set password_hash=?,updated_at=now() where id=? and deleted=false", DEFAULT_PASSWORD, id);
    }

    public List<Map<String, Object>> departments() {
        return jdbc.queryForList("select d.id,d.parent_id as parentId,d.name,d.ancestors,d.sort,d.status,(select count(*) from sys_user u where u.dept_id=d.id and u.deleted=false) as userCount,(select count(*) from sys_dept c where c.parent_id=d.id) as childCount from sys_dept d order by d.sort,d.id");
    }

    public Map<String, Object> createDepartment(Map<String, Object> req) {
        String name = String.valueOf(req.getOrDefault("name", "")).trim();
        if (name.isBlank()) throw new IllegalArgumentException("部门名称不能为空");
        long parentId = number(req.get("parentId"), 0L);
        if (parentId != 0) requireDepartment(parentId);
        String ancestors = parentId == 0 ? "0," : departmentAncestors(parentId) + parentId + ",";
        long id = jdbc.queryForObject("insert into sys_dept(parent_id,name,ancestors,sort,status) values(?,?,?,?,?) returning id", Long.class, parentId, name, ancestors, number(req.get("sort"), 0), number(req.get("status"), 1));
        return departments().stream().filter(d -> ((Number) d.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    public Map<String, Object> updateDepartment(long id, Map<String, Object> req) {
        if (id == number(req.get("parentId"), -1L))
            throw new IllegalArgumentException("部门不能设置为自身的上级");
        long parentId = number(req.get("parentId"), 0L);
        if (parentId != 0) requireDepartment(parentId);
        if (parentId != 0 && isDescendant(parentId, id))
            throw new IllegalArgumentException("不能将部门移动到其下级部门中");
        String name = req.get("name") == null ? null : String.valueOf(req.get("name")).trim();
        String ancestors = parentId == 0 ? "0," : departmentAncestors(parentId) + parentId + ",";
        jdbc.update("update sys_dept set name=coalesce(?,name),parent_id=?,ancestors=?,sort=coalesce(?,sort),status=coalesce(?,status) where id=?", name, parentId, ancestors, req.get("sort"), req.get("status"), id);
        return departments().stream().filter(d -> ((Number) d.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    public void deleteDepartment(long id) {
        Integer children = jdbc.queryForObject("select count(*) from sys_dept where parent_id=?", Integer.class, id);
        Integer users = jdbc.queryForObject("select count(*) from sys_user where dept_id=? and deleted=false", Integer.class, id);
        if (children != null && children > 0)
            throw new IllegalStateException("部门下存在子部门，无法删除");
        if (users != null && users > 0) throw new IllegalStateException("部门下存在员工，无法删除");
        jdbc.update("delete from sys_dept where id=?", id);
    }

    private String departmentAncestors(long id) {
        return jdbc.queryForObject("select ancestors from sys_dept where id=?", String.class, id);
    }

    private boolean isDescendant(long candidate, long ancestor) {
        Integer count = jdbc.queryForObject("with recursive tree as (select id from sys_dept where id=? union all select d.id from sys_dept d join tree t on d.parent_id=t.id) select count(*) from tree where id=?", Integer.class, ancestor, candidate);
        return count != null && count > 0;
    }

    private long number(Object value, long fallback) {
        return value instanceof Number n ? n.longValue() : fallback;
    }

    private void requireDepartment(long id) {
        if (id <= 0 || jdbc.queryForObject("select count(*) from sys_dept where id=? and status=1", Integer.class, id) == 0)
            throw new IllegalArgumentException("部门不存在或已停用");
    }

    public List<Map<String, Object>> roles() {
        return jdbc.queryForList("select r.id,r.code,r.name,r.remark,r.status,(select count(*) from sys_user_role ur where ur.role_id=r.id) as userCount,(select count(*) from sys_role_permission rp where rp.role_id=r.id) as menuCount from sys_role r order by r.id");
    }

    public Map<String, Object> createRole(Map<String, Object> req) {
        String code = text(req.get("code")).toUpperCase(Locale.ROOT);
        String name = text(req.get("name"));
        if (code.isBlank() || !code.matches("[A-Z][A-Z0-9_]{2,63}"))
            throw new IllegalArgumentException("角色编码须为 3-64 位大写字母、数字或下划线");
        if (name.isBlank()) throw new IllegalArgumentException("请输入角色名称");
        if (jdbc.queryForObject("select count(*) from sys_role where code=?", Integer.class, code) > 0)
            throw new IllegalArgumentException("角色编码已存在");
        long id = jdbc.queryForObject("insert into sys_role(code,name,remark,status) values(?,?,?,?) returning id", Long.class, code, name, req.get("remark"), number(req.get("status"), 1));
        return role(id);
    }

    public Map<String, Object> updateRole(long id, Map<String, Object> req) {
        ensureRole(id);
        String code = req.containsKey("code") ? text(req.get("code")).toUpperCase(Locale.ROOT) : null;
        if (code != null && (!code.matches("[A-Z][A-Z0-9_]{2,63}")))
            throw new IllegalArgumentException("角色编码须为 3-64 位大写字母、数字或下划线");
        if (code != null && jdbc.queryForObject("select count(*) from sys_role where code=? and id<>?", Integer.class, code, id) > 0)
            throw new IllegalArgumentException("角色编码已存在");
        String name = req.containsKey("name") ? text(req.get("name")) : null;
        if (name != null && name.isBlank()) throw new IllegalArgumentException("请输入角色名称");
        jdbc.update("update sys_role set code=coalesce(?,code),name=coalesce(?,name),remark=coalesce(?,remark),status=coalesce(?,status) where id=?", code, name, req.get("remark"), req.get("status"), id);
        return role(id);
    }

    public void deleteRole(long id) {
        ensureRole(id);
        if (jdbc.queryForObject("select count(*) from sys_user_role where role_id=?", Integer.class, id) > 0)
            throw new IllegalStateException("角色已关联用户，无法删除");
        jdbc.update("delete from sys_role_permission where role_id=?", id);
        jdbc.update("delete from sys_role where id=?", id);
    }

    public List<Long> roleMenuIds(long id) {
        ensureRole(id);
        return jdbc.queryForList("select permission_id from sys_role_permission where role_id=? order by permission_id", Long.class, id);
    }

    public void updateRoleMenus(long id, Map<String, Object> req) {
        ensureRole(id);
        List<Long> ids = values(req.get("menuIds"));
        if (!ids.isEmpty()) {
            Integer existing = jdbc.queryForObject("select count(*) from sys_permission where type=1 and id in (" + placeholders(ids.size()) + ")", Integer.class, ids.toArray());
            if (existing == null || existing != ids.size()) throw new IllegalArgumentException("包含不存在的菜单");
        }
        jdbc.update("delete from sys_role_permission where role_id=?", id);
        for (Long menuId : ids)
            jdbc.update("insert into sys_role_permission(role_id,permission_id) values(?,?)", id, menuId);
    }

    public List<Map<String, Object>> menus() {
        return jdbc.queryForList("select p.id,p.parent_id as parentId,p.name,p.perms,p.path,p.sort,p.status,(select count(*) from sys_permission c where c.parent_id=p.id) as childCount,(select count(*) from sys_role_permission rp where rp.permission_id=p.id) as roleCount from sys_permission p where p.type=1 order by p.sort,p.id");
    }

    public List<Long> userMenuIds(String username) {
        return jdbc.queryForList("select distinct rp.permission_id from sys_user u join sys_user_role ur on ur.user_id=u.id join sys_role r on r.id=ur.role_id and r.status=1 join sys_role_permission rp on rp.role_id=r.id join sys_permission p on p.id=rp.permission_id and p.status=1 and p.type=1 where u.username=? and u.deleted=false", Long.class, username);
    }

    public Map<String, Object> createMenu(Map<String, Object> req) {
        String name = text(req.get("name"));
        if (name.isBlank()) throw new IllegalArgumentException("请输入菜单名称");
        long parentId = number(req.get("parentId"), 0);
        if (parentId != 0) ensureMenu(parentId);
        String perms = nullableText(req.get("perms"));
        ensurePermissionCode(perms, 0);
        long id = jdbc.queryForObject("insert into sys_permission(parent_id,name,perms,type,path,sort,status) values(?,?,?,1,?,?,?) returning id", Long.class, parentId, name, perms, nullableText(req.get("path")), number(req.get("sort"), 0), number(req.get("status"), 1));
        return menu(id);
    }

    public Map<String, Object> updateMenu(long id, Map<String, Object> req) {
        ensureMenu(id);
        long parentId = number(req.get("parentId"), 0);
        if (id == parentId) throw new IllegalArgumentException("菜单不能设置为自身的上级");
        if (parentId != 0) ensureMenu(parentId);
        if (parentId != 0 && isMenuDescendant(parentId, id))
            throw new IllegalArgumentException("不能将菜单移动到其下级菜单中");
        String name = req.containsKey("name") ? text(req.get("name")) : null;
        if (name != null && name.isBlank()) throw new IllegalArgumentException("请输入菜单名称");
        String perms = req.containsKey("perms") ? nullableText(req.get("perms")) : null;
        ensurePermissionCode(perms, id);
        jdbc.update("update sys_permission set parent_id=?,name=coalesce(?,name),perms=coalesce(?,perms),path=coalesce(?,path),sort=coalesce(?,sort),status=coalesce(?,status) where id=?", parentId, name, perms, req.get("path"), req.get("sort"), req.get("status"), id);
        return menu(id);
    }

    public void deleteMenu(long id) {
        ensureMenu(id);
        if (jdbc.queryForObject("select count(*) from sys_permission where parent_id=?", Integer.class, id) > 0)
            throw new IllegalStateException("菜单下存在子菜单，无法删除");
        if (jdbc.queryForObject("select count(*) from sys_role_permission where permission_id=?", Integer.class, id) > 0)
            throw new IllegalStateException("菜单已分配给角色，无法删除");
        jdbc.update("delete from sys_permission where id=?", id);
    }

    private Map<String, Object> role(long id) {
        return roles().stream().filter(r -> ((Number) r.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    private Map<String, Object> menu(long id) {
        return menus().stream().filter(m -> ((Number) m.get("id")).longValue() == id).findFirst().orElseThrow();
    }

    private void ensureRole(long id) {
        if (jdbc.queryForObject("select count(*) from sys_role where id=?", Integer.class, id) == 0)
            throw new IllegalArgumentException("角色不存在");
    }

    private void ensureMenu(long id) {
        if (jdbc.queryForObject("select count(*) from sys_permission where id=? and type=1", Integer.class, id) == 0)
            throw new IllegalArgumentException("菜单不存在");
    }

    private void ensurePermissionCode(String perms, long id) {
        if (perms != null && jdbc.queryForObject("select count(*) from sys_permission where perms=? and id<>?", Integer.class, perms, id) > 0)
            throw new IllegalArgumentException("权限标识已存在");
    }

    private boolean isMenuDescendant(long candidate, long ancestor) {
        Integer total = jdbc.queryForObject("with recursive tree as (select id from sys_permission where id=? union all select p.id from sys_permission p join tree t on p.parent_id=t.id) select count(*) from tree where id=?", Integer.class, ancestor, candidate);
        return total != null && total > 0;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String valueText = text(value);
        return valueText.isBlank() ? null : valueText;
    }

    private List<Long> values(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        return list.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::longValue).distinct().toList();
    }

    private String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    public List<Map<String, Object>> auditLogs() {
        return jdbc.queryForList("select id,created_at as createdAt,username,action,module,detail,result from audit_log order by created_at desc,id desc");
    }

    public List<Map<String, Object>> bases() {
        return jdbc.queryForList("select id,name,description,category,visibility,owner_id as ownerId,dept_id as deptId,chunk_size as chunkSize,chunk_overlap as chunkOverlap,created_at as createdAt,updated_at as updatedAt,(select count(*) from kb_document d where d.kb_id=kb_knowledge_base.id and d.deleted=false) as docCount from kb_knowledge_base where deleted=false order by id desc");
    }

    public record KbScope(long userId, Long deptId, boolean admin, boolean kbAccess, boolean kbManager) {
    }

    public KbScope kbScope(String username) {
        return jdbc.query("select u.id,u.dept_id, " +
                        "exists(select 1 from sys_user_role ur join sys_role r on r.id=ur.role_id and r.status=1 where ur.user_id=u.id and r.code='ROLE_ADMIN') as admin, " +
                        "exists(select 1 from sys_user_role ur join sys_role r on r.id=ur.role_id and r.status=1 join sys_role_permission rp on rp.role_id=r.id join sys_permission p on p.id=rp.permission_id and p.status=1 where ur.user_id=u.id and p.perms='kb:view') as kb_access, " +
                        "exists(select 1 from sys_user_role ur join sys_role r on r.id=ur.role_id and r.status=1 where ur.user_id=u.id and r.code='ROLE_KB_MANAGER') as kb_manager " +
                        "from sys_user u where u.username=? and u.deleted=false and u.status=1",
                rs -> {
                    if (!rs.next()) throw new IllegalArgumentException("当前用户不存在或已停用");
                    return new KbScope(rs.getLong("id"), (Long) rs.getObject("dept_id"), rs.getBoolean("admin"), rs.getBoolean("kb_access"), rs.getBoolean("kb_manager"));
                }, username);
    }

    public List<Map<String, Object>> accessibleBases(KbScope scope) {
        return bases().stream().filter(base -> canReadBase(base, scope)).peek(base -> enrichBaseAccess(base, scope)).toList();
    }

    public List<Long> accessibleBaseIds(KbScope scope) {
        return accessibleBases(scope).stream().map(base -> numberValue(base, "id")).toList();
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
        String visibility = String.valueOf(r.getOrDefault("visibility", "DEPT")).toUpperCase(Locale.ROOT);
        if (!Set.of("PRIVATE", "DEPT", "ORG", "PUBLIC").contains(visibility))
            throw new IllegalArgumentException("知识库可见范围不合法");
        if (("ORG".equals(visibility) || "PUBLIC".equals(visibility)) && !scope.admin())
            throw new SecurityException("仅系统管理员可创建组织级或公开知识库");
        Long deptId = "DEPT".equals(visibility) ? scope.deptId() : null;
        if ("DEPT".equals(visibility) && deptId == null)
            throw new IllegalArgumentException("当前用户未分配部门，无法创建部门知识库");
        long id = jdbc.queryForObject("insert into kb_knowledge_base(name,description,category,visibility,owner_id,dept_id,chunk_size,chunk_overlap) values(?,?,?,?,?,?,?,?) returning id", Long.class,
                r.getOrDefault("name", "未命名知识库"), r.getOrDefault("description", ""), r.getOrDefault("category", ""), visibility, scope.userId(), deptId, r.getOrDefault("chunkSize", 512), r.getOrDefault("chunkOverlap", 64));
        if (deptId != null)
            jdbc.update("insert into kb_knowledge_base_dept(kb_id,dept_id) values(?,?) on conflict do nothing", id, deptId);
        return id;
    }

    public Map<String, Object> base(long id) {
        return jdbc.queryForMap("select id,name,description,category,visibility,owner_id as ownerId,dept_id as deptId,chunk_size as chunkSize,chunk_overlap as chunkOverlap,created_at as createdAt,updated_at as updatedAt,(select count(*) from kb_document d where d.kb_id=kb_knowledge_base.id and d.deleted=false) as docCount from kb_knowledge_base where id=? and deleted=false", id);
    }

    public void updateBase(long id, Map<String, Object> r, KbScope scope) {
        Map<String, Object> existing = base(id);
        String visibility = r.containsKey("visibility") ? String.valueOf(r.get("visibility")).toUpperCase(Locale.ROOT) : String.valueOf(valueOf(existing, "visibility"));
        if (!Set.of("PRIVATE", "DEPT", "ORG", "PUBLIC").contains(visibility))
            throw new IllegalArgumentException("知识库可见范围不合法");
        if (("ORG".equals(visibility) || "PUBLIC".equals(visibility)) && !scope.admin())
            throw new SecurityException("仅系统管理员可设置组织级或公开范围");
        Long deptId = "DEPT".equals(visibility) ? (scope.admin() ? numberOrNull(existing, "deptId") : scope.deptId()) : null;
        if ("DEPT".equals(visibility) && deptId == null)
            throw new IllegalArgumentException("部门知识库必须归属有效部门");
        jdbc.update("update kb_knowledge_base set name=coalesce(?,name),description=coalesce(?,description),visibility=?,dept_id=?,updated_at=now() where id=?", r.get("name"), r.get("description"), visibility, deptId, id);
        if ("DEPT".equals(visibility) && allowedDepartmentIds(id).isEmpty())
            jdbc.update("insert into kb_knowledge_base_dept(kb_id,dept_id) values(?,?) on conflict do nothing", id, deptId);
    }

    public List<Long> allowedDepartmentIds(long kbId) {
        return jdbc.queryForList("select dept_id from kb_knowledge_base_dept where kb_id=? order by dept_id", Long.class, kbId);
    }

    public void updateAllowedDepartments(long kbId, List<Long> deptIds, KbScope scope) {
        if (!scope.admin()) throw new SecurityException("仅系统管理员可配置知识库部门权限");
        Map<String, Object> target = base(kbId);
        List<Long> uniqueIds = deptIds.stream().distinct().toList();
        if (!uniqueIds.isEmpty()) {
            Integer active = jdbc.queryForObject("select count(*) from sys_dept where status=1 and id in (" + placeholders(uniqueIds.size()) + ")", Integer.class, uniqueIds.toArray());
            if (active == null || active != uniqueIds.size())
                throw new IllegalArgumentException("包含不存在或已停用的部门");
        }
        if ("DEPT".equals(String.valueOf(valueOf(target, "visibility"))) && uniqueIds.isEmpty())
            throw new IllegalArgumentException("部门知识库至少需要授权一个部门");
        jdbc.update("delete from kb_knowledge_base_dept where kb_id=?", kbId);
        for (Long deptId : uniqueIds)
            jdbc.update("insert into kb_knowledge_base_dept(kb_id,dept_id) values(?,?)", kbId, deptId);
    }

    public void deleteBase(long id) {
        jdbc.update("update kb_knowledge_base set deleted=true,updated_at=now() where id=?", id);
    }

    public long createDoc(long kbId, String name, String type, long size, String key, long uploader) {
        return jdbc.queryForObject("insert into kb_document(kb_id,file_name,file_type,file_size,object_key,uploader_id) values(?,?,?,?,?,?) returning id", Long.class, kbId, name, type, size, key, uploader);
    }

    public Map<String, Object> doc(long id) {
        return jdbc.queryForMap("select id,kb_id as kbId,file_name as fileName,file_type as fileType,file_size as fileSize,object_key as objectKey,version,parse_status as parseStatus,chunk_status as chunkStatus,chunk_count as chunkCount,error_msg as errorMsg,created_at as createdAt,updated_at as updatedAt from kb_document where id=? and deleted=false", id);
    }

    public List<Map<String, Object>> docs(long kbId) {
        return jdbc.queryForList("select id,kb_id as kbId,file_name as fileName,file_type as fileType,file_size as fileSize,version,parse_status as parseStatus,chunk_status as chunkStatus,chunk_count as chunkCount,error_msg as errorMsg,created_at as createdAt,updated_at as updatedAt from kb_document where kb_id=? and deleted=false order by id desc", kbId);
    }

    public void status(long id, String parse, String chunk, int count, String error) {
        jdbc.update("update kb_document set parse_status=?,chunk_status=?,chunk_count=?,error_msg=?,updated_at=now() where id=?", parse, chunk, count, error, id);
    }

    public void deleteDoc(long id) {
        jdbc.update("update kb_document set deleted=true,updated_at=now() where id=?", id);
    }

    private void requireKbAccess(KbScope scope) {
        if (!scope.admin() && !scope.kbAccess()) throw new SecurityException("当前角色未获知识库访问授权");
    }

    private boolean canReadBase(Map<String, Object> base, KbScope scope) {
        if (scope.admin()) return true;
        if (!scope.kbAccess()) return false;
        String visibility = String.valueOf(valueOf(base, "visibility"));
        if ("ORG".equals(visibility) || "PUBLIC".equals(visibility)) return departmentAllowed(base, scope);
        if ("PRIVATE".equals(visibility)) return numberValue(base, "ownerId") == scope.userId();
        return "DEPT".equals(visibility) && departmentAllowed(base, scope);
    }

    private boolean canManageBase(Map<String, Object> base, KbScope scope) {
        if (!canReadBase(base, scope)) return false;
        if (scope.admin() || numberValue(base, "ownerId") == scope.userId()) return true;
        return scope.kbManager() && "DEPT".equals(String.valueOf(valueOf(base, "visibility"))) && departmentAllowed(base, scope);
    }

    private void enrichBaseAccess(Map<String, Object> base, KbScope scope) {
        base.put("allowedDeptIds", allowedDepartmentIds(numberValue(base, "id")));
        base.put("canManage", canManageBase(base, scope));
        base.put("canConfigureDepartments", scope.admin());
    }

    private boolean departmentAllowed(Map<String, Object> base, KbScope scope) {
        if (scope.deptId() == null) return false;
        List<Long> allowed = allowedDepartmentIds(numberValue(base, "id"));
        if (!allowed.isEmpty()) return allowed.contains(scope.deptId());
        String visibility = String.valueOf(valueOf(base, "visibility"));
        return !"DEPT".equals(visibility) || Objects.equals(numberOrNull(base, "deptId"), scope.deptId());
    }

    private Object valueOf(Map<String, Object> values, String key) {
        if (values.containsKey(key)) return values.get(key);
        return values.get(key.toLowerCase(Locale.ROOT));
    }

    private long numberValue(Map<String, Object> values, String key) {
        Object value = valueOf(values, key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long numberOrNull(Map<String, Object> values, String key) {
        Object value = valueOf(values, key);
        return value instanceof Number number ? number.longValue() : null;
    }

    public long saveChunk(long doc, long kb, int seq, String content, Integer page, List<Float> vector) {
        int characterCount = content == null ? 0 : content.codePointCount(0, content.length());
        return jdbc.queryForObject("insert into kb_chunk(doc_id,kb_id,seq,content,page_no,token_count,embedding_id) values(?,?,?,?,?,?,?) returning id", Long.class, doc, kb, seq, content, page, characterCount, String.valueOf(System.nanoTime()));
    }

    @Transactional
    public void clearChunks(long docId) {
        jdbc.update("delete from kb_chunk where doc_id=?", docId);
    }

    /**
     * Keyword candidates complement semantic retrieval for exact terms such as document numbers and product models.
     * Every value is parameterized and the caller-provided knowledge-base range is already authorization-filtered.
     */
    public List<Map<String, Object>> keywordChunks(List<String> terms, List<Long> allowedKbIds, int limit) {
        if (terms == null || terms.isEmpty() || allowedKbIds == null || allowedKbIds.isEmpty()) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        String kbPlaceholders = placeholders(allowedKbIds.size());
        String termClause = String.join(" or ", Collections.nCopies(terms.size(), "(c.content ilike ? or d.file_name ilike ?)"));
        List<Object> parameters = new ArrayList<>(allowedKbIds);
        for (String term : terms) {
            String pattern = "%" + term.replace("%", "\\%").replace("_", "\\_") + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        }
        parameters.add(safeLimit);
        String sql = "select c.id,c.doc_id,c.kb_id,c.content,c.page_no,d.file_name "
                + "from kb_chunk c join kb_document d on d.id=c.doc_id "
                + "where c.kb_id in (" + kbPlaceholders + ") and d.deleted=false "
                + "and d.parse_status='SUCCESS' and d.chunk_status in ('INDEXED','PARTIAL') and (" + termClause + ") "
                + "order by c.id desc limit ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters.toArray());
        return rows.stream()
                .map(row -> keywordChunk(row, terms))
                .sorted(Comparator.comparingDouble(row -> -((Number) row.get("keywordScore")).doubleValue()))
                .limit(safeLimit)
                .toList();
    }

    private Map<String, Object> keywordChunk(Map<String, Object> row, List<String> terms) {
        String content = String.valueOf(row.getOrDefault("content", "")).toLowerCase(Locale.ROOT);
        String fileName = String.valueOf(row.getOrDefault("file_name", "")).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            String normalized = term.toLowerCase(Locale.ROOT);
            if (content.contains(normalized)) score += 2;
            if (fileName.contains(normalized)) score += 3;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("score", (double) score);
        result.put("keywordScore", score);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunk_id", row.get("id"));
        payload.put("doc_id", row.get("doc_id"));
        payload.put("kb_id", row.get("kb_id"));
        payload.put("content", row.get("content"));
        payload.put("file_name", row.get("file_name"));
        if (row.get("page_no") != null) payload.put("page_no", row.get("page_no"));
        result.put("payload", payload);
        return result;
    }

    public List<Map<String, Object>> retrievalEvalCases() {
        return jdbc.query("select id,question,expected_chunk_ids,enabled,note,created_at as createdAt,updated_at as updatedAt from kb_retrieval_eval_case order by id",
                (rs, rowNum) -> evalCaseRow(rs));
    }

    public Map<String, Object> retrievalEvalCase(long id) {
        List<Map<String, Object>> rows = jdbc.query("select id,question,expected_chunk_ids,enabled,note,created_at as createdAt,updated_at as updatedAt from kb_retrieval_eval_case where id=?",
                (rs, rowNum) -> evalCaseRow(rs), id);
        if (rows.isEmpty()) throw new NoSuchElementException("检索评测用例不存在");
        return rows.get(0);
    }

    public Map<String, Object> createRetrievalEvalCase(Map<String, Object> request) {
        String question = String.valueOf(request.getOrDefault("question", "")).trim();
        if (question.isBlank()) throw new IllegalArgumentException("评测问题不能为空");
        List<Long> expected = values(request.get("expectedChunkIds"));
        long id = jdbc.queryForObject("insert into kb_retrieval_eval_case(question,expected_chunk_ids,enabled,note) values(?,?::bigint[],?,?) returning id",
                Long.class, question, expected.toArray(Long[]::new), request.getOrDefault("enabled", true), request.get("note"));
        return retrievalEvalCase(id);
    }

    private Map<String, Object> evalCaseRow(java.sql.ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("question", rs.getString("question"));
        row.put("expectedChunkIds", arrayValues(rs.getArray("expected_chunk_ids")));
        row.put("enabled", rs.getBoolean("enabled"));
        row.put("note", rs.getString("note"));
        row.put("createdAt", rs.getObject("createdAt"));
        row.put("updatedAt", rs.getObject("updatedAt"));
        return row;
    }

    public record ChatTurn(long conversationId, long userMessageId, long assistantMessageId) {
    }

    public Map<String, Object> conversations(long userId, String cursor, int pageSize) {
        int limit = Math.max(1, Math.min(pageSize, 50));
        List<Object> parameters = new ArrayList<>();
        String cursorClause = "";
        if (cursor != null && !cursor.isBlank()) {
            long cursorId;
            try {
                cursorId = Long.parseLong(cursor);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("会话分页游标无效");
            }
            Date cursorTime = jdbc.query("select last_message_at from kb_conversation where id=? and user_id=? and deleted_at is null",
                    rs -> rs.next() ? rs.getTimestamp(1) : null, cursorId, userId);
            if (cursorTime == null) throw new IllegalArgumentException("会话分页游标无效");
            cursorClause = " and (last_message_at, id) < (?, ?)";
            parameters.add(cursorTime);
            parameters.add(cursorId);
        }
        parameters.add(userId);
        parameters.add(limit + 1);
        String sql = "select id,title,selected_kb_ids,last_message_at,created_at,updated_at from kb_conversation "
                + "where deleted_at is null" + cursorClause + " and user_id=? order by last_message_at desc,id desc limit ?";
        List<Map<String, Object>> rows = jdbc.query(sql, (rs, rowNum) -> conversationRow(rs.getLong("id"), rs.getString("title"),
                rs.getArray("selected_kb_ids"), rs.getObject("last_message_at"), rs.getObject("created_at"), rs.getObject("updated_at")), parameters.toArray());
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows.remove(rows.size() - 1);
        String nextCursor = hasMore && !rows.isEmpty() ? String.valueOf(rows.get(rows.size() - 1).get("id")) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", hasMore);
        return result;
    }

    public Map<String, Object> conversation(long userId, long conversationId) {
        List<Map<String, Object>> rows = jdbc.query("select id,title,selected_kb_ids,last_message_at,created_at,updated_at from kb_conversation where id=? and user_id=? and deleted_at is null",
                (rs, rowNum) -> conversationRow(rs.getLong("id"), rs.getString("title"), rs.getArray("selected_kb_ids"), rs.getObject("last_message_at"), rs.getObject("created_at"), rs.getObject("updated_at")), conversationId, userId);
        if (rows.isEmpty()) throw new NoSuchElementException("会话不存在或无权访问");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> createConversation(long userId, String title, List<Long> selectedKbIds) {
        String normalizedTitle = normalizeConversationTitle(title);
        long id = jdbc.queryForObject("insert into kb_conversation(user_id,title,selected_kb_ids) values(?,?,?::bigint[]) returning id", Long.class,
                userId, normalizedTitle, selectedKbIds.toArray(Long[]::new));
        return conversation(userId, id);
    }

    @Transactional
    public Map<String, Object> updateConversationTitle(long userId, long conversationId, String title) {
        String normalizedTitle = normalizeConversationTitle(title);
        int updated = jdbc.update("update kb_conversation set title=?,updated_at=now() where id=? and user_id=? and deleted_at is null", normalizedTitle, conversationId, userId);
        if (updated == 0) throw new NoSuchElementException("会话不存在或无权访问");
        return conversation(userId, conversationId);
    }

    @Transactional
    public void deleteConversation(long userId, long conversationId) {
        int updated = jdbc.update("update kb_conversation set deleted_at=now(),updated_at=now() where id=? and user_id=? and deleted_at is null", conversationId, userId);
        if (updated == 0) throw new NoSuchElementException("会话不存在或无权访问");
    }

    @Transactional
    public ChatTurn prepareChatTurn(long userId, Long requestedConversationId, String question, List<Long> permittedKbIds) {
        long conversationId;
        if (requestedConversationId == null) {
            conversationId = jdbc.queryForObject("insert into kb_conversation(user_id,title,selected_kb_ids) values(?,?,?::bigint[]) returning id", Long.class,
                    userId, titleFromQuestion(question), permittedKbIds.toArray(Long[]::new));
        } else {
            conversationId = requestedConversationId;
            List<Long> locked = jdbc.query("select id from kb_conversation where id=? and user_id=? and deleted_at is null for update",
                    (rs, rowNum) -> rs.getLong(1), conversationId, userId);
            if (locked.isEmpty()) throw new NoSuchElementException("会话不存在或无权访问");
        }
        int nextSequence = jdbc.queryForObject("select coalesce(max(sequence_no),0)+1 from kb_chat_message where conversation_id=?", Integer.class, conversationId);
        long userMessageId = jdbc.queryForObject("insert into kb_chat_message(conversation_id,sequence_no,role,content,status,source_kb_ids,completed_at) values(?,?,'USER',?,'COMPLETED',?::bigint[],now()) returning id", Long.class,
                conversationId, nextSequence, question, permittedKbIds.toArray(Long[]::new));
        long assistantMessageId = jdbc.queryForObject("insert into kb_chat_message(conversation_id,sequence_no,role,status,source_kb_ids) values(?,?,'ASSISTANT','STREAMING',?::bigint[]) returning id", Long.class,
                conversationId, nextSequence + 1, permittedKbIds.toArray(Long[]::new));
        jdbc.update("update kb_conversation set selected_kb_ids=?::bigint[],last_message_at=now(),updated_at=now() where id=?", permittedKbIds.toArray(Long[]::new), conversationId);
        return new ChatTurn(conversationId, userMessageId, assistantMessageId);
    }

    public Map<String, Object> messages(long userId, long conversationId, String before, int pageSize) {
        conversation(userId, conversationId);
        int limit = Math.max(1, Math.min(pageSize, 50));
        List<Object> parameters = new ArrayList<>();
        parameters.add(conversationId);
        String beforeClause = "";
        if (before != null && !before.isBlank()) {
            long beforeId;
            try {
                beforeId = Long.parseLong(before);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("消息分页游标无效");
            }
            Integer beforeSequence = jdbc.query("select sequence_no from kb_chat_message where id=? and conversation_id=?", rs -> rs.next() ? rs.getInt(1) : null, beforeId, conversationId);
            if (beforeSequence == null) throw new IllegalArgumentException("消息分页游标无效");
            beforeClause = " and sequence_no < ?";
            parameters.add(beforeSequence);
        }
        parameters.add(limit + 1);
        List<Map<String, Object>> rows = jdbc.query("select id,sequence_no,role,content,status,source_kb_ids,retrieved_chunk_ids,model_name,latency_ms,error_message,qa_record_id,created_at,completed_at from kb_chat_message where conversation_id=?" + beforeClause + " order by sequence_no desc limit ?",
                (rs, rowNum) -> messageRow(rs), parameters.toArray());
        boolean hasMore = rows.size() > limit;
        if (hasMore) rows.remove(rows.size() - 1);
        Collections.reverse(rows);
        for (Map<String, Object> row : rows)
            row.put("citations", messageCitations(((Number) row.get("id")).longValue()));
        String nextCursor = hasMore && !rows.isEmpty() ? String.valueOf(rows.get(0).get("id")) : null;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("nextCursor", nextCursor);
        result.put("hasMore", hasMore);
        return result;
    }

    public String recentChatHistory(long userId, long conversationId) {
        conversation(userId, conversationId);
        List<Map<String, Object>> rows = jdbc.query("select role,content from kb_chat_message where conversation_id=? and status='COMPLETED' and role in ('USER','ASSISTANT') order by sequence_no desc limit 8",
                (rs, rowNum) -> Map.of("role", rs.getString("role"), "content", rs.getString("content")), conversationId);
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
        long recordId = jdbc.queryForObject("insert into kb_qa_record(user_id,kb_ids,question,answer,retrieved_chunk_ids,model_name,latency_ms,conversation_id) values(?,?::bigint[],?,?,?::bigint[],?,?,?) returning id",
                Long.class, userId, permittedKbIds.toArray(Long[]::new), question, answer, retrievedChunkIds.toArray(Long[]::new), "qwen-plus", latencyMs, turn.conversationId());
        try {
            jdbc.update("update kb_qa_record set rerank_scores=?::jsonb where id=?", objectMapper.writeValueAsString(retrievalTrace == null ? Map.of() : retrievalTrace), recordId);
        } catch (JsonProcessingException ignored) {
            // Retrieval telemetry must not make a successfully generated answer fail.
        }
        jdbc.update("update kb_chat_message set content=?,status='COMPLETED',retrieved_chunk_ids=?::bigint[],model_name=?,latency_ms=?,qa_record_id=?,completed_at=now() where id=? and conversation_id=?",
                answer, retrievedChunkIds.toArray(Long[]::new), "qwen-plus", latencyMs, recordId, turn.assistantMessageId(), turn.conversationId());
        for (int index = 0; index < citations.size(); index++) {
            Map<String, Object> citation = citations.get(index);
            jdbc.update("insert into kb_chat_message_citation(message_id,chunk_id,doc_id,kb_id,file_name,page_no,snippet,rank_no,score) values(?,?,?,?,?,?,?,?,?)",
                    turn.assistantMessageId(), longValue(citation.get("chunkId")), longValue(citation.get("docId")), longValue(citation.get("kbId")),
                    String.valueOf(citation.getOrDefault("fileName", "知识库文档")), positiveInt(citation.get("pageNo")),
                    String.valueOf(citation.getOrDefault("snippet", "")), index + 1, doubleValue(citation.get("score")));
        }
        jdbc.update("update kb_qa_record set assistant_message_id=? where id=?", turn.assistantMessageId(), recordId);
        jdbc.update("update kb_conversation set last_message_at=now(),updated_at=now() where id=?", turn.conversationId());
        return recordId;
    }

    @Transactional
    public void failChatTurn(ChatTurn turn, String message) {
        String safeMessage = message == null || message.isBlank() ? "问答生成失败，请稍后重试" : message.substring(0, Math.min(message.length(), 500));
        jdbc.update("update kb_chat_message set status='FAILED',error_message=?,completed_at=now() where id=? and conversation_id=?", safeMessage, turn.assistantMessageId(), turn.conversationId());
        jdbc.update("update kb_conversation set last_message_at=now(),updated_at=now() where id=?", turn.conversationId());
    }

    public Map<String, Object> qaRecords(long userId, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int limit = Math.max(1, Math.min(pageSize, 100));
        int offset = (normalizedPage - 1) * limit;
        List<Map<String, Object>> items = jdbc.query("select id,conversation_id,question,answer,model_name,latency_ms,created_at from kb_qa_record where user_id=? order by created_at desc,id desc limit ? offset ?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("conversationId", rs.getObject("conversation_id"));
                    row.put("question", rs.getString("question"));
                    row.put("answer", rs.getString("answer"));
                    row.put("modelName", rs.getString("model_name"));
                    row.put("latencyMs", rs.getObject("latency_ms"));
                    row.put("createdAt", rs.getObject("created_at"));
                    return row;
                }, userId, limit, offset);
        Integer total = jdbc.queryForObject("select count(*) from kb_qa_record where user_id=?", Integer.class, userId);
        return Map.of("items", items, "total", total == null ? 0 : total, "page", normalizedPage, "pageSize", limit);
    }

    public Map<String, Object> qaRecord(long userId, long recordId) {
        List<Map<String, Object>> rows = jdbc.query("select id,conversation_id,question,answer,model_name,latency_ms,created_at from kb_qa_record where id=? and user_id=?",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("conversationId", rs.getObject("conversation_id"));
                    row.put("question", rs.getString("question"));
                    row.put("answer", rs.getString("answer"));
                    row.put("modelName", rs.getString("model_name"));
                    row.put("latencyMs", rs.getObject("latency_ms"));
                    row.put("createdAt", rs.getObject("created_at"));
                    return row;
                }, recordId, userId);
        if (rows.isEmpty()) throw new NoSuchElementException("问答记录不存在或无权访问");
        return rows.get(0);
    }

    public boolean ownsQaRecord(long userId, long recordId) {
        Integer count = jdbc.queryForObject("select count(*) from kb_qa_record where id=? and user_id=?", Integer.class, recordId, userId);
        return count != null && count > 0;
    }

    private Map<String, Object> conversationRow(long id, String title, Array selectedKbIds, Object lastMessageAt, Object createdAt, Object updatedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("selectedKbIds", arrayValues(selectedKbIds));
        row.put("lastMessageAt", lastMessageAt);
        row.put("createdAt", createdAt);
        row.put("updatedAt", updatedAt);
        return row;
    }

    private Map<String, Object> messageRow(java.sql.ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("sequenceNo", rs.getInt("sequence_no"));
        row.put("role", rs.getString("role").toLowerCase(Locale.ROOT));
        row.put("content", rs.getString("content"));
        row.put("status", rs.getString("status"));
        row.put("sourceKbIds", arrayValues(rs.getArray("source_kb_ids")));
        row.put("retrievedChunkIds", arrayValues(rs.getArray("retrieved_chunk_ids")));
        row.put("modelName", rs.getString("model_name"));
        row.put("latencyMs", rs.getObject("latency_ms"));
        row.put("errorMessage", rs.getString("error_message"));
        row.put("qaRecordId", rs.getObject("qa_record_id"));
        row.put("createdAt", rs.getObject("created_at"));
        row.put("completedAt", rs.getObject("completed_at"));
        return row;
    }

    private List<Map<String, Object>> messageCitations(long messageId) {
        List<Map<String, Object>> citations = jdbc.query("select chunk_id,doc_id,kb_id,file_name,page_no,snippet,rank_no,score from kb_chat_message_citation where message_id=? order by rank_no",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("chunkId", rs.getObject("chunk_id"));
                    row.put("docId", rs.getObject("doc_id"));
                    row.put("kbId", rs.getObject("kb_id"));
                    row.put("fileName", rs.getString("file_name"));
                    row.put("pageNo", rs.getObject("page_no"));
                    row.put("snippet", rs.getString("snippet"));
                    row.put("rank", rs.getInt("rank_no"));
                    row.put("score", rs.getObject("score"));
                    return row;
                }, messageId);
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> citation : citations) {
            Long docId = longValue(citation.get("docId"));
            Long kbId = longValue(citation.get("kbId"));
            Integer pageNo = positiveInt(citation.get("pageNo"));
            String fileName = String.valueOf(citation.getOrDefault("fileName", "")).trim();
            if (docId == null || kbId == null || pageNo == null || docId <= 0 || kbId <= 0
                    || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) continue;
            String key = docId + ":" + kbId + ":" + fileName;
            Map<String, Object> entry = grouped.computeIfAbsent(key, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>(citation);
                value.put("pageNos", new TreeSet<Integer>());
                return value;
            });
            @SuppressWarnings("unchecked")
            Set<Integer> pages = (Set<Integer>) entry.get("pageNos");
            pages.add(pageNo);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> citation : grouped.values()) {
            @SuppressWarnings("unchecked")
            Set<Integer> pages = (Set<Integer>) citation.get("pageNos");
            List<Integer> sortedPages = List.copyOf(pages);
            citation.put("pageNo", sortedPages.get(0));
            citation.put("pageNos", sortedPages);
            result.add(citation);
        }
        return result.stream().map(citation -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("fileName", citation.get("fileName"));
            view.put("pageNo", citation.get("pageNo"));
            view.put("pageNos", citation.get("pageNos"));
            return view;
        }).toList();
    }

    private List<Long> arrayValues(Array value) {
        if (value == null) return List.of();
        try {
            Object raw = value.getArray();
            if (!(raw instanceof Object[] values)) return List.of();
            return Arrays.stream(values).filter(Objects::nonNull).map(item -> ((Number) item).longValue()).toList();
        } catch (SQLException e) {
            throw new IllegalStateException("无法读取会话关联数据", e);
        }
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

    public void saveQa(long id, long user, String q, String a, int latency) {
        jdbc.update("insert into kb_qa_record(id,user_id,question,answer,model_name,latency_ms) values(?,?,?,?,?,?)", id, user, q, a, "qwen-plus", latency);
    }

    public void feedback(long record, long user, int rating, String comment) {
        jdbc.update("insert into kb_feedback(qa_record_id,user_id,rating,comment) values(?,?,?,?) on conflict(qa_record_id) do update set rating=excluded.rating,comment=excluded.comment", record, user, rating, comment);
    }
}
