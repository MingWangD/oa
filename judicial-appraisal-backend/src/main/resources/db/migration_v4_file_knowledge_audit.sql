CREATE TABLE IF NOT EXISTS file_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通用文件版本ID',
  biz_type VARCHAR(64) NOT NULL COMMENT '业务类型：case/task/document/knowledge',
  biz_id BIGINT NULL COMMENT '业务对象ID',
  case_id BIGINT NULL COMMENT '案件ID',
  node_code VARCHAR(64) NULL COMMENT '节点编码',
  task_id BIGINT NULL COMMENT '任务ID',
  artifact_code VARCHAR(128) NULL COMMENT '产物编码',
  artifact_name VARCHAR(255) NULL COMMENT '产物名称',
  version_no INT NOT NULL COMMENT '版本号',
  file_id BIGINT NOT NULL COMMENT '文件ID',
  change_note VARCHAR(1000) NULL COMMENT '变更说明',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_file_version_biz (biz_type, biz_id, artifact_code, version_no),
  KEY idx_file_version_case (case_id),
  KEY idx_file_version_file (file_id),
  KEY idx_file_version_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通用文件版本表';

CREATE TABLE IF NOT EXISTS knowledge_directory (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识目录ID',
  parent_id BIGINT NULL COMMENT '父目录ID',
  directory_code VARCHAR(128) NOT NULL COMMENT '目录编码',
  directory_name VARCHAR(255) NOT NULL COMMENT '目录名称',
  directory_type VARCHAR(32) NOT NULL DEFAULT 'public' COMMENT '目录类型：personal/dept/public/case',
  case_id BIGINT NULL COMMENT '案件ID',
  dept_id BIGINT NULL COMMENT '部门ID',
  owner_user_id BIGINT NULL COMMENT '个人目录所有者',
  path VARCHAR(1000) NULL COMMENT '目录路径',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  UNIQUE KEY uk_knowledge_directory_code (directory_code),
  KEY idx_knowledge_directory_parent (parent_id),
  KEY idx_knowledge_directory_case (case_id),
  KEY idx_knowledge_directory_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库目录表';

CREATE TABLE IF NOT EXISTS knowledge_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识文档ID',
  directory_id BIGINT NOT NULL COMMENT '目录ID',
  case_id BIGINT NULL COMMENT '案件ID',
  title VARCHAR(255) NOT NULL COMMENT '标题',
  artifact_code VARCHAR(128) NULL COMMENT '产物编码',
  source_type VARCHAR(64) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/workflow/archive',
  wf_instance_id BIGINT NULL COMMENT '流程实例ID',
  node_code VARCHAR(64) NULL COMMENT '节点编码',
  node_name VARCHAR(128) NULL COMMENT '节点名称',
  task_id BIGINT NULL COMMENT '任务ID',
  current_file_id BIGINT NULL COMMENT '当前文件ID',
  current_version_no INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
  form_snapshot_json LONGTEXT NULL COMMENT '表单快照JSON',
  archive_result_json LONGTEXT NULL COMMENT '归档结果JSON',
  status VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  KEY idx_knowledge_document_directory (directory_id),
  KEY idx_knowledge_document_case (case_id),
  KEY idx_knowledge_document_node (node_code),
  KEY idx_knowledge_document_file (current_file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库文档表';

CREATE TABLE IF NOT EXISTS knowledge_document_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识文档版本ID',
  document_id BIGINT NOT NULL COMMENT '文档ID',
  version_no INT NOT NULL COMMENT '版本号',
  file_id BIGINT NULL COMMENT '文件ID',
  form_snapshot_json LONGTEXT NULL COMMENT '表单快照JSON',
  archive_result_json LONGTEXT NULL COMMENT '归档结果JSON',
  change_note VARCHAR(1000) NULL COMMENT '变更说明',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_knowledge_document_version (document_id, version_no),
  KEY idx_knowledge_document_version_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库文档版本表';

CREATE TABLE IF NOT EXISTS knowledge_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '知识权限ID',
  directory_id BIGINT NULL COMMENT '目录ID',
  document_id BIGINT NULL COMMENT '文档ID',
  subject_type VARCHAR(32) NOT NULL COMMENT '主体类型：all/user/role/dept',
  subject_id BIGINT NULL COMMENT '主体ID',
  permission_code VARCHAR(32) NOT NULL COMMENT '权限：read/download/write/manage',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_knowledge_permission (directory_id, document_id, subject_type, subject_id, permission_code),
  KEY idx_knowledge_permission_directory (directory_id),
  KEY idx_knowledge_permission_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='知识库权限表';

CREATE TABLE IF NOT EXISTS audit_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '审计事件ID',
  action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
  action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
  biz_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  biz_id BIGINT NULL COMMENT '业务ID',
  case_id BIGINT NULL COMMENT '案件ID',
  operator_id BIGINT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NULL COMMENT '操作人姓名',
  result_status VARCHAR(32) NOT NULL DEFAULT 'success' COMMENT '结果',
  detail_json LONGTEXT NULL COMMENT '详情JSON',
  ip_address VARCHAR(64) NULL COMMENT 'IP地址',
  user_agent VARCHAR(512) NULL COMMENT 'User-Agent',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_audit_event_action (action_code),
  KEY idx_audit_event_biz (biz_type, biz_id),
  KEY idx_audit_event_case (case_id),
  KEY idx_audit_event_operator (operator_id),
  KEY idx_audit_event_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='不可变审计事件表';

CREATE TABLE IF NOT EXISTS case_archive_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '案件归档记录ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  case_no VARCHAR(64) NULL COMMENT '案件编号',
  wf_instance_id BIGINT NULL COMMENT '流程实例ID',
  node_code VARCHAR(64) NULL COMMENT '节点编码',
  node_name VARCHAR(128) NULL COMMENT '节点名称',
  task_id BIGINT NULL COMMENT '任务ID',
  document_id BIGINT NULL COMMENT '知识文档ID',
  archive_type VARCHAR(64) NOT NULL DEFAULT 'node',
  archive_status VARCHAR(32) NOT NULL DEFAULT 'archived',
  archive_summary VARCHAR(1000) NULL COMMENT '归档摘要',
  archived_by BIGINT NULL COMMENT '归档人ID',
  archived_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间',
  KEY idx_case_archive_case (case_id),
  KEY idx_case_archive_node (node_code),
  KEY idx_case_archive_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件自动归档记录表';
