INSERT INTO sys_permission (id, parent_id, name, perms, type, path, sort, status)
VALUES (10, 0, 'RAG监控', 'rag:monitoring:view', 1, '/monitoring', 60, 1)
ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_role_permission (role_id, permission_id)
VALUES (1, 10)
ON CONFLICT DO NOTHING;
