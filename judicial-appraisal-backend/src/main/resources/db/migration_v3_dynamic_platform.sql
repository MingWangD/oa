-- Phase 2: dynamic form and workflow design platform

USE judicial_appraisal;

ALTER TABLE wf_definition
  ADD COLUMN form_code VARCHAR(64) NULL COMMENT '关联表单编码' AFTER wf_type,
  ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT '发布状态：draft/published/archived' AFTER enabled,
  ADD COLUMN definition_json LONGTEXT NULL COMMENT '流程全局配置JSON' AFTER remark,
  ADD COLUMN source_wf_id BIGINT NULL COMMENT '来源流程定义ID' AFTER definition_json,
  ADD COLUMN published_by BIGINT NULL COMMENT '发布人ID' AFTER source_wf_id,
  ADD COLUMN published_time DATETIME NULL COMMENT '发布时间' AFTER published_by,
  ADD COLUMN immutable_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否不可变版本' AFTER published_time;

UPDATE wf_definition
SET publish_status = 'published',
    immutable_flag = 1
WHERE version_no > 0
  AND deleted = 0;

ALTER TABLE wf_node_def
  ADD COLUMN config_json LONGTEXT NULL COMMENT '节点扩展配置JSON' AFTER timeout_hours,
  ADD COLUMN assignee_rule_json LONGTEXT NULL COMMENT '办理人规则JSON' AFTER config_json,
  ADD COLUMN form_rule_json LONGTEXT NULL COMMENT '表单字段权限/规则JSON' AFTER assignee_rule_json,
  ADD COLUMN permission_json LONGTEXT NULL COMMENT '节点权限JSON' AFTER form_rule_json;

ALTER TABLE wf_transition_def
  ADD COLUMN condition_expression LONGTEXT NULL COMMENT '条件表达式' AFTER require_opinion,
  ADD COLUMN transition_config_json LONGTEXT NULL COMMENT '流转扩展配置JSON' AFTER condition_expression;

CREATE TABLE IF NOT EXISTS form_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '表单定义ID',
  form_code VARCHAR(64) NOT NULL COMMENT '表单编码',
  form_name VARCHAR(128) NOT NULL COMMENT '表单名称',
  category VARCHAR(64) NULL COMMENT '分类',
  current_published_version INT NOT NULL DEFAULT 0 COMMENT '当前已发布版本号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否 1是',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_form_definition_code (form_code),
  KEY idx_form_definition_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态表单定义表';

CREATE TABLE IF NOT EXISTS form_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '表单版本ID',
  form_id BIGINT NOT NULL COMMENT '表单定义ID',
  form_code VARCHAR(64) NOT NULL COMMENT '表单编码',
  form_name VARCHAR(128) NOT NULL COMMENT '表单名称',
  version_no INT NOT NULL DEFAULT 0 COMMENT '版本号：0为草稿',
  status VARCHAR(32) NOT NULL DEFAULT 'draft' COMMENT '状态：draft/published/archived',
  input_files_json LONGTEXT NULL COMMENT '输入文件JSON',
  output_files_json LONGTEXT NULL COMMENT '输出文件JSON',
  versioned_artifacts_json LONGTEXT NULL COMMENT '需保留版本产物JSON',
  field_schema_json LONGTEXT NULL COMMENT '字段定义JSON',
  layout_schema_json LONGTEXT NULL COMMENT '布局定义JSON',
  validation_schema_json LONGTEXT NULL COMMENT '校验规则JSON',
  permission_schema_json LONGTEXT NULL COMMENT '字段权限JSON',
  linkage_schema_json LONGTEXT NULL COMMENT '联动规则JSON',
  calculation_schema_json LONGTEXT NULL COMMENT '计算规则JSON',
  attachment_schema_json LONGTEXT NULL COMMENT '附件规则JSON',
  subtable_schema_json LONGTEXT NULL COMMENT '子表规则JSON',
  notes_json LONGTEXT NULL COMMENT '注意事项JSON',
  source_version_id BIGINT NULL COMMENT '来源版本ID',
  published_by BIGINT NULL COMMENT '发布人ID',
  published_time DATETIME NULL COMMENT '发布时间',
  immutable_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否不可变版本',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_form_version_code_status (form_code, status),
  KEY idx_form_version_code_version (form_code, version_no),
  KEY idx_form_version_form_id (form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='动态表单版本表';

CREATE TABLE IF NOT EXISTS sys_role_data_scope_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  dept_id BIGINT NOT NULL COMMENT '部门ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_role_data_scope_dept (role_id, dept_id),
  KEY idx_role_data_scope_dept_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色自定义数据权限部门表';

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_code, path, component, menu_type, icon, sort_no) VALUES
(10, 1, '设计表单', 'workflow:form-design', '/workflow/forms', 'WorkflowFormDesignerView', 'C', 'EditPen', 110),
(11, 10, '发布表单', 'workflow:form-publish', NULL, NULL, 'F', '#', 111),
(12, 1, '设计流程', 'workflow:process-design', '/workflow/processes', 'WorkflowProcessDesignerView', 'C', 'Share', 120),
(13, 12, '发布流程', 'workflow:process-publish', NULL, NULL, 'F', '#', 121);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
JOIN sys_menu m ON m.id IN (10, 11, 12, 13)
WHERE r.role_code = 'ADMIN'
  AND r.deleted = 0
  AND m.deleted = 0;
