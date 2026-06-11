-- Phase 2: RBAC and Permission System

USE judicial_appraisal;

-- 菜单权限表
CREATE TABLE sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '菜单ID',
  parent_id BIGINT DEFAULT 0 COMMENT '上级菜单ID',
  menu_name VARCHAR(64) NOT NULL COMMENT '菜单名称',
  menu_code VARCHAR(64) NULL COMMENT '权限编码 (如 sys:user:view)',
  path VARCHAR(255) NULL COMMENT '路由路径',
  component VARCHAR(255) NULL COMMENT '前端组件路径',
  menu_type CHAR(1) NOT NULL COMMENT '菜单类型：M目录 C菜单 F按钮',
  icon VARCHAR(64) DEFAULT '#' COMMENT '菜单图标',
  sort_no INT DEFAULT 0 COMMENT '显示顺序',
  status VARCHAR(32) DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_sys_menu_parent (parent_id),
  KEY idx_sys_menu_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单权限表';

-- 角色菜单关联表
CREATE TABLE sys_role_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  menu_id BIGINT NOT NULL COMMENT '菜单ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_sys_role_menu (role_id, menu_id),
  KEY idx_sys_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单关联表';

-- 角色表增加数据权限范围字段
ALTER TABLE sys_role ADD COLUMN data_scope VARCHAR(32) DEFAULT 'all' COMMENT '数据权限范围：all全部, dept本部门, dept_sub本部门及下级, self本人, custom自定义';

-- 预置一些基础菜单数据 (根据 PlatformCatalogService 中的定义)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, path, component, menu_type, icon, sort_no) VALUES
(1, 0, '流程中心', 'workflow', '/my-work', NULL, 'M', 'Operation', 10),
(2, 1, '新建工作', 'workflow:new', '/case/new', 'NewWorkView', 'C', 'Plus', 10),
(3, 1, '我的工作', 'workflow:mine', '/my-work', 'WorkbenchView', 'C', 'List', 20),
(4, 1, '工作查询', 'workflow:query', '/work-query', 'CaseListView', 'C', 'Search', 30),
(5, 0, '知识管理', 'knowledge', '/knowledge', NULL, 'M', 'Notebook', 20),
(6, 5, '知识库', 'knowledge:base', '/knowledge', 'KnowledgeBaseView', 'C', 'Files', 10),
(7, 0, '系统管理', 'system', '/admin', NULL, 'M', 'Setting', 100),
(8, 7, '用户管理', 'system:user', '/admin/users', 'UserManagementView', 'C', 'User', 10),
(9, 7, '权限管理', 'system:permission', '/placeholder/system/permission', 'PlaceholderView', 'C', 'Key', 20);

-- 为 ADMIN 角色关联所有菜单，按角色编码定位，避免依赖固定主键
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
CROSS JOIN sys_menu m
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.deleted = 0;
