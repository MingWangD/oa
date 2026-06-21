<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import {
  fetchCaseDetail,
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

interface DynamicFormField {
  key: string;
  label: string;
  type: string;
  group: string;
  required: boolean;
  readonly: boolean;
  options: string[];
}

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const acting = ref(false);
const saving = ref(false);
const detail = ref<CaseDetail | null>(null);
const subflows = ref<CaseSubflowSummary[]>([]);
const forms = ref<JudicialFormDefinition[]>([]);
const currentTask = ref<TaskDetail | null>(null);
const formPreview = ref<FormVersionDesign | null>(null);
const userOptions = ref<AdminUser[]>([]);
const formData = ref<Record<string, unknown>>({});
const uploadedFiles = ref<FileUploadResponse[]>([]);
const opinion = ref('');
const selectedTransition = ref<WorkflowActionCode>('SUBMIT');

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
  return authStore.isAdmin || !detail.value.currentHandlerId || detail.value.currentHandlerId === authStore.user?.id;
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

const formRule = computed(() => {
  if (!currentTask.value?.formRuleJson) {
    return {};
  }
  return parseJson<Record<string, any>>(currentTask.value.formRuleJson, {});
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
      const key = String(field.field || field.code || '');
      const auth = fieldAuth[key] || {};
      if (auth.hidden) {
        return false;
      }
      if (isDraftCase.value && (field.group === '流程基础' || field.group === '受理决策')) {
        return false;
      }
      return key !== 'handlerOpinion';
    })
    .map((field, index) => {
      const key = String(field.field || field.code || `field_${index + 1}`);
      const auth = fieldAuth[key] || {};
      let isReadonly = Boolean(field.readOnly ?? field.readonly);
      if (auth.readonly !== undefined) {
        isReadonly = Boolean(auth.readonly);
      }

      // Check group-level permissions (e.g., role restrictions)
      const groupName = String(field.group || '');
      const groupConfig = permissionSchema?.groups?.[groupName];
      if (groupConfig) {
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

      let isRequired = Boolean(field.required);
      if (auth.required !== undefined) {
        isRequired = Boolean(auth.required);
      }

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
const formRequirementRows = computed(() => {
  const form = currentForm.value;
  return [
    { label: '输入文件', value: form?.inputFiles.join('、') || '按当前节点表单配置校验' },
    { label: '输出文件', value: form?.outputFiles.join('、') || '节点完成后固化归档' },
    { label: '版本产物', value: form?.versionedArtifacts.join('、') || '无独立版本产物' }
  ];
});
const transitionOptions = computed(() => {
  const name = detail.value?.caseType ?? '';
  if (isDraftCase.value && !currentTask.value) {
    return [
      { value: 'SUBMIT', label: '提交并启动流程' }
    ];
  }
  if (name.includes('收到委托书')) {
    return [
      { value: 'APPROVE', label: '转交/继续流转到下一节点' },
      { value: 'RETURN', label: '退回到允许节点' },
      { value: 'TERMINATE', label: '终止办理' }
    ];
  }
  return [
    { value: 'SUBMIT', label: '提交当前节点' },
    { value: 'APPROVE', label: '同意并流转' },
    { value: 'RETURN', label: '退回' },
    { value: 'TERMINATE', label: '终止' }
  ];
});
const nodeCards = computed(() => [
  { title: '当前环节', value: detail.value?.currentNodeName || detail.value?.currentNodeCode || '草稿' },
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
    selectedTransition.value = currentTask.value ? 'APPROVE' : 'SUBMIT';
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
    defaults[field.key] = field.type === 'boolean' ? false : '';
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

async function submitAction(actionCode: WorkflowActionCode): Promise<void> {
  if (!detail.value) {
    return;
  }
  if (!canHandle.value) {
    ElMessage.warning('当前用户不是本环节主办人，暂不能提交办理动作');
    return;
  }
  const missingFields = missingRequiredFields(actionCode);
  if (missingFields.length > 0) {
    ElMessage.warning(`请先补齐必填字段：${missingFields.slice(0, 4).join('、')}`);
    return;
  }
  if ((actionCode === 'RETURN' || actionCode === 'TERMINATE') && !opinion.value.trim()) {
    ElMessage.warning('退回或终止必须填写明确办理意见');
    return;
  }
  acting.value = true;
  try {
    await submitWorkflowAction(detail.value.id, {
      taskId: currentTask.value?.id,
      actionCode,
      opinion: opinion.value || undefined,
      formData: {
        ...formData.value,
        handlerOpinion: opinion.value || formData.value.handlerOpinion
      },
      fileIds: uploadedFiles.value.map((file) => file.fileId)
    });
    ElMessage.success('流程动作已提交');
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
  if (!code) return '-';
  const cleanCode = code.toUpperCase();
  return nodeNameMap[cleanCode] || code;
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

      <CaseDynamicForm
        :form-preview="formPreview"
        :current-form="currentForm"
        :case-type="detail.caseType"
        :field-groups="fieldGroups"
        :form-data="formData"
        :form-requirement-rows="formRequirementRows"
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
          <h2>流程图与日志</h2>
          <span>节点、意见、附件、归档动作需全量留痕</span>
        </div>
        <ol class="flow-log">
          <li>发起/草稿：{{ formatDateTime(detail.createdTime) }}</li>
          <li>当前节点：{{ detail.currentNodeName || detail.currentNodeCode || '未启动' }}</li>
          <li>最近更新：{{ formatDateTime(detail.updatedTime) }}</li>
        </ol>
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

.flow-log {
  margin: 0;
  padding-left: 20px;
  color: #475569;
  line-height: 1.9;
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

.flow-log {
  margin: 0;
  padding-left: 20px;
  color: #475569;
  line-height: 1.9;
}

.relation-actions {
  margin-top: 12px;
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
