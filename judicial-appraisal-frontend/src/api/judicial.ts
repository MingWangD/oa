import { get, post, put } from './http';

export interface UserRole {
  id?: number | null;
  code: string;
  name: string;
  dataScope?: string | null;
  customDeptIds?: number[];
}

export interface UserInfo {
  id: number;
  username: string;
  realName: string;
  mobile: string | null;
  email: string | null;
  deptId: number | null;
  deptName: string | null;
  postId: number | null;
  postName: string | null;
  status: string;
  roles: UserRole[];
  permissions: string[];
}

export interface LoginResponse {
  token: string;
  user: UserInfo;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface AdminRole {
  id: number;
  roleCode: string;
  roleName: string;
  status: string;
  dataScope: string | null;
  customDeptIds: number[];
}

export interface RoleDataScopePayload {
  dataScope: string;
  deptIds?: number[];
}

export interface AdminUser {
  id: number;
  username: string;
  realName: string;
  mobile: string | null;
  email: string | null;
  deptId: number | null;
  deptName: string | null;
  postId: number | null;
  postName: string | null;
  status: string;
  roles: AdminRole[];
}

export interface AdminUserPayload {
  username: string;
  realName: string;
  password: string;
  mobile?: string;
  email?: string;
  deptId?: number | null;
  postId?: number | null;
  status?: string;
  roleIds?: number[];
}

export interface AdminUserUpdatePayload {
  realName: string;
  mobile?: string;
  email?: string;
  deptId?: number | null;
  postId?: number | null;
  status?: string;
}

export interface OrganizationDept {
  id: number;
  parentId: number | null;
  deptName: string;
  deptCode: string | null;
  sortNo: number | null;
  status: string;
}

export interface OrganizationPost {
  id: number;
  postName: string;
  postCode: string;
  sortNo: number | null;
  status: string;
}

export interface WorkbenchSummary {
  todoCount: number;
  doneCount: number;
  processingCount: number;
  overdueCount: number;
}

export interface TaskSummary {
  id: number;
  caseId: number;
  taskTitle: string;
  nodeCode: string;
  nodeName: string;
  status: string;
  assigneeId: number | null;
  assigneeName: string | null;
  deadlineTime: string | null;
}

export interface TaskDetail {
  id: number;
  caseId: number;
  caseNo: string | null;
  caseTitle: string;
  wfName: string | null;
  wfInstanceId: number | null;
  nodeInstanceId: number | null;
  taskType: string | null;
  taskTitle: string;
  nodeCode: string;
  nodeName: string;
  status: string;
  assigneeId: number | null;
  assigneeName: string | null;
  startedTime: string | null;
  completedTime: string | null;
  deadlineTime: string | null;
  overtimeFlag: number | null;
  resultAction: string | null;
  resultOpinion: string | null;
}

export interface CaseItem {
  id: number;
  caseNo: string | null;
  caseTitle: string;
  caseType: string | null;
  caseStatus: string | null;
  caseStatusName: string | null;
  currentNodeCode: string | null;
  currentNodeName: string | null;
  currentHandlerId: number | null;
  currentHandlerName: string | null;
  acceptDeptId: number | null;
  acceptDeptName: string | null;
  entrustOrgName: string | null;
  deadlineTime: string | null;
  urgentFlag: number | null;
  submittedTime: string | null;
  completedTime: string | null;
  createdTime: string | null;
}

export interface CaseDetail {
  id: number;
  caseNo: string | null;
  caseTitle: string;
  caseType: string | null;
  caseStatus: string | null;
  currentNodeCode: string | null;
  currentNodeName: string | null;
  currentHandlerId: number | null;
  currentHandlerName: string | null;
  acceptDeptId: number | null;
  acceptDeptName: string | null;
  entrustOrgName: string | null;
  deadlineTime: string | null;
  urgentFlag: number | null;
  overtimeFlag: number | null;
  submittedTime: string | null;
  completedTime: string | null;
  createdTime: string | null;
  updatedTime: string | null;
  version: number | null;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  pageNo: number;
  pageSize: number;
}

export interface CaseQuery {
  keyword?: string;
  caseStatus?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface CaseDraftPayload {
  caseTitle: string;
  caseType?: string;
  entrustOrgName?: string;
  acceptDeptId?: number | null;
}

export type WorkflowActionCode =
  | 'SUBMIT'
  | 'APPROVE'
  | 'COMPLETE'
  | 'RETURN'
  | 'TERMINATE'
  | 'REOPEN'
  | 'ASSIGN'
  | 'CLAIM'
  | 'WITHDRAW';

export interface WorkflowActionPayload {
  taskId?: number;
  actionCode: WorkflowActionCode;
  opinion?: string;
  reason?: string;
  assigneeId?: number;
  assigneeName?: string;
}

export interface WorkflowActionResult {
  caseId: number;
  taskId: number | null;
  actionCode: string;
  success: boolean;
  message: string;
}

export interface MenuDto {
  id: number;
  parentId: number;
  menuName: string;
  menuCode: string;
  path: string;
  component: string | null;
  menuType: string;
  icon: string;
  sortNo: number;
  children: MenuDto[];
}

export interface OaModule {
  code: string;
  name: string;
  scope: string;
  priority: string;
  implementationStatus: string;
  requiredCapabilities: string[];
}

export interface ReconstructionPhase {
  phase: string;
  goal: string;
  status: string;
  deliverables: string[];
}

export interface JudicialWorkflowDefinition {
  code: string;
  name: string;
  formCode: string;
  entryMode: string;
  roles: string[];
  keyRules: string[];
  nextFlows: string[];
}

export interface JudicialFormDefinition {
  code: string;
  name: string;
  alias: string;
  inputFiles: string[];
  outputFiles: string[];
  versionedArtifacts: string[];
}

export interface JudicialCatalog {
  workflowCount: number;
  formCount: number;
  dedicatedRoles: string[];
  workflows: JudicialWorkflowDefinition[];
  forms: JudicialFormDefinition[];
}

export interface FormDefinitionDesign {
  id: number;
  formCode: string;
  formName: string;
  category: string | null;
  currentPublishedVersion: number;
  enabled: number;
  createdTime: string | null;
  updatedTime: string | null;
}

export interface FormVersionDesign {
  id: number;
  formId: number;
  formCode: string;
  formName: string;
  versionNo: number;
  status: string;
  inputFilesJson: string | null;
  outputFilesJson: string | null;
  versionedArtifactsJson: string | null;
  fieldSchemaJson: string | null;
  layoutSchemaJson: string | null;
  validationSchemaJson: string | null;
  permissionSchemaJson: string | null;
  linkageSchemaJson: string | null;
  calculationSchemaJson: string | null;
  attachmentSchemaJson: string | null;
  subtableSchemaJson: string | null;
  notesJson: string | null;
  sourceVersionId: number | null;
  publishedBy: number | null;
  publishedTime: string | null;
  immutableFlag: number;
  createdTime: string | null;
  updatedTime: string | null;
}

export interface FormDesignPayload {
  formCode: string;
  formName: string;
  category?: string;
  inputFilesJson?: string;
  outputFilesJson?: string;
  versionedArtifactsJson?: string;
  fieldSchemaJson?: string;
  layoutSchemaJson?: string;
  validationSchemaJson?: string;
  permissionSchemaJson?: string;
  linkageSchemaJson?: string;
  calculationSchemaJson?: string;
  attachmentSchemaJson?: string;
  subtableSchemaJson?: string;
  notesJson?: string;
}

export interface WorkflowNodeDesign {
  id?: number;
  wfId?: number;
  nodeCode: string;
  nodeName: string;
  nodeType: string;
  taskType?: string | null;
  caseStatus?: string | null;
  handlerDeptRule?: string | null;
  handlerPostRule?: string | null;
  handlerRoleRule?: string | null;
  allowManualAssign?: number | null;
  timeoutHours?: number | null;
  configJson?: string | null;
  assigneeRuleJson?: string | null;
  formRuleJson?: string | null;
  permissionJson?: string | null;
  sortNo?: number | null;
  enabled?: number | null;
}

export interface WorkflowTransitionDesign {
  id?: number;
  wfId?: number;
  fromNodeCode: string;
  toNodeCode: string;
  actionCode: string;
  actionName: string;
  requireReason?: number | null;
  requireOpinion?: number | null;
  conditionExpression?: string | null;
  transitionConfigJson?: string | null;
  enabled?: number | null;
  sortNo?: number | null;
}

export interface WorkflowDefinitionDesign {
  id: number;
  wfCode: string;
  wfName: string;
  wfType: string;
  formCode: string | null;
  versionNo: number;
  enabled: number;
  publishStatus: string;
  remark: string | null;
  definitionJson: string | null;
  sourceWfId: number | null;
  publishedBy: number | null;
  publishedTime: string | null;
  immutableFlag: number;
  createdTime: string | null;
  updatedTime: string | null;
  nodes: WorkflowNodeDesign[];
  transitions: WorkflowTransitionDesign[];
}

export interface WorkflowVersionDesign {
  id: number;
  wfCode: string;
  wfName: string;
  wfType: string;
  formCode: string | null;
  versionNo: number;
  enabled: number;
  publishStatus: string;
  remark: string | null;
  definitionJson: string | null;
  sourceWfId: number | null;
  publishedBy: number | null;
  publishedTime: string | null;
  immutableFlag: number;
  createdTime: string | null;
  updatedTime: string | null;
}

export interface WorkflowDesignPayload {
  wfCode: string;
  wfName: string;
  wfType: string;
  formCode?: string;
  remark?: string;
  definitionJson?: string;
  nodes: WorkflowNodeDesign[];
  transitions: WorkflowTransitionDesign[];
}

export function login(username: string, password: string): Promise<LoginResponse> {
  return post<LoginResponse>('/auth/login', { username, password });
}

export function fetchCurrentUser(): Promise<UserInfo> {
  return get<UserInfo>('/auth/me');
}

export function logout(): Promise<void> {
  return post<void>('/auth/logout');
}

export function changePassword(payload: ChangePasswordPayload): Promise<void> {
  return post<void>('/auth/change-password', payload);
}

export function fetchAdminUsers(keyword?: string): Promise<AdminUser[]> {
  return get<AdminUser[]>('/admin/users', { keyword });
}

export function fetchAdminRoles(): Promise<AdminRole[]> {
  return get<AdminRole[]>('/admin/roles');
}

export function createAdminUser(payload: AdminUserPayload): Promise<AdminUser> {
  return post<AdminUser>('/admin/users', payload);
}

export function updateAdminUser(userId: number, payload: AdminUserUpdatePayload): Promise<AdminUser> {
  return put<AdminUser>(`/admin/users/${userId}`, payload);
}

export function assignAdminUserRoles(userId: number, roleIds: number[]): Promise<AdminUser> {
  return put<AdminUser>(`/admin/users/${userId}/roles`, { roleIds });
}

export function updateRoleDataScope(roleId: number, payload: RoleDataScopePayload): Promise<AdminRole> {
  return put<AdminRole>(`/admin/roles/${roleId}/data-scope`, payload);
}

export function fetchAdminDepts(): Promise<OrganizationDept[]> {
  return get<OrganizationDept[]>('/admin/depts');
}

export function fetchAdminPosts(): Promise<OrganizationPost[]> {
  return get<OrganizationPost[]>('/admin/posts');
}

export function fetchWorkbenchSummary(assigneeId?: number): Promise<WorkbenchSummary> {
  return get<WorkbenchSummary>('/workbench/summary', { assigneeId });
}

export function fetchWorkbenchTodo(assigneeId?: number): Promise<TaskSummary[]> {
  return get<TaskSummary[]>('/workbench/todo', { assigneeId });
}

export function fetchWorkbenchDone(assigneeId?: number): Promise<TaskSummary[]> {
  return get<TaskSummary[]>('/workbench/done', { assigneeId });
}

export function fetchCases(query: CaseQuery): Promise<PageResult<CaseItem>> {
  return get<PageResult<CaseItem>>('/cases', { ...query });
}

export function fetchCaseDetail(caseId: number): Promise<CaseDetail> {
  return get<CaseDetail>(`/cases/${caseId}`);
}

export function createCaseDraft(payload: CaseDraftPayload): Promise<CaseDetail> {
  return post<CaseDetail>('/cases', payload);
}

export function fetchTaskDetail(taskId: number): Promise<TaskDetail> {
  return get<TaskDetail>(`/tasks/${taskId}`);
}

export function fetchTaskDetailByCaseNode(caseId: number, nodeCode: string): Promise<TaskDetail> {
  return get<TaskDetail>('/tasks', { caseId, nodeCode });
}

export function submitWorkflowAction(
  caseId: number,
  payload: WorkflowActionPayload
): Promise<WorkflowActionResult> {
  return post<WorkflowActionResult>(`/cases/${caseId}/actions`, payload);
}

export function fetchPlatformMenus(): Promise<MenuDto[]> {
  return get<MenuDto[]>('/platform/menus');
}

export function fetchPlatformModules(): Promise<OaModule[]> {
  return get<OaModule[]>('/platform/modules');
}

export function fetchReconstructionPlan(): Promise<ReconstructionPhase[]> {
  return get<ReconstructionPhase[]>('/platform/reconstruction-plan');
}

export function fetchJudicialCatalog(): Promise<JudicialCatalog> {
  return get<JudicialCatalog>('/platform/judicial-catalog');
}

export function fetchFormDesigns(): Promise<FormDefinitionDesign[]> {
  return get<FormDefinitionDesign[]>('/designer/forms');
}

export function saveFormDraft(payload: FormDesignPayload): Promise<FormVersionDesign> {
  return post<FormVersionDesign>('/designer/forms/drafts', payload);
}

export function fetchFormDraft(formCode: string): Promise<FormVersionDesign> {
  return get<FormVersionDesign>(`/designer/forms/${formCode}/draft`);
}

export function fetchFormPreview(formCode: string): Promise<FormVersionDesign> {
  return get<FormVersionDesign>(`/designer/forms/${formCode}/preview`);
}

export function fetchFormVersions(formCode: string): Promise<FormVersionDesign[]> {
  return get<FormVersionDesign[]>(`/designer/forms/${formCode}/versions`);
}

export function publishForm(formCode: string): Promise<FormVersionDesign> {
  return post<FormVersionDesign>(`/designer/forms/${formCode}/publish`);
}

export function restoreFormVersion(formCode: string, versionNo: number): Promise<FormVersionDesign> {
  return post<FormVersionDesign>(`/designer/forms/${formCode}/versions/${versionNo}/restore`);
}

export function fetchWorkflowDesigns(): Promise<WorkflowDefinitionDesign[]> {
  return get<WorkflowDefinitionDesign[]>('/designer/workflows');
}

export function saveWorkflowDraft(payload: WorkflowDesignPayload): Promise<WorkflowDefinitionDesign> {
  return post<WorkflowDefinitionDesign>('/designer/workflows/drafts', payload);
}

export function fetchWorkflowDraft(wfCode: string): Promise<WorkflowDefinitionDesign> {
  return get<WorkflowDefinitionDesign>(`/designer/workflows/${wfCode}/draft`);
}

export function fetchWorkflowPreview(wfCode: string): Promise<WorkflowDefinitionDesign> {
  return get<WorkflowDefinitionDesign>(`/designer/workflows/${wfCode}/preview`);
}

export function fetchWorkflowVersions(wfCode: string): Promise<WorkflowVersionDesign[]> {
  return get<WorkflowVersionDesign[]>(`/designer/workflows/${wfCode}/versions`);
}

export function publishWorkflow(wfCode: string): Promise<WorkflowDefinitionDesign> {
  return post<WorkflowDefinitionDesign>(`/designer/workflows/${wfCode}/publish`);
}

export function restoreWorkflowVersion(wfCode: string, versionNo: number): Promise<WorkflowDefinitionDesign> {
  return post<WorkflowDefinitionDesign>(`/designer/workflows/${wfCode}/versions/${versionNo}/restore`);
}
