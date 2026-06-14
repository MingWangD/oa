ALTER TABLE case_subflow_instance
  ADD COLUMN parent_task_id BIGINT NULL COMMENT '触发子流程的父任务ID' AFTER parent_wf_instance_id,
  ADD COLUMN parent_node_code VARCHAR(64) NULL COMMENT '触发子流程的父节点编码' AFTER parent_task_id,
  ADD COLUMN wf_code VARCHAR(64) NULL COMMENT '子流程编码' AFTER wf_id,
  ADD COLUMN wf_name VARCHAR(128) NULL COMMENT '子流程名称' AFTER wf_code,
  ADD KEY idx_case_subflow_parent_task (parent_task_id),
  ADD KEY idx_case_subflow_wf_code (wf_code);
