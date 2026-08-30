package com.example.rag.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 统一新旧数据库中的默认组织名称和演示数据。
 */
@Component
public class DepartmentDataInitializer {
    private final JdbcTemplate jdbc;

    public DepartmentDataInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        try {
            jdbc.execute("create table if not exists kb_knowledge_base_dept (kb_id bigint not null references kb_knowledge_base(id) on delete cascade, dept_id bigint not null references sys_dept(id), created_at timestamptz not null default now(), primary key (kb_id, dept_id))");
            jdbc.execute("create index if not exists idx_kb_dept_dept on kb_knowledge_base_dept(dept_id)");
            updateLegacy(1, "\u603b\u7ecf\u529e", "\u4ea7\u54c1\u4e2d\u5fc3", "Product Center");
            updateLegacy(2, "\u6280\u672f\u652f\u6301\u4e2d\u5fc3", "Technology Center", "\u6280\u672f\u4e2d\u5fc3");
            updateLegacy(3, "\u8425\u9500\u4e2d\u5fc3", "Human Resources", "\u4eba\u529b\u8d44\u6e90\u90e8");
            insertIfMissing(4, "\u7814\u53d1\u4e2d\u5fc3", 40);
            insertIfMissing(5, "\u5236\u9020\u4e2d\u5fc3", 50);
            insertIfMissing(6, "\u6d77\u5916\u9500\u552e\u90e8", 60);
            insertIfMissing(7, "\u804c\u80fd\u4e2d\u5fc3", 70);
            insertIfMissing(8, "\u5de5\u827a\u54c1\u8d28\u4e2d\u5fc3", 80);
            insertIfMissing(9, "\u884c\u653f\u4e2d\u5fc3", 90);

            // 将旧版本演示账号统一为中文名称、部门和默认密码。
            String password = "\u0024\u0032\u0061\u0024\u0031\u0030\u0024eKaV11zKw8k6PHk/jYlx/.KFIpDsmf5uwlhSDve8.Nww1gQTZp9W.";
            jdbc.update("update sys_user set real_name=?, username='admin', password_hash=?, dept_id=1 where id=1", "\u7ba1\u7406\u5458", password);
            jdbc.update("update sys_user set real_name=?, username='dongzhiqiang', password_hash=?, dept_id=4 where id=2", "\u8463\u5fd7\u5f3a", password);
            jdbc.update("update sys_user set real_name=?, username='yuchunyi', password_hash=?, dept_id=3 where id=3", "\u55bb\u6625\u610f", password);
            jdbc.update("update sys_user set real_name=?, username='sunkeping', password_hash=?, dept_id=9 where id=4", "\u5b59\u53ef\u5e73", password);
            jdbc.update("update sys_user set real_name='\u6d4b\u8bd5\u7528\u6237', password_hash=? where id=5", password);
            jdbc.update("update sys_user set password_hash=?", password);

            jdbc.update("update sys_role set name='系统管理员' where id=1");
            jdbc.update("update sys_role set name='知识库管理员' where id=2");
            jdbc.update("update sys_role set name='普通员工' where id=3");
            jdbc.update("update kb_knowledge_base set name='产品与设计',description='产品方法论、设计规范与研发协作流程',category='产品' where id=1");
            jdbc.update("update kb_knowledge_base set dept_id=4 where id=1");
            jdbc.update("update kb_knowledge_base set name='员工制度与福利',description='入职、考勤、福利与员工服务制度',category='人事' where id=2");
            jdbc.update("update kb_knowledge_base set name='客户成功案例库',description='客户案例、解决方案与行业最佳实践',category='案例' where id=3");
            jdbc.update("update kb_knowledge_base set dept_id=3 where id=3");
            jdbc.update("insert into kb_knowledge_base_dept(kb_id,dept_id) select id,dept_id from kb_knowledge_base where deleted=false and visibility='DEPT' and dept_id is not null on conflict do nothing");
            jdbc.update("update kb_knowledge_base set name='品牌内容资产',description='品牌视觉、内容模板与对外传播资料',category='品牌' where id=4");
            jdbc.update("update audit_log set username='管理员',module='系统管理',detail='{\"message\":\"网页登录\"}'::jsonb where id=1");
            jdbc.update("update audit_log set username='董志强',module='知识库',detail='{\"file\":\"设计规范v2.3.pdf\"}'::jsonb where id=2");
            jdbc.update("update audit_log set username='喻春意',module='智能问答',detail='{\"question\":\"产品发布流程\"}'::jsonb where id=3");
            jdbc.update("update audit_log set username='孙可平',module='知识库' where id=4");
        } catch (Exception ignored) {
            // Database schema initialization may still be in progress; the next restart retries.
        }
    }

    private void updateLegacy(long id, String name, String... oldNames) {
        StringBuilder sql = new StringBuilder("update sys_dept set name=? where id=? and name in (");
        for (int i = 0; i < oldNames.length; i++) sql.append(i == 0 ? "?" : ",?");
        sql.append(")");
        Object[] args = new Object[oldNames.length + 2];
        args[0] = name;
        args[1] = id;
        System.arraycopy(oldNames, 0, args, 2, oldNames.length);
        jdbc.update(sql.toString(), args);
    }

    private void insertIfMissing(long id, String name, int sort) {
        jdbc.update("insert into sys_dept(id,parent_id,name,ancestors,sort,status) values(?,0,?,'0,',?,1) on conflict (id) do nothing", id, name, sort);
    }
}
