-- Phase 8: manual acceptance users, roles and menu permissions.

USE judicial_appraisal;

SET @default_password_hash := '$2a$10$BG8YJcV8SBK2q25psaHxUew7PfBCms1kpOnntM3QkHRDRz6WSb736';

-- 1. 更新部门信息
INSERT INTO sys_dept (dept_name, dept_code, sort_no, status)
VALUES
('司法鉴定所', 'JUDICIAL_APPRAISAL_OFFICE', 10, 'enabled'),
('综合业务部', 'BUSINESS_DEPARTMENT', 20, 'enabled'),
('鉴定业务部', 'APPRAISAL_DEPARTMENT', 30, 'enabled'),
('档案室', 'ARCHIVE_DEPARTMENT', 40, 'enabled'),
('财务室', 'FINANCE_DEPARTMENT', 50, 'enabled')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name), status = VALUES(status), deleted = 0;

-- 2. 更新岗位信息（对齐手册）
INSERT INTO sys_post (post_name, post_code, sort_no, status)
VALUES
('系统管理员', 'SYSTEM_ADMIN', 10, 'enabled'),
('收案员', 'CASE_ACCEPTOR', 20, 'enabled'),
('项目负责人', 'PROJECT_LEADER', 30, 'enabled'),
('项目辅助人', 'PROJECT_ASSISTANT', 40, 'enabled'),
('部门负责人', 'DEPT_LEADER', 50, 'enabled'),
('技术负责人', 'TECH_LEADER', 60, 'enabled'),
('档案管理员', 'ARCHIVIST', 80, 'enabled'),
('中心档案管理员', 'CENTER_ARCHIVIST', 90, 'enabled'),
('业务人员', 'BUSINESS_STAFF', 100, 'enabled'),
('财务人员', 'FINANCE_STAFF', 110, 'enabled')
ON DUPLICATE KEY UPDATE post_name = VALUES(post_name), post_code = VALUES(post_code), status = VALUES(status), deleted = 0;

-- 3. 更新角色信息（清理冗余，完全对齐手册）
INSERT INTO sys_role (role_name, role_code, status, data_scope)
VALUES
('系统管理员', 'ADMIN', 'enabled', 'all'),
('收案员', 'CASE_ACCEPTOR', 'enabled', 'self'),
('项目负责人', 'PROJECT_LEADER', 'enabled', 'self'),
('项目辅助人', 'PROJECT_ASSISTANT', 'enabled', 'self'),
('部门负责人', 'DEPT_LEADER', 'enabled', 'dept'),
('技术负责人', 'TECH_LEADER', 'enabled', 'dept'),
('档案管理员', 'ARCHIVIST', 'enabled', 'dept'),
('中心档案管理员', 'CENTER_ARCHIVIST', 'enabled', 'dept'),
('业务人员', 'BUSINESS_STAFF', 'enabled', 'dept'),
('财务人员', 'FINANCE', 'enabled', 'dept'),
('普通用户', 'COMMON_USER', 'enabled', 'self')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), status = VALUES(status), data_scope = VALUES(data_scope), deleted = 0;

-- 4. 更新用户信息
INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'admin', @default_password_hash, '系统管理员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'SYSTEM_ADMIN' WHERE d.dept_code = 'JUDICIAL_APPRAISAL_OFFICE'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'case_acceptor', @default_password_hash, '收案员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'CASE_ACCEPTOR' WHERE d.dept_code = 'BUSINESS_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'project_leader', @default_password_hash, '项目负责人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'PROJECT_LEADER' WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'project_assistant', @default_password_hash, '项目辅助人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'PROJECT_ASSISTANT' WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'dept_leader', @default_password_hash, '部门负责人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'DEPT_LEADER' WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'tech_leader', @default_password_hash, '技术负责人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'TECH_LEADER' WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'archivist', @default_password_hash, '档案管理员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'ARCHIVIST' WHERE d.dept_code = 'ARCHIVE_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'center_archivist', @default_password_hash, '中心档案管理员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'CENTER_ARCHIVIST' WHERE d.dept_code = 'ARCHIVE_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'business_staff', @default_password_hash, '业务人员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'BUSINESS_STAFF' WHERE d.dept_code = 'BUSINESS_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'finance', @default_password_hash, '财务人员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'FINANCE_STAFF' WHERE d.dept_code = 'FINANCE_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), status = 'enabled', deleted = 0;

-- 5. 更新用户角色关联（简洁化，移除冗余后缀）
TRUNCATE TABLE sys_user_role;

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'admin' AND r.role_code = 'ADMIN';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'case_acceptor' AND r.role_code = 'CASE_ACCEPTOR';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'project_leader' AND r.role_code = 'PROJECT_LEADER';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'project_assistant' AND r.role_code = 'PROJECT_ASSISTANT';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'dept_leader' AND r.role_code = 'DEPT_LEADER';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'tech_leader' AND r.role_code = 'TECH_LEADER';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'archivist' AND r.role_code = 'ARCHIVIST';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'center_archivist' AND r.role_code = 'CENTER_ARCHIVIST';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'business_staff' AND r.role_code = 'BUSINESS_STAFF';

INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'finance' AND r.role_code = 'FINANCE';

-- 6. 更新菜单权限
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, path, component, menu_type, icon, sort_no)
VALUES (14, 1, '数据报表', 'workflow:report', '/work-report', 'ReportCenterView', 'C', 'DataAnalysis', 40);

TRUNCATE TABLE sys_role_menu;
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r CROSS JOIN sys_menu m WHERE r.role_code = 'ADMIN' AND r.deleted = 0 AND m.deleted = 0;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.id IN (1, 2, 3, 4, 5, 6, 14) WHERE r.role_code <> 'ADMIN' AND r.deleted = 0 AND m.deleted = 0;
