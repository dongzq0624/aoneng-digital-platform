-- Remove the four audit rows shipped as demo seed data.
DELETE FROM audit_log
 WHERE (user_id = 1 AND username = 'admin' AND action = 'LOGIN'
        AND detail ->> 'message' = E'\u7f51\u9875\u767b\u5f55')
    OR (user_id = 2 AND username = 'dongzhiqiang' AND action = 'DOC_UPLOAD'
        AND detail ->> 'file' LIKE '%v2.3.pdf')
    OR (user_id = 3 AND username = 'yuchunyi' AND action = 'QA_ASK'
        AND detail ->> 'question' = E'\u4ea7\u54c1\u53d1\u5e03\u6d41\u7a0b')
    OR (user_id = 4 AND username = 'sunkeping' AND action = 'DOC_DELETE' AND detail ->> 'documentId' = '182');
