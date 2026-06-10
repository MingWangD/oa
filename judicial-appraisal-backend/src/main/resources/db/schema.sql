-- Generated from E:/tongda/docs/judicial-appraisal-database-sql.md

CREATE DATABASE IF NOT EXISTS judicial_appraisal
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE judicial_appraisal;

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  real_name VARCHAR(64) NOT NULL COMMENT '真实姓名',
  mobile VARCHAR(32) NULL COMMENT '手机号',
  email VARCHAR(128) NULL COMMENT '邮箱',
  dept_id BIGINT NULL COMMENT '所属部门ID',
  post_id BIGINT NULL COMMENT '主岗位ID',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  last_login_time DATETIME NULL COMMENT '最后登录时间',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_dept_id (dept_id),
  KEY idx_sys_user_post_id (post_id),
  KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户表';

CREATE TABLE sys_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '部门ID',
  parent_id BIGINT NULL COMMENT '上级部门ID',
  dept_name VARCHAR(128) NOT NULL COMMENT '部门名称',
  dept_code VARCHAR(64) NULL COMMENT '部门编码',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_sys_dept_code (dept_code),
  KEY idx_sys_dept_parent_id (parent_id),
  KEY idx_sys_dept_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门表';

CREATE TABLE sys_post (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '岗位ID',
  post_name VARCHAR(128) NOT NULL COMMENT '岗位名称',
  post_code VARCHAR(64) NOT NULL COMMENT '岗位编码',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_sys_post_code (post_code),
  KEY idx_sys_post_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='岗位表';

CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
  role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
  role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
  status VARCHAR(32) NOT NULL DEFAULT 'enabled' COMMENT '状态：enabled/disabled',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_sys_role_code (role_code),
  KEY idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色表';

CREATE TABLE sys_user_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  role_id BIGINT NOT NULL COMMENT '角色ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_sys_user_role (user_id, role_id),
  KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联表';

CREATE TABLE case_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '案件ID',
  case_no VARCHAR(64) NULL COMMENT '案件编号，提交后生成',
  case_title VARCHAR(255) NOT NULL COMMENT '案件标题',
  case_type VARCHAR(64) NULL COMMENT '案件类型',
  case_status VARCHAR(64) NOT NULL DEFAULT 'DRAFT' COMMENT '案件状态',
  current_node_code VARCHAR(64) NULL COMMENT '当前节点编码',
  current_node_name VARCHAR(128) NULL COMMENT '当前节点名称',
  current_handler_id BIGINT NULL COMMENT '当前承办人ID',
  current_handler_name VARCHAR(64) NULL COMMENT '当前承办人姓名',
  accept_dept_id BIGINT NULL COMMENT '承办部门ID',
  accept_dept_name VARCHAR(128) NULL COMMENT '承办部门名称',
  entrust_org_name VARCHAR(255) NULL COMMENT '委托单位名称',
  entrust_contact_name VARCHAR(64) NULL COMMENT '委托联系人',
  entrust_contact_phone VARCHAR(32) NULL COMMENT '委托联系人电话',
  entrust_date DATE NULL COMMENT '委托日期',
  accepted_time DATETIME NULL COMMENT '受理时间',
  deadline_time DATETIME NULL COMMENT '办理截止时间',
  urgent_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否加急：0否 1是',
  overtime_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否超时：0否 1是',
  submitted_time DATETIME NULL COMMENT '提交时间',
  completed_time DATETIME NULL COMMENT '办结时间',
  archived_time DATETIME NULL COMMENT '归档时间',
  terminated_time DATETIME NULL COMMENT '终止时间',
  remark VARCHAR(1000) NULL COMMENT '备注',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_case_info_case_no (case_no),
  KEY idx_case_info_status (case_status),
  KEY idx_case_info_current_node (current_node_code),
  KEY idx_case_info_handler (current_handler_id),
  KEY idx_case_info_accept_dept (accept_dept_id),
  KEY idx_case_info_created_time (created_time),
  KEY idx_case_info_deadline_time (deadline_time),
  KEY idx_case_info_entrust_org (entrust_org_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件主表';

CREATE TABLE case_party (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '当事人ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  party_type VARCHAR(64) NOT NULL COMMENT '当事人类型',
  party_name VARCHAR(128) NOT NULL COMMENT '当事人名称/姓名',
  id_type VARCHAR(64) NULL COMMENT '证件类型',
  id_no VARCHAR(128) NULL COMMENT '证件号码',
  contact_phone VARCHAR(32) NULL COMMENT '联系电话',
  address VARCHAR(255) NULL COMMENT '联系地址',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_case_party_case_id (case_id),
  KEY idx_case_party_party_name (party_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件当事人表';

CREATE TABLE case_appraisal_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鉴定事项ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  item_type VARCHAR(64) NOT NULL COMMENT '鉴定事项类型',
  item_name VARCHAR(255) NOT NULL COMMENT '鉴定事项名称',
  item_content TEXT NULL COMMENT '鉴定事项内容',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_case_appraisal_item_case_id (case_id),
  KEY idx_case_appraisal_item_type (item_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件鉴定事项表';

CREATE TABLE wf_definition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流程定义ID',
  wf_code VARCHAR(64) NOT NULL COMMENT '流程编码',
  wf_name VARCHAR(128) NOT NULL COMMENT '流程名称',
  wf_type VARCHAR(64) NOT NULL COMMENT '流程类型：main/subflow',
  version_no INT NOT NULL DEFAULT 1 COMMENT '版本号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否 1是',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_wf_definition_code_version (wf_code, version_no),
  KEY idx_wf_definition_type (wf_type),
  KEY idx_wf_definition_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程定义表';

CREATE TABLE wf_node_def (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '节点定义ID',
  wf_id BIGINT NOT NULL COMMENT '流程定义ID',
  node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
  node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
  node_type VARCHAR(64) NOT NULL COMMENT '节点类型：start/task/review/parallel/end',
  task_type VARCHAR(64) NOT NULL DEFAULT 'single' COMMENT '任务类型：single/candidate/parallel',
  case_status VARCHAR(64) NULL COMMENT '进入该节点时案件状态',
  handler_dept_rule VARCHAR(255) NULL COMMENT '承办部门规则',
  handler_post_rule VARCHAR(255) NULL COMMENT '承办岗位规则',
  handler_role_rule VARCHAR(255) NULL COMMENT '承办角色规则',
  allow_manual_assign TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许人工指定承办人',
  timeout_hours INT NULL COMMENT '节点超时时限小时数',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否 1是',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_wf_node_def_wf_node (wf_id, node_code),
  KEY idx_wf_node_def_wf_id (wf_id),
  KEY idx_wf_node_def_node_type (node_type),
  KEY idx_wf_node_def_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程节点定义表';

CREATE TABLE wf_transition_def (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流转定义ID',
  wf_id BIGINT NOT NULL COMMENT '流程定义ID',
  from_node_code VARCHAR(64) NOT NULL COMMENT '来源节点编码',
  to_node_code VARCHAR(64) NOT NULL COMMENT '目标节点编码',
  action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
  action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
  require_reason TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须填写原因：0否 1是',
  require_opinion TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须填写意见：0否 1是',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否 1是',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_wf_transition_wf_from (wf_id, from_node_code),
  KEY idx_wf_transition_wf_action (wf_id, action_code),
  KEY idx_wf_transition_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='流程流转定义表';

CREATE TABLE case_wf_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流程实例ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  wf_id BIGINT NOT NULL COMMENT '流程定义ID',
  wf_code VARCHAR(64) NOT NULL COMMENT '流程编码',
  wf_name VARCHAR(128) NOT NULL COMMENT '流程名称',
  status VARCHAR(64) NOT NULL DEFAULT 'running' COMMENT '流程状态',
  current_node_code VARCHAR(64) NULL COMMENT '当前节点编码',
  current_node_name VARCHAR(128) NULL COMMENT '当前节点名称',
  started_by BIGINT NULL COMMENT '发起人ID',
  started_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  completed_time DATETIME NULL COMMENT '完成时间',
  terminated_time DATETIME NULL COMMENT '终止时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_case_wf_instance_case_id (case_id),
  KEY idx_case_wf_instance_status (status),
  KEY idx_case_wf_instance_current_node (current_node_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件主流程实例表';

CREATE TABLE case_subflow_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '子流程实例ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  parent_wf_instance_id BIGINT NOT NULL COMMENT '主流程实例ID',
  wf_id BIGINT NOT NULL COMMENT '子流程定义ID',
  subflow_type VARCHAR(64) NOT NULL COMMENT '子流程类型',
  status VARCHAR(64) NOT NULL DEFAULT 'running' COMMENT '子流程状态',
  started_by BIGINT NULL COMMENT '发起人ID',
  started_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  completed_time DATETIME NULL COMMENT '完成时间',
  terminated_time DATETIME NULL COMMENT '终止时间',
  reason VARCHAR(1000) NULL COMMENT '发起原因',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_case_subflow_case_id (case_id),
  KEY idx_case_subflow_parent (parent_wf_instance_id),
  KEY idx_case_subflow_type (subflow_type),
  KEY idx_case_subflow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件子流程实例表';

CREATE TABLE case_node_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '节点实例ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  wf_instance_id BIGINT NULL COMMENT '主流程实例ID',
  subflow_instance_id BIGINT NULL COMMENT '子流程实例ID',
  node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
  node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
  status VARCHAR(64) NOT NULL DEFAULT 'running' COMMENT '节点状态：running/completed/cancelled',
  started_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  completed_time DATETIME NULL COMMENT '完成时间',
  handler_id BIGINT NULL COMMENT '节点实际办理人ID',
  handler_name VARCHAR(64) NULL COMMENT '节点实际办理人姓名',
  result_action VARCHAR(64) NULL COMMENT '完成动作',
  result_opinion VARCHAR(1000) NULL COMMENT '办理意见',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_case_node_case_id (case_id),
  KEY idx_case_node_wf_instance (wf_instance_id),
  KEY idx_case_node_subflow (subflow_instance_id),
  KEY idx_case_node_node_code (node_code),
  KEY idx_case_node_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件节点实例表';

CREATE TABLE case_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  wf_instance_id BIGINT NULL COMMENT '主流程实例ID',
  subflow_instance_id BIGINT NULL COMMENT '子流程实例ID',
  node_instance_id BIGINT NOT NULL COMMENT '节点实例ID',
  task_type VARCHAR(64) NOT NULL COMMENT '任务类型：single/candidate/parallel',
  task_title VARCHAR(255) NOT NULL COMMENT '任务标题',
  node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
  node_name VARCHAR(128) NOT NULL COMMENT '节点名称',
  status VARCHAR(64) NOT NULL DEFAULT 'pending' COMMENT '任务状态',
  assignee_id BIGINT NULL COMMENT '办理人ID',
  assignee_name VARCHAR(64) NULL COMMENT '办理人姓名',
  claimed_by BIGINT NULL COMMENT '认领人ID',
  claimed_time DATETIME NULL COMMENT '认领时间',
  started_time DATETIME NULL COMMENT '开始办理时间',
  completed_time DATETIME NULL COMMENT '完成时间',
  deadline_time DATETIME NULL COMMENT '截止时间',
  overtime_flag TINYINT NOT NULL DEFAULT 0 COMMENT '是否超时：0否 1是',
  result_action VARCHAR(64) NULL COMMENT '完成动作',
  result_opinion VARCHAR(1000) NULL COMMENT '办理意见',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  KEY idx_case_task_case_id (case_id),
  KEY idx_case_task_assignee_status (assignee_id, status),
  KEY idx_case_task_node_instance (node_instance_id),
  KEY idx_case_task_status (status),
  KEY idx_case_task_deadline (deadline_time),
  KEY idx_case_task_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件任务表';

CREATE TABLE case_task_candidate (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '候选ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  candidate_user_id BIGINT NULL COMMENT '候选用户ID',
  candidate_dept_id BIGINT NULL COMMENT '候选部门ID',
  candidate_post_id BIGINT NULL COMMENT '候选岗位ID',
  candidate_role_id BIGINT NULL COMMENT '候选角色ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_task_candidate_task_id (task_id),
  KEY idx_task_candidate_case_id (case_id),
  KEY idx_task_candidate_user (candidate_user_id),
  KEY idx_task_candidate_dept_post (candidate_dept_id, candidate_post_id),
  KEY idx_task_candidate_role (candidate_role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务候选人表';

CREATE TABLE case_task_opinion (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '意见ID',
  task_id BIGINT NOT NULL COMMENT '任务ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  node_code VARCHAR(64) NOT NULL COMMENT '节点编码',
  action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
  opinion VARCHAR(2000) NULL COMMENT '办理意见',
  reason VARCHAR(1000) NULL COMMENT '原因',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_task_opinion_task_id (task_id),
  KEY idx_task_opinion_case_id (case_id),
  KEY idx_task_opinion_node_code (node_code),
  KEY idx_task_opinion_operated_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务办理意见表';

CREATE TABLE case_material (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '材料ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  material_no VARCHAR(64) NULL COMMENT '材料编号',
  material_name VARCHAR(255) NOT NULL COMMENT '材料名称',
  material_type VARCHAR(64) NULL COMMENT '材料类型',
  source_type VARCHAR(64) NULL COMMENT '来源类型',
  receive_time DATETIME NULL COMMENT '接收时间',
  receive_user_id BIGINT NULL COMMENT '接收人ID',
  receive_user_name VARCHAR(64) NULL COMMENT '接收人姓名',
  status VARCHAR(64) NOT NULL DEFAULT 'received' COMMENT '材料状态',
  current_holder_id BIGINT NULL COMMENT '当前持有人ID',
  current_holder_name VARCHAR(64) NULL COMMENT '当前持有人姓名',
  quantity INT NULL COMMENT '数量',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_case_material_case_id (case_id),
  KEY idx_case_material_no (material_no),
  KEY idx_case_material_status (status),
  KEY idx_case_material_holder (current_holder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件材料台账表';

CREATE TABLE case_evidence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '鉴材ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  evidence_no VARCHAR(64) NULL COMMENT '鉴材编号',
  evidence_name VARCHAR(255) NOT NULL COMMENT '鉴材名称',
  evidence_type VARCHAR(64) NULL COMMENT '鉴材类型',
  receive_time DATETIME NULL COMMENT '接收时间',
  receive_user_id BIGINT NULL COMMENT '接收人ID',
  receive_user_name VARCHAR(64) NULL COMMENT '接收人姓名',
  status VARCHAR(64) NOT NULL DEFAULT 'received' COMMENT '鉴材状态',
  current_holder_id BIGINT NULL COMMENT '当前持有人ID',
  current_holder_name VARCHAR(64) NULL COMMENT '当前持有人姓名',
  storage_location VARCHAR(255) NULL COMMENT '存放位置',
  return_time DATETIME NULL COMMENT '归还时间',
  return_receiver VARCHAR(128) NULL COMMENT '归还接收人',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_case_evidence_case_id (case_id),
  KEY idx_case_evidence_no (evidence_no),
  KEY idx_case_evidence_status (status),
  KEY idx_case_evidence_holder (current_holder_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件鉴材台账表';

CREATE TABLE case_document (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文书ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  document_no VARCHAR(64) NULL COMMENT '文书编号',
  document_name VARCHAR(255) NOT NULL COMMENT '文书名称',
  document_type VARCHAR(64) NOT NULL COMMENT '文书类型',
  source_type VARCHAR(64) NOT NULL COMMENT '来源类型：generated/uploaded',
  template_id BIGINT NULL COMMENT '模板ID',
  file_id BIGINT NULL COMMENT '当前文件ID',
  version_no INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
  status VARCHAR(64) NOT NULL DEFAULT 'draft' COMMENT '文书状态',
  submit_review_time DATETIME NULL COMMENT '提交审核时间',
  issued_time DATETIME NULL COMMENT '签发时间',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_case_document_case_id (case_id),
  KEY idx_case_document_no (document_no),
  KEY idx_case_document_type (document_type),
  KEY idx_case_document_status (status),
  KEY idx_case_document_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件文书台账表';

CREATE TABLE case_document_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文书版本ID',
  document_id BIGINT NOT NULL COMMENT '文书ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  version_no INT NOT NULL COMMENT '版本号',
  file_id BIGINT NOT NULL COMMENT '文件ID',
  change_note VARCHAR(1000) NULL COMMENT '变更说明',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_document_version (document_id, version_no),
  KEY idx_document_version_case_id (case_id),
  KEY idx_document_version_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文书版本表';

CREATE TABLE doc_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板ID',
  template_name VARCHAR(255) NOT NULL COMMENT '模板名称',
  template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
  doc_type VARCHAR(64) NOT NULL COMMENT '文书类型',
  file_id BIGINT NULL COMMENT '模板文件ID',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否 1是',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_doc_template_code (template_code),
  KEY idx_doc_template_type (doc_type),
  KEY idx_doc_template_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='文书模板表';

CREATE TABLE sys_file (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
  original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
  file_name VARCHAR(255) NOT NULL COMMENT '存储文件名',
  file_ext VARCHAR(32) NULL COMMENT '文件扩展名',
  content_type VARCHAR(128) NULL COMMENT '内容类型',
  file_size BIGINT NOT NULL COMMENT '文件大小',
  storage_bucket VARCHAR(128) NOT NULL COMMENT '存储桶',
  storage_key VARCHAR(512) NOT NULL COMMENT '存储Key',
  md5 VARCHAR(64) NULL COMMENT '文件MD5',
  upload_user_id BIGINT NULL COMMENT '上传人ID',
  upload_user_name VARCHAR(64) NULL COMMENT '上传人姓名',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_sys_file_storage_key (storage_key),
  KEY idx_sys_file_upload_user (upload_user_id),
  KEY idx_sys_file_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统文件表';

CREATE TABLE case_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '附件关系ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  biz_type VARCHAR(64) NOT NULL COMMENT '业务类型：case/material/evidence/document/task',
  biz_id BIGINT NULL COMMENT '业务对象ID',
  file_id BIGINT NOT NULL COMMENT '文件ID',
  attachment_name VARCHAR(255) NULL COMMENT '附件名称',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  KEY idx_case_attachment_case_id (case_id),
  KEY idx_case_attachment_biz (biz_type, biz_id),
  KEY idx_case_attachment_file_id (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件附件关系表';

CREATE TABLE case_transfer_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '流转日志ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  wf_instance_id BIGINT NULL COMMENT '流程实例ID',
  subflow_instance_id BIGINT NULL COMMENT '子流程实例ID',
  from_node_code VARCHAR(64) NULL COMMENT '来源节点编码',
  from_node_name VARCHAR(128) NULL COMMENT '来源节点名称',
  to_node_code VARCHAR(64) NULL COMMENT '目标节点编码',
  to_node_name VARCHAR(128) NULL COMMENT '目标节点名称',
  action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
  action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  opinion VARCHAR(2000) NULL COMMENT '办理意见',
  reason VARCHAR(1000) NULL COMMENT '原因',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_transfer_log_case_id (case_id),
  KEY idx_transfer_log_wf_instance (wf_instance_id),
  KEY idx_transfer_log_action (action_code),
  KEY idx_transfer_log_operated_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件流程流转日志表';

CREATE TABLE case_action_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '操作日志ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  biz_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  biz_id BIGINT NULL COMMENT '业务对象ID',
  action_code VARCHAR(64) NOT NULL COMMENT '动作编码',
  action_name VARCHAR(128) NOT NULL COMMENT '动作名称',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  request_data JSON NULL COMMENT '请求数据',
  result_data JSON NULL COMMENT '结果数据',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_action_log_case_id (case_id),
  KEY idx_action_log_biz (biz_type, biz_id),
  KEY idx_action_log_action (action_code),
  KEY idx_action_log_operated_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件操作日志表';

CREATE TABLE case_status_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '状态日志ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  old_status VARCHAR(64) NULL COMMENT '原案件状态',
  new_status VARCHAR(64) NOT NULL COMMENT '新案件状态',
  old_node_code VARCHAR(64) NULL COMMENT '原节点编码',
  new_node_code VARCHAR(64) NULL COMMENT '新节点编码',
  change_reason VARCHAR(1000) NULL COMMENT '变更原因',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  changed_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  KEY idx_status_log_case_id (case_id),
  KEY idx_status_log_new_status (new_status),
  KEY idx_status_log_changed_time (changed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件状态日志表';

CREATE TABLE case_withdraw_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '撤回日志ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  wf_instance_id BIGINT NULL COMMENT '流程实例ID',
  from_node_code VARCHAR(64) NULL COMMENT '撤回前节点编码',
  target_node_code VARCHAR(64) NULL COMMENT '撤回目标节点编码',
  reason VARCHAR(1000) NOT NULL COMMENT '撤回原因',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_withdraw_log_case_id (case_id),
  KEY idx_withdraw_log_operated_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件撤回日志表';

CREATE TABLE case_reopen_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '重开日志ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  old_status VARCHAR(64) NOT NULL COMMENT '重开前状态',
  reopen_node_code VARCHAR(64) NULL COMMENT '重开节点编码',
  reason VARCHAR(1000) NOT NULL COMMENT '重开原因',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_reopen_log_case_id (case_id),
  KEY idx_reopen_log_operated_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='案件重开日志表';

CREATE TABLE ledger_transfer_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '台账流转日志ID',
  case_id BIGINT NOT NULL COMMENT '案件ID',
  ledger_type VARCHAR(64) NOT NULL COMMENT '台账类型：material/evidence/document',
  ledger_id BIGINT NOT NULL COMMENT '台账对象ID',
  from_holder_id BIGINT NULL COMMENT '原持有人ID',
  from_holder_name VARCHAR(64) NULL COMMENT '原持有人姓名',
  to_holder_id BIGINT NULL COMMENT '新持有人ID',
  to_holder_name VARCHAR(64) NULL COMMENT '新持有人姓名',
  transfer_action VARCHAR(64) NOT NULL COMMENT '流转动作',
  reason VARCHAR(1000) NULL COMMENT '流转原因',
  operator_id BIGINT NOT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人姓名',
  operated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_ledger_transfer_case_id (case_id),
  KEY idx_ledger_transfer_ledger (ledger_type, ledger_id),
  KEY idx_ledger_transfer_operated_time (operated_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='台账流转日志表';

CREATE TABLE sys_dict_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典类型ID',
  dict_code VARCHAR(64) NOT NULL COMMENT '字典编码',
  dict_name VARCHAR(128) NOT NULL COMMENT '字典名称',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_sys_dict_type_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典类型表';

CREATE TABLE sys_dict_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '字典项ID',
  dict_type_id BIGINT NOT NULL COMMENT '字典类型ID',
  item_label VARCHAR(128) NOT NULL COMMENT '字典项名称',
  item_value VARCHAR(128) NOT NULL COMMENT '字典项值',
  sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0否 1是',
  remark VARCHAR(1000) NULL COMMENT '备注',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除：0否 1是',
  UNIQUE KEY uk_sys_dict_item_type_value (dict_type_id, item_value),
  KEY idx_sys_dict_item_type_id (dict_type_id),
  KEY idx_sys_dict_item_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字典项表';

INSERT INTO sys_dict_type (dict_code, dict_name) VALUES
('case_status', '案件状态'),
('task_status', '任务状态'),
('task_type', '任务类型'),
('action_code', '流程动作'),
('material_status', '材料状态'),
('evidence_status', '鉴材状态'),
('document_status', '文书状态'),
('document_source_type', '文书来源类型');

INSERT INTO sys_dict_item (dict_type_id, item_label, item_value, sort_no)
SELECT id, '草稿', 'DRAFT', 10 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '待受理', 'TO_ACCEPT', 20 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '受理审核中', 'ACCEPT_REVIEWING', 30 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '不受理', 'REJECTED_ACCEPTANCE', 40 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '待补正/待补充', 'CORRECTION_PENDING', 50 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '办理中', 'PROCESSING', 60 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '复核/审核中', 'REVIEWING', 70 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '文书签发中', 'DOC_ISSUING', 80 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '已办结', 'COMPLETED', 90 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '已归档', 'ARCHIVED', 100 FROM sys_dict_type WHERE dict_code = 'case_status'
UNION ALL SELECT id, '已终止', 'TERMINATED', 110 FROM sys_dict_type WHERE dict_code = 'case_status';
