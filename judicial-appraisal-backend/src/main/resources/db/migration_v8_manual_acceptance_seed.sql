-- Phase 8: manual acceptance users, roles and menu permissions.

USE judicial_appraisal;

SET @default_password_hash := '$2a$10$BG8YJcV8SBK2q25psaHxUew7PfBCms1kpOnntM3QkHRDRz6WSb736';

INSERT INTO sys_dept (dept_name, dept_code, sort_no, status)
VALUES
('司法鉴定所', 'JUDICIAL_APPRAISAL_OFFICE', 10, 'enabled'),
('综合业务部', 'BUSINESS_DEPARTMENT', 20, 'enabled'),
('鉴定业务部', 'APPRAISAL_DEPARTMENT', 30, 'enabled'),
('档案室', 'ARCHIVE_DEPARTMENT', 40, 'enabled'),
('财务室', 'FINANCE_DEPARTMENT', 50, 'enabled')
ON DUPLICATE KEY UPDATE dept_name = VALUES(dept_name), status = VALUES(status), deleted = 0;

INSERT INTO sys_post (post_name, post_code, sort_no, status)
VALUES
('系统管理员', 'SYSTEM_ADMIN', 10, 'enabled'),
('收案员', 'CASE_ACCEPTOR', 20, 'enabled'),
('项目负责人', 'PROJECT_LEADER', 30, 'enabled'),
('项目辅助人', 'PROJECT_ASSISTANT', 40, 'enabled'),
('部门负责人', 'DEPT_LEADER', 50, 'enabled'),
('技术负责人', 'TECH_LEADER', 60, 'enabled'),
('审阅所长', 'DIRECTOR_REVIEW', 70, 'enabled'),
('档案管理员', 'ARCHIVIST', 80, 'enabled'),
('中心档案管理员', 'CENTER_ARCHIVIST', 90, 'enabled'),
('综合业务部', 'BUSINESS_COMPREHENSIVE', 100, 'enabled'),
('财务', 'FINANCE', 110, 'enabled')
ON DUPLICATE KEY UPDATE post_name = VALUES(post_name), status = VALUES(status), deleted = 0;

INSERT INTO sys_role (role_name, role_code, status, data_scope)
VALUES
('系统管理员', 'ADMIN', 'enabled', 'all'),
('收案员', 'CASE_ACCEPTOR', 'enabled', 'self'),
('项目负责人', 'PROJECT_LEADER', 'enabled', 'self'),
('项目辅助人', 'PROJECT_ASSISTANT', 'enabled', 'self'),
('部门负责人', 'DEPT_LEADER', 'enabled', 'dept'),
('技术负责人', 'TECH_LEADER', 'enabled', 'dept'),
('审阅所长', 'DIRECTOR_REVIEW', 'enabled', 'all'),
('档案管理员', 'ARCHIVIST', 'enabled', 'dept'),
('中心档案管理员', 'CENTER_ARCHIVIST', 'enabled', 'dept'),
('综合业务部', 'BUSINESS_COMPREHENSIVE', 'enabled', 'dept'),
('财务', 'FINANCE', 'enabled', 'dept'),
('收件人', 'RECEIVER', 'enabled', 'self'),
('申请人', 'APPLICANT', 'enabled', 'self'),
('发起人', 'INITIATOR', 'enabled', 'self'),
('盖章经办人', 'SEAL_OPERATOR', 'enabled', 'dept'),
('邮寄人员', 'MAIL_CLERK', 'enabled', 'dept')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), status = VALUES(status), data_scope = VALUES(data_scope), deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'admin', @default_password_hash, '系统管理员', d.id, p.id, 'enabled', 0
FROM sys_dept d
JOIN sys_post p ON p.post_code = 'SYSTEM_ADMIN'
WHERE d.dept_code = 'JUDICIAL_APPRAISAL_OFFICE'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'case_acceptor', @default_password_hash, '收案员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'CASE_ACCEPTOR'
WHERE d.dept_code = 'BUSINESS_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'project_leader', @default_password_hash, '项目负责人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'PROJECT_LEADER'
WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'project_assistant', @default_password_hash, '项目辅助人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'PROJECT_ASSISTANT'
WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'dept_leader', @default_password_hash, '部门负责人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'DEPT_LEADER'
WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'tech_leader', @default_password_hash, '技术负责人', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'TECH_LEADER'
WHERE d.dept_code = 'APPRAISAL_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'director_review', @default_password_hash, '审阅所长', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'DIRECTOR_REVIEW'
WHERE d.dept_code = 'JUDICIAL_APPRAISAL_OFFICE'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'archivist', @default_password_hash, '档案管理员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'ARCHIVIST'
WHERE d.dept_code = 'ARCHIVE_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'center_archivist', @default_password_hash, '中心档案管理员', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'CENTER_ARCHIVIST'
WHERE d.dept_code = 'ARCHIVE_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'business_staff', @default_password_hash, '综合业务部', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'BUSINESS_COMPREHENSIVE'
WHERE d.dept_code = 'BUSINESS_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT INTO sys_user (username, password_hash, real_name, dept_id, post_id, status, deleted)
SELECT 'finance', @default_password_hash, '财务', d.id, p.id, 'enabled', 0
FROM sys_dept d JOIN sys_post p ON p.post_code = 'FINANCE'
WHERE d.dept_code = 'FINANCE_DEPARTMENT'
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name), password_hash = VALUES(password_hash), dept_id = VALUES(dept_id), post_id = VALUES(post_id), status = 'enabled', deleted = 0;

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'ADMIN'
WHERE u.username = 'admin';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('CASE_ACCEPTOR', 'RECEIVER', 'APPLICANT', 'INITIATOR')
WHERE u.username = 'case_acceptor';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('PROJECT_LEADER', 'APPLICANT', 'INITIATOR')
WHERE u.username = 'project_leader';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'PROJECT_ASSISTANT'
WHERE u.username = 'project_assistant';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'DEPT_LEADER'
WHERE u.username = 'dept_leader';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'TECH_LEADER'
WHERE u.username = 'tech_leader';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'DIRECTOR_REVIEW'
WHERE u.username = 'director_review';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('ARCHIVIST', 'SEAL_OPERATOR', 'MAIL_CLERK')
WHERE u.username = 'archivist';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code = 'CENTER_ARCHIVIST'
WHERE u.username = 'center_archivist';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('BUSINESS_COMPREHENSIVE', 'APPLICANT', 'INITIATOR')
WHERE u.username = 'business_staff';

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id
FROM sys_user u
JOIN sys_role r ON r.role_code IN ('FINANCE', 'APPLICANT', 'INITIATOR')
WHERE u.username = 'finance';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, path, component, menu_type, icon, sort_no)
VALUES (14, 1, '数据报表', 'workflow:report', '/placeholder/workflow/report', 'PlaceholderView', 'C', 'DataAnalysis', 40);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.deleted = 0;

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (1, 2, 3, 4, 5, 6, 14)
WHERE r.role_code <> 'ADMIN'
  AND r.deleted = 0
  AND m.deleted = 0;
