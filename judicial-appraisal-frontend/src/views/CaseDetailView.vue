<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import {
  fetchCaseDetail,
  fetchCaseSubflows,
  fetchFormPreview,
  fetchJudicialCatalog,
  fetchTaskDetailByCaseNode,
  submitWorkflowAction,
  uploadWorkflowFile,
  type CaseDetail,
  type CaseSubflowSummary,
  type FileUploadResponse,
  type FormVersionDesign,
  type JudicialFormDefinition,
  type TaskDetail,
  type WorkflowActionCode
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';

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
const detail = ref<CaseDetail | null>(null);
const subflows = ref<CaseSubflowSummary[]>([]);
const forms = ref<JudicialFormDefinition[]>([]);
const currentTask = ref<TaskDetail | null>(null);
const formPreview = ref<FormVersionDesign | null>(null);
const formData = ref<Record<string, unknown>>({});
const uploadedFiles = ref<FileUploadResponse[]>([]);
const opinion = ref('');
const selectedTransition = ref<WorkflowActionCode>('SUBMIT');

const caseId = computed(() => Number(route.params.id));
const returnPath = computed(() => (typeof route.query.from === 'string' ? route.query.from : ''));
const returnLabel = computed(() => (typeof route.query.fromLabel === 'string' ? route.query.fromLabel : '上一页'));
const sourceBoard = computed(() => (typeof route.query.fromBoard === 'string' ? route.query.fromBoard : ''));
const hasEntrustOrg = computed(() => Boolean(detail.value?.entrustOrgName));
const hasStatus = computed(() => Boolean(detail.value?.caseStatus));
const isDraftCase = computed(() => detail.value?.caseStatus === 'DRAFT');
const canHandle = computed(() => {
  if (!detail.value) {
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
const dynamicFields = computed<DynamicFormField[]>(() => {
  const fields = parseJson<Array<Record<string, unknown>>>(formPreview.value?.fieldSchemaJson, []);
  return fields.map((field, index) => ({
    key: String(field.field || field.code || `field_${index + 1}`),
    label: String(field.label || field.name || field.field || `字段 ${index + 1}`),
    type: String(field.type || 'text'),
    group: String(field.group || '基础信息'),
    required: Boolean(field.required),
    readonly: Boolean(field.readonly),
    options: Array.isArray(field.options) ? field.options.map((item) => String(item)) : []
  }));
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
    const [caseDetail, caseSubflows, catalog] = await Promise.all([
      fetchCaseDetail(caseId.value),
      fetchCaseSubflows(caseId.value).catch(() => []),
      fetchJudicialCatalog()
    ]);
    detail.value = caseDetail;
    subflows.value = caseSubflows;
    forms.value = catalog.forms;
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
  const formCode = currentForm.value?.code;
  if (!formCode) {
    formPreview.value = null;
    formData.value = buildDefaultFormData([]);
    return;
  }
  try {
    formPreview.value = await fetchFormPreview(formCode);
    formData.value = buildDefaultFormData(dynamicFields.value);
  } catch {
    formPreview.value = null;
    formData.value = buildDefaultFormData([]);
  }
}

function buildDefaultFormData(fields: DynamicFormField[]): Record<string, unknown> {
  const defaults: Record<string, unknown> = {
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
      assigneeId: authStore.user?.id,
      assigneeName: authStore.user?.realName || authStore.user?.username,
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
          <el-descriptions-item label="状态">{{ detail.caseStatus || '-' }}</el-descriptions-item>
          <el-descriptions-item label="委托单位">{{ detail.entrustOrgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="受理部门">{{ detail.acceptDeptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatDateTime(detail.submittedTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDateTime(detail.updatedTime) }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="process-section">
        <div class="section-title">
          <h2>动态表单</h2>
          <span>{{ formPreview?.formName || currentForm?.name || detail.caseType || '待匹配表单' }}</span>
        </div>
        <div class="form-board">
          <article v-for="row in formRequirementRows" :key="row.label">
            <span>{{ row.label }}</span>
            <strong>{{ row.value }}</strong>
          </article>
        </div>
        <el-empty v-if="fieldGroups.length === 0" description="暂未匹配到已发布表单字段" />
        <el-form v-else class="dynamic-form" label-position="top" :model="formData" :disabled="!canHandle">
          <section v-for="group in fieldGroups" :key="group.title" class="dynamic-form-group">
            <h3>{{ group.title }}</h3>
            <div class="dynamic-field-grid">
              <el-form-item
                v-for="field in group.fields"
                :key="field.key"
                :label="field.required ? `${field.label} *` : field.label"
              >
                <el-input
                  v-if="field.type === 'textarea'"
                  v-model="formData[field.key]"
                  :readonly="field.readonly"
                  type="textarea"
                  :rows="3"
                />
                <el-select
                  v-else-if="field.type === 'select'"
                  v-model="formData[field.key]"
                  :disabled="field.readonly || !canHandle"
                  clearable
                >
                  <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
                </el-select>
                <el-switch
                  v-else-if="field.type === 'boolean'"
                  v-model="formData[field.key]"
                  :disabled="field.readonly || !canHandle"
                  active-text="是"
                  inactive-text="否"
                />
                <el-date-picker
                  v-else-if="field.type === 'date'"
                  v-model="formData[field.key]"
                  :readonly="field.readonly"
                  type="date"
                  value-format="YYYY-MM-DD"
                />
                <el-date-picker
                  v-else-if="field.type === 'datetime'"
                  v-model="formData[field.key]"
                  :readonly="field.readonly"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                />
                <el-input-number
                  v-else-if="field.type === 'number'"
                  v-model="formData[field.key]"
                  :disabled="field.readonly || !canHandle"
                  :controls="false"
                  class="number-field"
                />
                <el-input
                  v-else
                  v-model="formData[field.key]"
                  :readonly="field.readonly"
                  :placeholder="field.type === 'user' ? '请选择或填写办理人' : undefined"
                />
              </el-form-item>
            </div>
          </section>
        </el-form>
        <div class="attachment-panel">
          <div class="section-title attachment-title">
            <h2>节点附件</h2>
            <span>上传后随本次办理动作归档</span>
          </div>
          <el-upload
            drag
            multiple
            :disabled="!canHandle"
            :http-request="uploadNodeFile"
            :show-file-list="true"
          >
            <div class="upload-text">拖拽文件到此处，或点击上传输入/输出文件</div>
          </el-upload>
          <div v-if="uploadedFiles.length" class="uploaded-file-list">
            <span v-for="file in uploadedFiles" :key="file.fileId">
              {{ file.originalName }} · v{{ file.versionNo }}{{ file.duplicate ? ' · 重复文件' : '' }}
            </span>
          </div>
        </div>
      </section>

      <section class="process-section">
        <div class="section-title">
          <h2>关联流程</h2>
          <span>{{ subflows.length }} 条</span>
        </div>
        <el-table :data="subflows" border empty-text="暂无已触发子流程">
          <el-table-column prop="wfName" label="流程名称" min-width="180" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="parentNodeCode" label="父节点" width="140" />
          <el-table-column label="发起时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.startedTime) }}</template>
          </el-table-column>
        </el-table>
        <div class="query-actions relation-actions">
          <el-button v-if="hasEntrustOrg" @click="openSiblingCasesByOrg">查看同单位案件</el-button>
          <el-button v-if="hasStatus" @click="openSiblingCasesByStatus">查看同状态案件</el-button>
        </div>
      </section>

      <section class="process-section">
        <div class="section-title">
          <h2>办理意见</h2>
          <span>退回或终止时必须填写明确原因</span>
        </div>
        <el-input
          v-model="opinion"
          :disabled="!canHandle"
          type="textarea"
          :rows="4"
          placeholder="请输入办理意见"
        />
      </section>

      <section class="process-section transition-section">
        <div class="section-title">
          <h2>流转处理</h2>
          <span>按流程图选择下一步</span>
        </div>
        <div class="transition-row">
          <el-select v-model="selectedTransition" :disabled="!canHandle" class="transition-select">
            <el-option
              v-for="option in transitionOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-button
            type="primary"
            :disabled="!canHandle"
            :loading="acting"
            @click="submitAction(selectedTransition)"
          >
            提交流转
          </el-button>
          <el-button :disabled="!canHandle" :loading="acting" @click="submitAction('RETURN')">退回</el-button>
          <el-button type="danger" plain :disabled="!canHandle" :loading="acting" @click="submitAction('TERMINATE')">
            终止
          </el-button>
        </div>
      </section>

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
.section-title span,
.form-board span {
  color: #728096;
  font-size: 13px;
}

.process-strip strong,
.form-board strong {
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

.form-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.form-board article {
  min-height: 74px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  background: #fafafa;
}

.dynamic-form {
  margin-top: 14px;
}

.dynamic-form-group + .dynamic-form-group {
  margin-top: 16px;
}

.dynamic-form-group h3 {
  margin: 0 0 10px;
  color: #334155;
  font-size: 15px;
}

.dynamic-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.dynamic-field-grid :deep(.el-select),
.dynamic-field-grid :deep(.el-date-editor),
.number-field {
  width: 100%;
}

.attachment-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e2e8f0;
}

.attachment-title {
  margin-bottom: 10px;
}

.upload-text {
  color: #526174;
  font-size: 14px;
}

.uploaded-file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.uploaded-file-list span {
  padding: 5px 8px;
  border: 1px solid #d8e0ec;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
}

.relation-actions,
.transition-row {
  margin-top: 12px;
}

.transition-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.transition-select {
  width: min(100%, 320px);
}

.flow-log {
  margin: 0;
  padding-left: 20px;
  color: #475569;
  line-height: 1.9;
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

  .process-strip,
  .form-board,
  .dynamic-field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
