const roleCodeLabels: Record<string, string> = {
  ADMIN: '系统管理员',
  ROLE_ADMIN: '系统管理员',
  CASE_ACCEPTOR: '收案员',
  PROJECT_LEADER: '项目负责人',
  PROJECT_ASSISTANT: '项目辅助人',
  DEPT_LEADER: '部门负责人',
  TECH_LEADER: '技术负责人',
  DIRECTOR_REVIEW: '审阅所长',
  ARCHIVIST: '档案管理员',
  CENTER_ARCHIVIST: '中心档案管理员',
  BUSINESS_COMPREHENSIVE: '综合业务部',
  FINANCE: '财务',
  RECEIVER: '收件人',
  APPLICANT: '申请人',
  SEAL_OPERATOR: '盖章经办人',
  MAIL_CLERK: '邮寄人员',
  INITIATOR: '发起人',
  AUTH_APPROVER: '授权审批人'
};

const workflowCodeLabels: Record<string, string> = {
  'received-entrust': '收到委托书',
  'preliminary-survey': '初步勘验',
  'payment-notice': '发交费通知书及相关函件',
  'quality-control': '编制内部质量控制文件',
  'field-survey': '现场勘验',
  'material-receive-return': '材料接收与返还',
  'draft-opinion-review': '征求意见稿送审稿编制',
  'final-opinion-review': '鉴定意见书送审稿编制',
  'issue-opinion': '出具鉴定意见书',
  'issue-draft-opinion': '出具征求意见稿',
  'court-letter': '收到法院相关函件',
  'court-appearance': '出庭',
  'reject-acceptance': '不予受理',
  'withdraw-case-letter': '收到撤案函',
  refund: '退费',
  'terminate-appraisal': '终止鉴定',
  archive: '归档',
  'seal-application': '用章流程',
  'expense-reimbursement': '财务报销'
};

const formCodeLabels: Record<string, string> = {
  received_entrust: '委托受理表',
  preliminary_survey: '初步勘验表',
  payment_notice: '交费通知相关表',
  quality_control: '内部质量控制表',
  field_survey: '现场勘验表',
  material_receive_return: '材料接收与返还表',
  draft_opinion_review: '征求意见稿送审稿表',
  final_opinion_review: '鉴定意见书送审稿表',
  issue_opinion: '鉴定意见书出具表',
  issue_draft_opinion: '征求意见稿出具表',
  court_letter: '法院函件处理表',
  court_appearance: '出庭处理表',
  reject_acceptance: '不予受理处理表',
  withdraw_case_letter: '撤案函处理表',
  refund: '退费处理表',
  terminate_appraisal: '终止鉴定处理表',
  archive: '归档处理表',
  seal_application: '用章申请表',
  expense_reimbursement: '财务报销表'
};

const statusCodeLabels: Record<string, string> = {
  DRAFT: '草稿',
  PROCESSING: '办理中',
  COMPLETED: '已完成',
  ARCHIVED: '已归档',
  TERMINATED: '已终止',
  REJECTED: '已驳回',
  REJECTED_ACCEPTANCE: '受理退回',
  CORRECTION_PENDING: '补正中',
  PENDING: '待处理',
  TODO: '待办',
  DONE: '已办',
  OVERDUE: '已超期',
  REVIEW: '审核中',
  UNDER_REVIEW: '审核中',
  APPROVED: '已通过',
  ENABLED: '启用',
  DISABLED: '禁用',
  ACTIVE: '启用',
  INACTIVE: '停用'
};

function normalizeCode(value: string | null | undefined): string {
  return String(value ?? '').trim();
}

export function formatRoleCode(value: string | null | undefined): string {
  const code = normalizeCode(value);
  return roleCodeLabels[code.toUpperCase()] || code || '-';
}

export function formatRoleNames(values: Array<string | null | undefined> | null | undefined): string {
  const labels = (values || []).map(formatRoleCode).filter((item) => item && item !== '-');
  return labels.length > 0 ? labels.join('、') : '-';
}

export function formatWorkflowCode(value: string | null | undefined): string {
  const code = normalizeCode(value);
  return workflowCodeLabels[code] || code || '-';
}

export function formatFormCode(value: string | null | undefined): string {
  const code = normalizeCode(value);
  return formCodeLabels[code] || code.replace(/[-_]/g, ' ') || '-';
}

export function formatStatusCode(value: string | null | undefined): string {
  const code = normalizeCode(value);
  return statusCodeLabels[code.toUpperCase()] || code || '-';
}
