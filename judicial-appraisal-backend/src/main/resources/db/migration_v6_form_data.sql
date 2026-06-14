-- Add form_data column to case_info to persist the latest form data for the case
ALTER TABLE case_info ADD COLUMN form_data JSON NULL COMMENT '案件最新的表单数据';

-- Add form_data column to case_node_instance to persist the form data at the time of node completion
ALTER TABLE case_node_instance ADD COLUMN form_data JSON NULL COMMENT '节点完成时的表单数据快照';
