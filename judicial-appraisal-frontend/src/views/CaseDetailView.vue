<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  fetchCaseDetail,
  fetchCaseWorkflowView,
  fetchCaseSubflows,
  fetchJudicialCatalog,
  fetchRuntimeFormPreview,
  fetchTaskDetail,
  fetchTaskDetailByCaseNode,
  saveCaseFormData,
  submitWorkflowAction,
  uploadWorkflowFile,
  fetchUserOptions,
  type CaseDetail,
  type CaseWorkflowView,
  type CaseSubflowSummary,
  type FileUploadResponse,
  type FormVersionDesign,
  type JudicialFormDefinition,
  type TaskDetail,
  type WorkflowActionCode,
  type AdminUser
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';
import CaseDynamicForm from '../components/case/CaseDynamicForm.vue';
import CaseActionBar from '../components/case/CaseActionBar.vue';
import {
  getWorkflowFieldAuth,
  getWorkflowFieldKey,
  isWorkflowFieldHidden,
  resolveWorkflowFieldReadonly,
  resolveWorkflowFieldRequired
} from '../utils/workflowFieldRules';

interface DynamicFormField {
  key: string;
  label: string;
  type: string;
  group: string;
  required: boolean;
  readonly: boolean;
  options: string[];
}

interface RuntimeTransitionOption {
  key: string;
  value: WorkflowActionCode;
  label: string;
  targetNode?: string;
  condition?: string | null;
}

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const acting = ref(false);
const saving = ref(false);
const detail = ref<CaseDetail | null>(null);
const workflowView = ref<CaseWorkflowView | null>(null);
const flowchartVisible = ref(false);
const activeFlowchartTab = ref<'current' | 'panorama' | 'detailed'>('current');
const subflows = ref<CaseSubflowSummary[]>([]);
const forms = ref<JudicialFormDefinition[]>([]);
const currentTask = ref<TaskDetail | null>(null);
const formPreview = ref<FormVersionDesign | null>(null);
const userOptions = ref<AdminUser[]>([]);
const formData = ref<Record<string, unknown>>({});
const uploadedFiles = ref<FileUploadResponse[]>([]);
const opinion = ref('');
const selectedTransition = ref<string>('SUBMIT');

const caseId = computed(() => Number(route.params.id));
const returnPath = computed(() => (typeof route.query.from === 'string' ? route.query.from : ''));
const returnLabel = computed(() => (typeof route.query.fromLabel === 'string' ? route.query.fromLabel : '上一页'));
const sourceBoard = computed(() => (typeof route.query.fromBoard === 'string' ? route.query.fromBoard : ''));
const routeTaskId = computed(() => Number(route.query.taskId));
const pageMode = computed(() => (typeof route.query.mode === 'string' ? route.query.mode : ''));
const readonlyMode = computed(() => pageMode.value === 'readonly' || route.query.readonly === '1');
const hasEntrustOrg = computed(() => Boolean(detail.value?.entrustOrgName));
const hasStatus = computed(() => Boolean(detail.value?.caseStatus));
const isDraftCase = computed(() => detail.value?.caseStatus === 'DRAFT');

const canHandle = computed(() => {
  if (!detail.value) {
    return false;
  }
  if (readonlyMode.value || currentTask.value?.status === 'completed') {
    return false;
  }
  if (detail.value.caseStatus === 'COMPLETED' || detail.value.caseStatus === 'TERMINATED') {
    return false;
  }
  if (detail.value.caseStatus !== 'DRAFT' && !currentTask.value) {
    return false;
  }
  // 如果任务已经明确指派给特定处理人，且该处理人不是当前登录用户，则当前用户不能办理
  if (detail.value.currentHandlerId && detail.value.currentHandlerId !== authStore.user?.id) {
    return false;
  }

  // 草稿案件且没有当前任务时，默认允许起草人（或管理员）办理
  if (isDraftCase.value) {
    return authStore.isAdmin || !detail.value.currentHandlerId || detail.value.currentHandlerId === authStore.user?.id;
  }

  // 非草稿案件，必须有当前任务
  if (currentTask.value) {
    // 如果已经指派处理人，则只有该处理人能处理
    if (detail.value.currentHandlerId) {
      return detail.value.currentHandlerId === authStore.user?.id;
    }
    // 如果任务还未指派处理人（待领取状态），校验当前用户是否为候选人或拥有候选角色
    const candidateUsers = currentTask.value.candidateUserIds || [];
    const candidateRoles = currentTask.value.candidateRoleIds || [];
    const userRoles = authStore.user?.roles || [];

    const hasCandidateUser = candidateUsers.length > 0;
    const hasCandidateRole = candidateRoles.length > 0;

    // 如果该任务没有任何候选限制（防呆设计，一般不应该发生），如果没有限制则意味着需要由系统自动流转或者管理员介入
    if (!hasCandidateUser && !hasCandidateRole) {
      return false;
    }

    const isUserMatch = hasCandidateUser && authStore.user?.id && candidateUsers.includes(authStore.user.id);
    const isRoleMatch = hasCandidateRole && userRoles.some((role) => role.id && candidateRoles.includes(role.id));

    return Boolean(isUserMatch) || Boolean(isRoleMatch);
  }

  return false;
});
const currentForm = computed(() => {
  const current = detail.value;
  if (!current) {
    return null;
  }
  return forms.value.find((form) => [form.name, form.alias, form.code].filter(Boolean).some((value) => value === current.caseType))
    ?? forms.value.find((form) => current.caseType?.includes(form.name) || form.name.includes(current.caseType ?? ''))
    ?? null;
});
const activeNodeCode = computed(() => currentTask.value?.nodeCode || detail.value?.currentNodeCode || '');
const activeFormCode = computed(() => currentTask.value?.formCode || currentForm.value?.code || formPreview.value?.formCode || '');
const isReceivedEntrustFillStage = computed(() =>
  activeNodeCode.value === 'INIT_FILL' ||
  (isDraftCase.value && activeFormCode.value === 'received-entrust')
);

function mergeFieldAuthDefaults(
  parsed: Record<string, any>,
  defaults: Record<string, Record<string, unknown>>,
  overrides: Record<string, Record<string, unknown>> = {}
): Record<string, any> {
  const sourceAuth = parsed.fieldAuth || {};
  return {
    ...parsed,
    fieldAuth: Object.fromEntries(
      Array.from(new Set([...Object.keys(defaults), ...Object.keys(sourceAuth), ...Object.keys(overrides)])).map((fieldName) => [
        fieldName,
        {
          ...(defaults[fieldName] || {}),
          ...(sourceAuth[fieldName] || {}),
          ...(overrides[fieldName] || {})
        }
      ])
    )
  };
}

const formRule = computed(() => {
  const parsed = currentTask.value?.formRuleJson ? parseJson<Record<string, any>>(currentTask.value.formRuleJson, {}) : {};
  if (isReceivedEntrustFillStage.value) {
    const requiredFields = [
      'receivedDate',
      'filingDate',
      'clientName',
      'caseNo',
      'undertakingLegalPerson',
      'institutionSelectionMethod',
      'institutionSelectionTime',
      'appraisalCategory',
      'applicantName',
      'respondentName',
      'urgencyLevel',
      'caseChannel',
      'appraisalMatter'
    ];
    return mergeFieldAuthDefaults(
      parsed,
      Object.fromEntries(requiredFields.map((fieldName) => [fieldName, { required: true, readonly: false }])),
      {
        expressNo: { required: false, readonly: false },
        projectAmount: { required: false, readonly: false }
      }
    );
  }
  if (activeFormCode.value === 'received-entrust') {
    const registrationBaseFields = [
      'expressNo',
      'projectAmount',
      'receivedDate',
      'filingDate',
      'clientName',
      'caseNo',
      'undertakingLegalPerson',
      'institutionSelectionMethod',
      'institutionSelectionTime',
      'appraisalCategory',
      'applicantName',
      'respondentName',
      'urgencyLevel',
      'caseChannel',
      'appraisalMatter'
    ];
    const assignmentFields = [
      'entrustAccepted',
      'departmentHeadId',
      'projectLeaderId',
      'projectAssistantId'
    ];
    
    const projectDecisionFields = [
      'preliminarySurveyRequired',
      'materialReceiveRequired'
    ];
    
    const nodeOverrides: Record<string, Record<string, unknown>> = {};
    assignmentFields.forEach((fieldName) => {
      nodeOverrides[fieldName] = { readonly: true };
    });
    if (activeNodeCode.value === 'CLERK_REGISTER') {
      nodeOverrides['departmentHeadId'] = { required: true, readonly: false, hidden: false };
    } else if (activeNodeCode.value === 'DEPT_REVIEW') {
      nodeOverrides['entrustAccepted'] = { required: true, readonly: false };
      nodeOverrides['departmentHeadId'] = { required: false, readonly: true };
      nodeOverrides['projectLeaderId'] = { required: true, readonly: false };
      nodeOverrides['projectAssistantId'] = { required: false, readonly: true, hidden: true };
    } else if (activeNodeCode.value === 'PROJECT_DECISION') {
      nodeOverrides['departmentHeadId'] = { required: false, readonly: true };
      nodeOverrides['projectLeaderId'] = { required: false, readonly: true };
      nodeOverrides['projectAssistantId'] = { required: true, readonly: false };
    } else {
      nodeOverrides['projectAssistantId'] = { required: false, readonly: true };
    }

    if (activeNodeCode.value !== 'PROJECT_DECISION') {
      projectDecisionFields.forEach((fieldName) => {
        nodeOverrides[fieldName] = { readonly: true };
      });
    }

    return mergeFieldAuthDefaults(
      parsed,
      {},
      {
        ...Object.fromEntries(registrationBaseFields.map((fieldName) => [fieldName, { required: false, readonly: true }])),
        ...nodeOverrides
      }
    );
  }
  if (activeNodeCode.value === 'DEPT_REVIEW') {
    return mergeFieldAuthDefaults(parsed, {
      entrustAccepted: { required: true }
    });
  }
  return parsed;
});

const dynamicFields = computed<DynamicFormField[]>(() => {
  const fields = parseJson<Array<Record<string, unknown>>>(formPreview.value?.fieldSchemaJson, []);
  const fieldAuth = formRule.value?.fieldAuth || {};
  const permissionSchema = parseJson<{ groups?: Record<string, { readOnly?: boolean; roles?: string[] }> }>(
    formPreview.value?.permissionSchemaJson,
    {}
  );

  return fields
    .filter((field) => {
      const key = getWorkflowFieldKey(field);
      if (isWorkflowFieldHidden(key, fieldAuth)) {
        return false;
      }
      if (isDraftCase.value && (field.group === '流程基础' || field.group === '受理决策' || field.group === '项目决策')) {
        return false;
      }
      // 如果部门负责人明确选择不予受理，则隐藏后续指定人和项目决策字段，不作必填校验。
      // 布尔开关未选择时不能按 false 处理，否则收案员节点会误隐藏“指定部门负责人”。
      if (activeNodeCode.value === 'DEPT_REVIEW' &&
          formData.value?.entrustAccepted === false &&
          (key === 'preliminarySurveyRequired' ||
           key === 'materialReceiveRequired' ||
           key === 'projectLeaderId' ||
           key === 'projectAssistantId' ||
           key === 'departmentHeadId')) {
        return false;
      }
      return key !== 'handlerOpinion';
    })
    .map((field, index) => {
      const key = getWorkflowFieldKey(field, `field_${index + 1}`);
      const auth = getWorkflowFieldAuth(key, fieldAuth);
      let isReadonly = resolveWorkflowFieldReadonly(field, auth, formRule.value?.readonly);
      const fieldAuthAllowsEdit = auth.readonly === false || auth.readOnly === false;
      const isAssignmentField = key === 'departmentHeadId' || key === 'projectLeaderId' || key === 'projectAssistantId';

      // Check group-level permissions (e.g., role restrictions)
      const groupName = String(field.group || '');
      const groupConfig = permissionSchema?.groups?.[groupName];
      if (groupConfig && !(isAssignmentField && fieldAuthAllowsEdit)) {
        if (groupConfig.readOnly) {
          isReadonly = true;
        }
        if (groupConfig.roles && groupConfig.roles.length > 0) {
          const userRoles = authStore.user?.roles || [];
          const hasRole = groupConfig.roles.some((roleName) =>
            userRoles.some(
              (r: any) =>
                r.roleName === roleName ||
                r.roleCode === roleName ||
                r.name === roleName ||
                r.code === roleName
            )
          );
          if (!hasRole && !authStore.isAdmin) {
            isReadonly = true;
          }
        }
      }

      let isRequired = resolveWorkflowFieldRequired(field, auth, formRule.value?.required);

      // Dynamic validation linkage:
      if (key === 'supplementaryNotice' && formData.value?.requireSupplementaryMaterial === true) {
        isRequired = true;
      }
      if ((key === 'returnReceiver' || key === 'returnDate') && formData.value?.requireReturn === true) {
        isRequired = true;
      }

      // Dynamic read-only linkage:
      if (key === 'mailTrackingNo' && formData.value?.deliveryRoute !== '邮寄入库') {
        isReadonly = true;
      }
      return {
        key,
        label: String(field.label || field.name || field.field || `字段 ${index + 1}`),
        type: String(field.type || 'text'),
        group: String(field.group || '基础信息'),
        required: isRequired,
        readonly: isReadonly,
        options: Array.isArray(field.options) ? field.options.map((item) => String(item)) : []
      };
    });
});
const fieldGroups = computed(() => {
  const groupMap = new Map<string, DynamicFormField[]>();
  dynamicFields.value.forEach((field) => {
    const fields = groupMap.get(field.group) ?? [];
    fields.push(field);
    groupMap.set(field.group, fields);
  });
  return Array.from(groupMap.entries()).map(([title, fields]) => ({ title, fields }));
});


function formatTransitionCondition(expression: string | null): string | null {
  if (!expression) {
    return null;
  }
  const match = expression.trim().match(/^form\.([A-Za-z0-9_]+)\s*==\s*(.+)$/);
  if (!match) {
    return '满足当前表单的流转条件';
  }

  const [, field, rawValue] = match;
  const fieldLabels: Record<string, string> = {
    entrustAccepted: '受理决定',
    sealRequired: '是否需要用章',
    nextRecommendation: '下一步建议',
    objectionAccepted: '是否属于异议函',
    replyRequired: '是否需要回复',
    archivistReviewed: '档案审核结果',
    sealCompleted: '盖章是否完成'
  };
  const normalizedValue = rawValue.trim().replace(/^['"]|['"]$/g, '');
  let valueLabel = normalizedValue;
  if (field === 'entrustAccepted') {
    valueLabel = normalizedValue === 'true' ? '受理' : '不予受理';
  } else if (normalizedValue === 'true') {
    valueLabel = '是';
  } else if (normalizedValue === 'false') {
    valueLabel = '否';
  }
  return `${fieldLabels[field] || '表单选项'}：${valueLabel}`;
}

const transitionOptions = computed<RuntimeTransitionOption[]>(() => {
  if (isDraftCase.value && !currentTask.value) {
    return [
      { key: 'SUBMIT', value: 'SUBMIT', label: '提交并启动流程' }
    ];
  }
  if (workflowView.value?.nextTransitions.length) {
    const nodeNames = new Map(workflowView.value.nodes.map((node) => [node.nodeCode, node.nodeName]));
    return workflowView.value.nextTransitions.map((transition, idx) => ({
      key: `${transition.actionCode}-${transition.toNodeCode}-${idx}`,
      value: transition.actionCode,
      label: transition.actionName || '转交下一步',
      targetNode: nodeNames.get(transition.toNodeCode) || transition.toNodeCode,
      condition: formatTransitionCondition(transition.conditionExpression)
    }));
  }
  return [
    { key: 'APPROVE', value: 'APPROVE', label: '完成当前节点' }
  ];
});
const selectedNextStepText = computed(() => transitionOptions.value
  .filter((option) => option.key === selectedTransition.value)
  .map((option) => option.targetNode || option.label)
  .join('、'));
const fallbackFlowchartUrl = computed(() => {
  const caseType = detail.value?.caseType ?? '';
  const assets: Array<[string, string]> = [
    ['收到委托书', '收到委托书流程.png'],
    ['不予受理', '不予受理.jpg'],
    ['内部质量控制', '编制内部质量控制文件.jpg'],
    ['现场勘验', '现场勘验.jpg'],
    ['初步勘验', '初步勘验.jpg'],
    ['材料接收', '材料接收与返还.jpg'],
    ['材料返还', '材料接收与返还.jpg'],
    ['发交费通知', '发交费通知书及相关函件.jpg'],
    ['终止鉴定', '终止鉴定.jpg'],
    ['退费', '退费.jpg'],
    ['归档', '归档.jpg'],
    ['征求意见稿', '鉴定意见书征求意见稿送审稿编.jpg']
  ];
  const matched = assets.find(([keyword]) => caseType.includes(keyword));
  return matched ? `/flowcharts/${matched[1]}` : null;
});
const displayedFlowchartUrl = computed(() => {
  if (activeFlowchartTab.value === 'panorama') {
    return '/flowcharts/b6130e9b27eacb5fd5b365f3272d4eba.png';
  }
  if (activeFlowchartTab.value === 'detailed') {
    return '/flowcharts/ff4fe8d56d1e2775159a261ad55e2f74.png';
  }
  return fallbackFlowchartUrl.value || '/flowcharts/b6130e9b27eacb5fd5b365f3272d4eba.png';
});
const nodeCards = computed(() => [
  { title: '当前环节', value: detail.value?.currentNodeName || formatParentNode(detail.value?.currentNodeCode ?? null) || '撰写草稿' },
  { title: '主办人', value: detail.value?.currentHandlerName || '待分派' },
  { title: '当前任务', value: currentTask.value?.taskTitle || '待启动/待领取' },
  { title: '截止时间', value: formatDateTime(detail.value?.deadlineTime ?? null) },
  { title: '权限状态', value: canHandle.value ? '可办理' : '经办/查看' }
]);

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}

async function loadDetail(): Promise<void> {
  if (!caseId.value) {
    return;
  }
  loading.value = true;
  try {
    const [caseDetail, caseSubflows, catalog, users] = await Promise.all([
      fetchCaseDetail(caseId.value),
      fetchCaseSubflows(caseId.value).catch(() => []),
      fetchJudicialCatalog(),
      fetchUserOptions().catch(() => [])
    ]);
    detail.value = caseDetail;
    subflows.value = caseSubflows;
    forms.value = catalog.forms;
    userOptions.value = users;
    currentTask.value = await loadCurrentTask(caseDetail);
    workflowView.value = await fetchCaseWorkflowView(caseDetail.id, currentTask.value?.id).catch(() => null);
    selectedTransition.value = currentTask.value ? 'APPROVE' : 'SUBMIT';
    if (currentTask.value && transitionOptions.value.length > 0) {
      selectedTransition.value = transitionOptions.value[0].key;
    }
    uploadedFiles.value = [];
    await loadFormPreview();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载案件详情失败');
  } finally {
    loading.value = false;
  }
}

function parseJson<T>(value: string | null | undefined, fallback: T): T {
  if (!value) {
    return fallback;
  }
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

async function loadFormPreview(): Promise<void> {
  const formCode = currentTask.value?.formCode || currentForm.value?.code;
  if (!formCode) {
    formPreview.value = null;
    formData.value = buildDefaultFormData([]);
    return;
  }
  try {
    formPreview.value = await fetchRuntimeFormPreview(formCode);
    formData.value = buildDefaultFormData(dynamicFields.value);
  } catch {
    formPreview.value = null;
    formData.value = buildDefaultFormData([]);
  }
}

function buildDefaultFormData(fields: DynamicFormField[]): Record<string, unknown> {
  const defaults: Record<string, unknown> = {
    ...(detail.value?.formData ?? {}),
    caseNo: detail.value?.caseNo ?? '',
    flowName: detail.value?.caseTitle ?? '',
    caseTitle: detail.value?.caseTitle ?? '',
    entrustOrgName: detail.value?.entrustOrgName ?? '',
    handlerOpinion: opinion.value
  };
  fields.forEach((field) => {
    if (defaults[field.key] !== undefined) {
      return;
    }
    defaults[field.key] = field.type === 'boolean' ? null : '';
  });
  return defaults;
}

async function loadCurrentTask(caseDetail: CaseDetail): Promise<TaskDetail | null> {
  if (Number.isFinite(routeTaskId.value) && routeTaskId.value > 0) {
    try {
      return await fetchTaskDetail(routeTaskId.value);
    } catch {
      return null;
    }
  }
  if (!caseDetail.currentNodeCode) {
    return null;
  }
  try {
    return await fetchTaskDetailByCaseNode(caseDetail.id, caseDetail.currentNodeCode);
  } catch {
    return null;
  }
}

async function submitAction(actionCodeOrKey: string): Promise<void> {
  if (!detail.value) {
    return;
  }
  if (!canHandle.value) {
    ElMessage.warning('当前用户不是本环节主办人，暂不能提交办理动作');
    return;
  }
  const matchedOption = transitionOptions.value.find((opt) => opt.key === actionCodeOrKey);
  const actionCode = matchedOption ? matchedOption.value : (actionCodeOrKey as WorkflowActionCode);

  const missingFields = missingRequiredFields(actionCode);
  if (missingFields.length > 0) {
    ElMessage.warning(`请先补齐必填字段：${missingFields.slice(0, 4).join('、')}`);
    return;
  }
  if ((actionCode === 'RETURN' || actionCode === 'TERMINATE') && !opinion.value.trim()) {
    ElMessage.warning('退回或终止必须填写明确办理意见');
    return;
  }
  if (!['RETURN', 'TERMINATE', 'REOPEN', 'WITHDRAW'].includes(actionCode)) {
    const target = selectedNextStepText.value || '流程配置 of 下一节点';
    try {
      await ElMessageBox.confirm(
        `信息填写完成后，请再次确认下一步骤为“${target}”。确认无误后将转交下一步。`,
        '确认流转节点',
        { confirmButtonText: '确认并转交', cancelButtonText: '返回检查', type: 'warning' }
      );
    } catch {
      return;
    }
  }
  acting.value = true;
  try {
    const isDraft = isDraftCase.value;
    const res = await submitWorkflowAction(detail.value.id, {
      taskId: currentTask.value?.id,
      actionCode,
      opinion: opinion.value || undefined,
      formData: {
        ...formData.value,
        handlerOpinion: opinion.value || formData.value.handlerOpinion
      },
      fileIds: uploadedFiles.value.map((file) => file.fileId)
    });

    if (isDraft && res.taskId) {
      try {
        await submitWorkflowAction(detail.value.id, {
          taskId: res.taskId,
          actionCode: 'APPROVE',
          opinion: opinion.value || '草稿转正自动流转',
          formData: {
            ...formData.value,
            handlerOpinion: opinion.value || '草稿转正自动流转'
          },
          fileIds: uploadedFiles.value.map((file) => file.fileId)
        });
        ElMessage.success('案件草稿转正成功，已自动提交并流转至收案员登记环节');
      } catch (autoErr) {
        console.warn('草稿转正后自动流转失败:', autoErr);
        ElMessage.warning('草稿已创建，但部分必填字段尚未完善，请继续填写后点击提交流转');
      }
    } else {
      ElMessage.success('流程动作已提交');
    }

    await loadDetail();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交流程动作失败');
  } finally {
    acting.value = false;
  }
}

async function saveCurrentForm(): Promise<void> {
  if (!detail.value) {
    return;
  }
  if (!canHandle.value) {
    ElMessage.warning('当前用户没有保存该表单的权限');
    return;
  }
  saving.value = true;
  try {
    detail.value = await saveCaseFormData(detail.value.id, {
      formData: {
        ...formData.value,
        handlerOpinion: opinion.value || formData.value.handlerOpinion
      },
      opinion: opinion.value || undefined
    });
    formData.value = buildDefaultFormData(dynamicFields.value);
    ElMessage.success('表单已保存，流程未流转');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存表单失败');
  } finally {
    saving.value = false;
  }
}

async function uploadNodeFile(options: { file: File; onSuccess?: (response: unknown) => void; onError?: (error: Error) => void }): Promise<void> {
  try {
    const response = await uploadWorkflowFile({
      file: options.file,
      caseId: detail.value?.id,
      nodeCode: detail.value?.currentNodeCode ?? undefined,
      taskId: currentTask.value?.id,
      artifactCode: detail.value?.currentNodeCode ?? currentForm.value?.code,
      artifactName: currentTask.value?.taskTitle ?? currentForm.value?.name ?? detail.value?.caseTitle,
      changeNote: `${detail.value?.currentNodeName || '节点'}附件`
    });
    uploadedFiles.value.push(response);
    ElMessage.success(response.duplicate ? '文件已上传，检测到重复文件记录' : '文件上传成功');
    options.onSuccess?.(response);
  } catch (error) {
    const normalized = error instanceof Error ? error : new Error('文件上传失败');
    ElMessage.error(normalized.message);
    options.onError?.(normalized);
  }
}

function missingRequiredFields(actionCode: WorkflowActionCode): string[] {
  if (actionCode === 'RETURN' || actionCode === 'TERMINATE' || actionCode === 'REOPEN' || actionCode === 'WITHDRAW') {
    return [];
  }
  return dynamicFields.value
    .filter((field) => field.required && !field.readonly)
    .filter((field) => {
      const value = formData.value[field.key];
      if (typeof value === 'boolean') {
        return false;
      }
      return value === undefined || value === null || String(value).trim() === '';
    })
    .map((field) => field.label);
}

const nodeNameMap: Record<string, string> = {
  'QUALITY_CONTROL': '编制内部质量控制文件',
  'PAYMENT_NOTICE': '发交费通知',
  'PRELIMINARY_SURVEY': '初步勘验',
  'FIELD_SURVEY': '现场勘验',
  'MATERIAL_RECEIVE_RETURN': '材料接收与返还',
  'DRAFT_OPINION_REVIEW': '征求意见稿送审稿编制',
  'FINAL_OPINION_REVIEW': '送审稿编制',
  'ISSUE_OPINION': '出具鉴定意见书',
  'ISSUE_DRAFT_OPINION': '出具征求意见稿',
  'REFUND': '退费',
  'TERMINATE_APPRAISAL': '终止鉴定',
  'SEAL_APPLICATION': '用章流程',
  'REJECT_ACCEPTANCE': '不予受理',
  'ARCHIVE': '归档',
  'COURT_LETTER': '收到法院其他函件',
  'COURT_APPEARANCE': '收到出庭通知',
  'WITHDRAW_CASE_LETTER': '收到撤案函',
  'EXPENSE_REIMBURSEMENT': '财务报销',
  'CASE_SUSPENSION': '案件暂停'
};

function formatParentNode(code: string | null): string {
  if (!code) return '';
  const cleanCode = code.toUpperCase();
  return nodeNameMap[cleanCode] || '未命名环节';
}

function formatCaseStatus(status?: string | null): string {
  if (!status) return '-';
  const map: Record<string, string> = {
    'DRAFT': '草稿',
    'PROCESSING': '办理中',
    'COMPLETED': '已完成',
    'ARCHIVED': '已归档',
    'TERMINATED': '已终止'
  };
  return map[status] || status;
}

async function goBack(): Promise<void> {
  if (returnPath.value) {
    await router.push(returnPath.value);
    return;
  }
  router.back();
}

async function openSiblingCasesByOrg(): Promise<void> {
  if (!detail.value?.entrustOrgName) {
    return;
  }
  await router.push({
    path: '/work-query',
    query: {
      keyword: detail.value.entrustOrgName,
      from: route.fullPath,
      fromLabel: '案件详情'
    }
  });
}

async function openSiblingCasesByStatus(): Promise<void> {
  if (!detail.value?.caseStatus) {
    return;
  }
  await router.push({
    path: '/work-query',
    query: {
      caseStatus: detail.value.caseStatus,
      from: route.fullPath,
      fromLabel: '案件详情'
    }
  });
}

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <section class="process-page">
    <div class="process-toolbar">
      <div>
        <div class="process-path">全部 &gt;&gt; 我的工作 &gt;&gt; 办理</div>
        <h1>{{ detail?.caseTitle || '案件办理' }}</h1>
      </div>
      <div class="query-actions">
        <el-button type="primary" plain @click="flowchartVisible = true">流程图</el-button>
        <el-button v-if="returnPath" @click="goBack">返回{{ returnLabel }}</el-button>
        <el-button :loading="loading" @click="loadDetail">刷新</el-button>
      </div>
    </div>

    <div v-if="detail" class="process-body" v-loading="loading">
      <div v-if="returnPath" class="state-banner">
        当前从 <strong>{{ returnLabel }}</strong> 进入案件办理
        <span v-if="sourceBoard">，处理后可返回原模块。</span>
      </div>

      <section class="process-strip">
        <article v-for="item in nodeCards" :key="item.title">
          <span>{{ item.title }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>

      <section class="process-section">
        <div class="section-title">
          <h2>案件信息</h2>
        <el-tag :type="canHandle ? 'success' : 'info'" effect="plain">{{ canHandle ? '主办可办理' : '只读查看' }}</el-tag>
        </div>
        <el-descriptions border :column="2">
          <el-descriptions-item label="流水号">{{ formData?.serialNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="案件名称">{{ detail.caseTitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="案件编号">{{ detail.caseNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="案件类型">{{ detail.caseType || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ formatCaseStatus(detail.caseStatus) }}</el-descriptions-item>
          <el-descriptions-item label="委托单位">{{ detail.entrustOrgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="受理部门">{{ detail.acceptDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(detail.submittedTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedTime) }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <el-dialog
        v-model="flowchartVisible"
        title="流程设计图"
        width="96vw"
        top="2vh"
        class="flowchart-dialog"
        destroy-on-close
      >
        <div class="flowchart-tabs">
          <el-radio-group v-model="activeFlowchartTab" size="large">
            <el-radio-button value="current">当前步骤流程图</el-radio-button>
            <el-radio-button value="panorama">全景工作流程图</el-radio-button>
            <el-radio-button value="detailed">细化工作流程图</el-radio-button>
          </el-radio-group>
        </div>

        <div class="workflow-image-wrap">
          <el-image
            :key="displayedFlowchartUrl"
            :src="displayedFlowchartUrl"
            :preview-src-list="[displayedFlowchartUrl]"
            fit="contain"
            class="workflow-image"
            preview-teleported
          />
        </div>

        <template #footer>
          <el-button type="primary" @click="flowchartVisible = false">关闭</el-button>
        </template>
      </el-dialog>

      <CaseDynamicForm
        :form-preview="formPreview"
        :current-form="currentForm"
        :case-type="detail.caseType"
        :field-groups="fieldGroups"
        :form-data="formData"
        :can-handle="canHandle"
        :uploaded-files="uploadedFiles"
        :user-options="userOptions"
        :upload-node-file="uploadNodeFile"
      />

      <section class="process-section">
        <div class="section-title">
          <h2>关联流程</h2>
          <span>{{ subflows.length }} 条</span>
        </div>
        <el-table :data="subflows" border empty-text="暂无已触发子流程">
          <el-table-column prop="wfName" label="流程名称" min-width="180" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              {{ formatCaseStatus(row.caseStatus) }}
            </template>
          </el-table-column>
          <el-table-column label="父节点" width="180">
            <template #default="{ row }">
              {{ formatParentNode(row.parentNodeCode) }}
            </template>
          </el-table-column>
          <el-table-column label="发起时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.startedTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="query-actions relation-actions">
          <el-button v-if="hasEntrustOrg" @click="openSiblingCasesByOrg">查看同单位案件</el-button>
          <el-button v-if="hasStatus" @click="openSiblingCasesByStatus">查看同状态案件</el-button>
        </div>
      </section>

      <CaseActionBar
        v-model:opinion="opinion"
        v-model:selected-transition="selectedTransition"
        :can-handle="canHandle"
        :is-draft-case="isDraftCase"
        :transition-options="transitionOptions"
        :acting="acting"
        :saving="saving"
        @save-form="saveCurrentForm"
        @submit-action="submitAction"
      />

      <section class="process-section">
        <div class="section-title">
          <h2>办理轨迹</h2>
          <span>展示当前案件的关键流程状态</span>
        </div>
        <el-timeline class="process-timeline">
          <el-timeline-item
            :timestamp="formatDateTime(detail.createdTime)"
            placement="top"
            type="primary"
          >
            <div class="timeline-card">
              <strong>案件发起</strong>
              <span>{{ detail.caseStatus === 'DRAFT' ? '已创建工作草稿，等待提交启动流程' : '案件已创建并进入办理流程' }}</span>
            </div>
          </el-timeline-item>
          <el-timeline-item
            :timestamp="formatDateTime(detail.updatedTime)"
            placement="top"
            type="success"
            hollow
          >
            <div class="timeline-card timeline-card--current">
              <div class="timeline-card-title">
                <strong>当前节点</strong>
                <el-tag size="small" type="success" effect="plain">进行中</el-tag>
              </div>
              <span>{{ detail.currentNodeName || formatParentNode(detail.currentNodeCode) || '撰写草稿' }}</span>
              <small v-if="detail.currentHandlerName">当前主办人：{{ detail.currentHandlerName }}</small>
            </div>
          </el-timeline-item>
          <el-timeline-item
            :timestamp="formatDateTime(detail.updatedTime)"
            placement="top"
            color="#94a3b8"
          >
            <div class="timeline-card">
              <strong>最近更新</strong>
              <span>案件信息或流程状态已更新</span>
            </div>
          </el-timeline-item>
        </el-timeline>
      </section>
    </div>

    <div v-else class="empty-state">正在加载案件办理...</div>
  </section>
</template>

<style scoped>
.process-page {
  min-height: calc(100vh - 92px);
  background: #f5f7fa;
  color: #334155;
}

.process-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 28px;
  border-bottom: 1px solid #d8dee8;
  background: #ffffff;
}

.process-path {
  margin-bottom: 4px;
  color: #64748b;
  font-size: 13px;
}

.process-toolbar h1 {
  margin: 0;
  color: #c43c20;
  font-size: 22px;
}

.process-body {
  display: grid;
  gap: 14px;
  padding: 16px 28px 32px;
}

.process-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.process-strip article,
.process-section {
  border: 1px solid #dce3ee;
  background: #ffffff;
}

.process-strip article {
  padding: 12px 14px;
}

.process-strip span,
.section-title span {
  color: #728096;
  font-size: 13px;
}

.process-strip strong {
  display: block;
  margin-top: 6px;
  color: #1f2937;
  font-size: 15px;
}

.process-section {
  padding: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title h2 {
  margin: 0;
  color: #243244;
  font-size: 16px;
}

.process-timeline {
  max-width: 900px;
  padding: 8px 8px 0 4px;
}

.timeline-card {
  display: grid;
  gap: 6px;
  padding: 12px 16px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
}

.timeline-card--current {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.timeline-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.timeline-card strong {
  color: #1e293b;
  font-size: 15px;
}

.timeline-card span {
  color: #475569;
}

.timeline-card small {
  color: #64748b;
}

.flowchart-tip {
  margin-bottom: 16px;
}

.flowchart-tabs {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.workflow-image-wrap {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  height: 67vh;
  padding: 14px 24px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  overflow: auto;
  background: #f8fafc;
}

.workflow-image {
  display: block;
  width: auto;
  max-width: 100%;
  min-width: min(100%, 900px);
  cursor: zoom-in;
}

:deep(.flowchart-dialog) {
  max-width: none;
  margin-bottom: 2vh;
}

:deep(.flowchart-dialog .el-dialog__body) {
  padding: 14px 22px 0;
}

:deep(.flowchart-dialog .el-dialog__footer) {
  padding: 14px 22px 16px;
}

.relation-actions {
  margin-top: 12px;
}

.empty-state {
  padding: 60px 28px;
  color: #94a3b8;
  font-size: 15px;
}

@media (max-width: 900px) {
  .process-toolbar {
    align-items: flex-start;
    flex-direction: column;
    padding: 14px 18px;
  }

  .process-body {
    padding: 14px 16px 28px;
  }

  .process-strip {
    grid-template-columns: 1fr;
  }
}
</style>
