CREATE TABLE IF NOT EXISTS contract_info (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '合同ID',
  contract_no VARCHAR(64) NOT NULL COMMENT '合同编号',
  contract_name VARCHAR(255) NOT NULL COMMENT '合同名称',
  customer_name VARCHAR(255) NOT NULL COMMENT '客户名称',
  related_case_id BIGINT NULL COMMENT '关联案件ID',
  amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '合同金额',
  owner_id BIGINT NULL COMMENT '负责人ID',
  owner_name VARCHAR(64) NULL COMMENT '负责人姓名',
  department_id BIGINT NULL COMMENT '所属部门ID',
  department_name VARCHAR(128) NULL COMMENT '所属部门名称',
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED/ARCHIVED',
  archive_document_id BIGINT NULL COMMENT '归档知识文档ID',
  review_opinion VARCHAR(1000) NULL COMMENT '审核意见',
  submitted_at DATETIME NULL COMMENT '提交时间',
  approved_at DATETIME NULL COMMENT '审批通过时间',
  archived_at DATETIME NULL COMMENT '归档时间',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_by BIGINT NULL COMMENT '更新人ID',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  UNIQUE KEY uk_contract_info_no (contract_no),
  KEY idx_contract_info_status (status),
  KEY idx_contract_info_owner (owner_id),
  KEY idx_contract_info_dept (department_id),
  KEY idx_contract_info_case (related_case_id),
  KEY idx_contract_info_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同主表';

CREATE TABLE IF NOT EXISTS contract_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '合同版本ID',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  version_no INT NOT NULL COMMENT '版本号',
  title VARCHAR(255) NOT NULL COMMENT '版本标题',
  content LONGTEXT NULL COMMENT '合同内容快照',
  change_note VARCHAR(1000) NULL COMMENT '变更说明',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  UNIQUE KEY uk_contract_version (contract_id, version_no),
  KEY idx_contract_version_contract (contract_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同版本表';

CREATE TABLE IF NOT EXISTS contract_attachment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '合同附件ID',
  contract_id BIGINT NOT NULL COMMENT '合同ID',
  file_id BIGINT NOT NULL COMMENT '文件ID',
  file_name VARCHAR(255) NULL COMMENT '文件名',
  artifact_code VARCHAR(128) NULL COMMENT '产物编码',
  created_by BIGINT NULL COMMENT '创建人ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '是否删除',
  UNIQUE KEY uk_contract_attachment_file (contract_id, file_id),
  KEY idx_contract_attachment_contract (contract_id),
  KEY idx_contract_attachment_file (file_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同附件关联表';
