-- =====================================================================
-- V4__seed_platform_defaults.sql
-- Replaces the Java-side DepartmentDataInitializer / ensureSeed() in
-- PlatformRepository. Default BCrypt hash corresponds to password "Admin@123".
--
-- All statements are idempotent so the migration can re-run safely against
-- environments that pre-date this script.
-- =====================================================================

BEGIN;

-- 1) 默认部门
INSERT INTO sys_dept (id, parent_id, name, ancestors, sort, status)
VALUES
    (1, 0, E'总经办',         '0,', 10, 1),
    (2, 0, E'技术支持中心',   '0,', 20, 1),
    (3, 0, E'营销中心',       '0,', 30, 1),
    (4, 0, E'研发中心',       '0,', 40, 1),
    (5, 0, E'制造中心',       '0,', 50, 1),
    (6, 0, E'海外销售部',     '0,', 60, 1),
    (7, 0, E'职能中心',       '0,', 70, 1),
    (8, 0, E'工艺品质中心',   '0,', 80, 1),
    (9, 0, E'行政中心',       '0,', 90, 1)
ON CONFLICT (id) DO NOTHING;

UPDATE sys_dept SET name = E'总经办'         WHERE id = 1 AND name IN (E'产品中心', 'Product Center');
UPDATE sys_dept SET name = E'技术支持中心'   WHERE id = 2 AND name IN (E'技术中心', 'Technology Center');
UPDATE sys_dept SET name = E'营销中心'       WHERE id = 3 AND name IN (E'人力资源部', 'Human Resources');

SELECT setval(
    pg_get_serial_sequence('sys_dept', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM sys_dept), 1),
    true);

-- 2) 默认用户（密码 Admin@123，BCrypt 10 rounds）
-- 占位常量保持与 Java 端 DEFAULT_PASSWORD 一致
INSERT INTO sys_user (id, employee_no, username, password_hash, real_name, dept_id, status)
VALUES
    (1, 'E2024018', 'admin',       '$2a$10$eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.', E'管理员',   1, 1),
    (2, 'E2023012', 'dongzhiqiang', '$2a$10$eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.', E'董志强',   4, 1),
    (3, 'E2022056', 'yuchunyi',    '$2a$10$eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.', E'喻春意',   3, 1),
    (4, 'E2021008', 'sunkeping',   '$2a$10$eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.', E'孙可平',   9, 1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_user', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM sys_user), 1),
    true);

-- 3) 默认角色
INSERT INTO sys_role (id, code, name, status)
VALUES
    (1, 'ROLE_ADMIN',      E'系统管理员',     1),
    (2, 'ROLE_KB_MANAGER', E'知识库管理员',   1),
    (3, 'ROLE_EMPLOYEE',   E'普通员工',       1)
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('sys_role', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM sys_role), 1),
    true);

-- 4) 用户-角色关联
INSERT INTO sys_user_role (user_id, role_id)
VALUES (1, 1), (2, 2), (3, 3), (4, 3)
ON CONFLICT DO NOTHING;

-- 5) 默认权限菜单
INSERT INTO sys_permission (id, parent_id, name, perms, type, path, sort, status)
VALUES
    (1, 0, E'工作台',     'dashboard:view',     1, '/dashboard',      10, 1),
    (2, 0, E'知识库管理', 'kb:view',            1, '/kb',             20, 1),
    (3, 0, E'智能问答',   'rag:chat',           1, '/chat',           30, 1),
    (4, 0, E'系统管理',   'system:view',        1, NULL,              40, 1),
    (5, 4, E'用户管理',   'system:user:view',   1, '/system/users',   10, 1),
    (6, 4, E'部门管理',   'system:dept:view',   1, '/system/depts',   20, 1),
    (7, 4, E'角色管理',   'system:role:view',   1, '/system/roles',   30, 1),
    (8, 4, E'菜单管理',   'system:menu:view',   1, '/system/menus',   40, 1),
    (9, 0, E'审计日志',   'audit:view',         1, '/audit',          50, 1)
ON CONFLICT (id) DO NOTHING;

UPDATE sys_permission
   SET name = E'用户管理'
 WHERE id = 5
   AND perms = 'system:user:view'
   AND path = '/system/users';

SELECT setval(
    pg_get_serial_sequence('sys_permission', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM sys_permission), 1),
    true);

-- 6) 角色-权限映射
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 2, id FROM sys_permission WHERE id IN (1, 2, 3)
ON CONFLICT DO NOTHING;

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 3, id FROM sys_permission WHERE id IN (1, 2, 3)
ON CONFLICT DO NOTHING;

-- 7) 默认知识库
INSERT INTO kb_knowledge_base (id, name, description, category, visibility, owner_id, dept_id, chunk_size, chunk_overlap)
VALUES
    (1, E'产品与设计',     E'产品方法论、设计规范与研发协作流程', E'产品', 'DEPT',    1, 4, 512, 64),
    (2, E'员工制度与福利', E'入职、考勤、福利与员工服务制度',     E'人事', 'ORG',     1, NULL, 512, 64),
    (3, E'客户成功案例库', E'客户案例、解决方案与行业最佳实践',   E'案例', 'PRIVATE', 1, 3, 512, 64),
    (4, E'品牌内容资产',   E'品牌视觉、内容模板与对外传播资料',   E'品牌', 'PUBLIC',  1, NULL, 512, 64)
ON CONFLICT (id) DO NOTHING;

SELECT setval(
    pg_get_serial_sequence('kb_knowledge_base', 'id'),
    GREATEST((SELECT COALESCE(MAX(id), 1) FROM kb_knowledge_base), 1),
    true);

-- 8) 部门级知识库授权（DEPT 类型自动写入关联）
INSERT INTO kb_knowledge_base_dept (kb_id, dept_id)
VALUES (1, 4)
ON CONFLICT DO NOTHING;

-- 9) 演示审计日志
INSERT INTO audit_log (user_id, username, action, module, detail, result)
VALUES
    (1, E'管理员', 'LOGIN',     E'系统管理', '{"message":"网页登录"}'::jsonb,                   1),
    (2, E'董志强', 'DOC_UPLOAD', E'知识库',   '{"file":"设计规范v2.3.pdf"}'::jsonb,             1),
    (3, E'喻春意', 'QA_ASK',    E'智能问答', '{"question":"产品发布流程"}'::jsonb,              1),
    (4, E'孙可平', 'DOC_DELETE', E'知识库',  '{"documentId":182}'::jsonb,                       0)
;

COMMIT;
