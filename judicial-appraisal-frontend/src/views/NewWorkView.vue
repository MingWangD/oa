<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Document, InfoFilled, Search, Share } from '@element-plus/icons-vue';

import {
  createCaseDraft,
  fetchJudicialCatalog,
  type JudicialWorkflowDefinition
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';

interface WorkCategory {
  key: string;
  label: string;
  workflowCodes?: string[];
}

const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const creatingCode = ref('');
const keyword = ref('');
const activeCategory = ref('common');
const activeRole = ref('all');
const viewMode = ref<'task' | 'list' | 'overview'>('task');
const workflows = ref<JudicialWorkflowDefinition[]>([]);
const judicialRoles = ref<string[]>([]);

const fallbackJudicialRoles = [
  '部门负责人',
  '项目负责人',
  '项目辅助人',
  '档案管理员',
  '中心档案管理员',
  '技术负责人',
  '审阅所长',
  '综合业务部',
  '财务',
  '收案员'
];

const manualCreateCodes = new Set([
  'received-entrust',
  'court-letter',
  'court-appearance',
  'withdraw-case-letter',
  'seal-application',
  'expense-reimbursement'
]);

const linkedCreateCodes = new Set(['court-letter', 'court-appearance', 'withdraw-case-letter']);

const subflowOnlyCodes = new Set([
  'preliminary-survey',
  'payment-notice',
  'quality-control',
  'field-survey',
  'material-receive-return',
  'draft-opinion-review',
  'final-opinion-review',
  'issue-opinion',
  'issue-draft-opinion',
  'reject-acceptance',
  'refund',
  'terminate-appraisal',
  'archive'
]);

const categories: WorkCategory[] = [
  {
    key: 'common',
    label: '常用工作',
    workflowCodes: [
      'received-entrust',
      'court-letter',
      'court-appearance',
      'withdraw-case-letter',
      'seal-application',
      'expense-reimbursement'
    ]
  },
  { key: 'all', label: '全部工作' },
  {
    key: 'judicial',
    label: '司法鉴定',
    workflowCodes: [
      'received-entrust',
      'preliminary-survey',
      'payment-notice',
      'quality-control',
      'field-survey',
      'material-receive-return',
      'draft-opinion-review',
      'final-opinion-review',
      'issue-opinion',
      'issue-draft-opinion',
      'court-letter',
      'court-appearance',
      'reject-acceptance',
      'withdraw-case-letter',
      'refund',
      'terminate-appraisal',
      'archive',
      'seal-application',
      'expense-reimbursement'
    ]
  },
  {
    key: 'subflow',
    label: '系统触发',
    workflowCodes: [
      'preliminary-survey',
      'payment-notice',
      'quality-control',
      'field-survey',
      'material-receive-return',
      'draft-opinion-review',
      'final-opinion-review',
      'issue-opinion',
      'issue-draft-opinion',
      'reject-acceptance',
      'refund',
      'terminate-appraisal',
      'archive'
    ]
  }
];

const userRoleNames = computed(() => authStore.roleNames);
const isAdmin = computed(() => authStore.isAdmin);
const roleFilterOptions = computed(() => ['all', ...(judicialRoles.value.length ? judicialRoles.value : fallbackJudicialRoles)]);

const visibleWorkflows = computed(() => {
  const selectedCategory = categories.find((item) => item.key === activeCategory.value);
  const categoryCodes = selectedCategory?.workflowCodes;
  const query = keyword.value.trim().toLowerCase();

  return workflows.value
    .filter((workflow) => canCreateWorkflow(workflow))
    .filter((workflow) => !categoryCodes || categoryCodes.includes(workflow.code))
    .filter((workflow) => activeRole.value === 'all' || workflow.roles.includes(activeRole.value))
    .filter((workflow) => {
      if (!query) {
        return true;
      }
      return [
        workflow.name,
        workflow.code,
        workflow.formCode,
        workflow.roles.join(' ')
      ].some((value) => value.toLowerCase().includes(query));
    })
    .sort((left, right) => {
      if (!categoryCodes) {
        return left.name.localeCompare(right.name, 'zh-Hans-CN');
      }
      const leftIndex = categoryCodes.indexOf(left.code);
      const rightIndex = categoryCodes.indexOf(right.code);
      return (leftIndex === -1 ? 999 : leftIndex) - (rightIndex === -1 ? 999 : rightIndex);
    });
});

const activeCategoryLabel = computed(
  () => categories.find((item) => item.key === activeCategory.value)?.label ?? '全部工作'
);

const activeRoleLabel = computed(() => activeRole.value === 'all' ? '全部角色' : activeRole.value);

onMounted(async () => {
  loading.value = true;
  try {
    const catalog = await fetchJudicialCatalog();
    workflows.value = catalog.workflows;
    judicialRoles.value = catalog.dedicatedRoles;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载可新建工作失败');
  } finally {
    loading.value = false;
  }
});

function canCreateWorkflow(workflow: JudicialWorkflowDefinition): boolean {
  if (isAdmin.value) {
    return true;
  }
  if (workflow.entryMode === 'direct' || workflow.entryMode.includes('direct')) {
    return true;
  }
  const roleNames = userRoleNames.value;
  return workflow.roles.some((role) => roleNames.some((name) => roleMatches(name, role)));
}

function roleMatches(userRole: string, workflowRole: string): boolean {
  return userRole === workflowRole || userRole.includes(workflowRole) || workflowRole.includes(userRole);
}

function roleCount(role: string): number {
  if (role === 'all') {
    return workflows.value.filter((workflow) => canCreateWorkflow(workflow)).length;
  }
  return workflows.value.filter((workflow) => canCreateWorkflow(workflow) && workflow.roles.includes(role)).length;
}

function canUseRoleFilter(role: string): boolean {
  if (role === 'all' || isAdmin.value) {
    return true;
  }
  return userRoleNames.value.some((userRole) => roleMatches(userRole, role));
}

function canManualCreate(workflow: JudicialWorkflowDefinition): boolean {
  return manualCreateCodes.has(workflow.code) || workflow.entryMode === 'direct' || workflow.entryMode.includes('direct');
}

function isLinkedCreate(workflow: JudicialWorkflowDefinition): boolean {
  return linkedCreateCodes.has(workflow.code) || workflow.entryMode.includes('linked');
}

function entryModeLabel(workflow: JudicialWorkflowDefinition): string {
  if (workflow.code === 'received-entrust') {
    return '主流程直接发起';
  }
  if (isLinkedCreate(workflow)) {
    return '直接或关联原流程';
  }
  if (workflow.code === 'seal-application') {
    return '用章可独立发起，也可由父流程触发';
  }
  if (workflow.code === 'expense-reimbursement') {
    return '独立财务流程';
  }
  if (subflowOnlyCodes.has(workflow.code) || workflow.entryMode === 'subflow') {
    return '由办理页流转触发';
  }
  return workflow.entryMode;
}

function workflowSummary(workflow: JudicialWorkflowDefinition): string {
  const prefix = entryModeLabel(workflow);
  const next = workflow.nextFlows.length ? `，后续：${workflow.nextFlows.slice(0, 2).join('、')}` : '';
  return `${prefix}${next}`;
}

async function quickCreate(workflow: JudicialWorkflowDefinition): Promise<void> {
  if (!canManualCreate(workflow)) {
    ElMessage.warning('该流程按使用手册应从父流程办理页流转触发，请先进入相关案件办理。');
    return;
  }
  creatingCode.value = workflow.code;
  try {
    const created = await createCaseDraft({
      caseTitle: workflow.name,
      caseType: workflow.name,
      entrustOrgName: isLinkedCreate(workflow) ? '关联流程待补' : undefined,
      acceptDeptId: null
    });
    ElMessage.success('工作草稿已创建');
    await router.push(`/case/${created.id}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建工作草稿失败');
  } finally {
    creatingCode.value = '';
  }
}

async function openGuide(workflow: JudicialWorkflowDefinition): Promise<void> {
  await ElMessageBox.alert(
    [
      `可发起角色：${workflow.roles.join('、') || '不限'}`,
      `入口规则：${entryModeLabel(workflow)}`,
      `关联表单：${workflow.formCode}`,
      `关键规则：${workflow.keyRules.join('；') || '暂无'}`,
      `后续流程：${workflow.nextFlows.join('、') || '流程结束'}`
    ].join('\n\n'),
    workflow.name,
    {
      confirmButtonText: '知道了',
      customClass: 'workflow-guide-dialog'
    }
  );
}
</script>

<template>
  <section class="new-work-page">
    <div class="new-work-header">
      <div>
        <div class="new-work-path">全部 &gt;&gt; {{ activeCategoryLabel }}</div>
        <h1>新建工作</h1>
      </div>
      <div class="new-work-tools">
        <el-input
          v-model="keyword"
          class="workflow-search"
          clearable
          :prefix-icon="Search"
          placeholder="请输入流程名称"
        />
        <el-button type="primary">查询</el-button>
        <el-radio-group v-model="viewMode" class="view-switch">
          <el-radio-button label="task">表单列表</el-radio-button>
          <el-radio-button label="list">列表视图</el-radio-button>
          <el-radio-button label="overview">总览视图</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="new-work-shell">
      <aside class="work-category-panel">
        <button
          v-for="category in categories"
          :key="category.key"
          class="work-category"
          :class="{ 'work-category--active': activeCategory === category.key }"
          type="button"
          @click="activeCategory = category.key"
        >
          <span class="category-mail" />
          {{ category.label }}
        </button>

        <div class="role-filter-title">司法鉴定角色</div>
        <button
          v-for="role in roleFilterOptions"
          :key="role"
          class="role-filter"
          :class="{ 'role-filter--active': activeRole === role }"
          :disabled="!canUseRoleFilter(role)"
          type="button"
          @click="activeRole = role"
        >
          <span class="role-avatar" />
          <span class="role-name">{{ role === 'all' ? '全部角色' : role }}</span>
          <em>{{ roleCount(role) }}</em>
        </button>
      </aside>

      <main class="work-list-panel">
        <div class="work-list-title">
          <strong>{{ activeCategoryLabel }}</strong>
          <span>共 {{ visibleWorkflows.length }} 张表单，当前角色：{{ activeRoleLabel }}</span>
        </div>

        <el-empty v-if="!loading && visibleWorkflows.length === 0" description="当前角色暂无可新建工作" />

        <div v-loading="loading" class="work-list">
          <article
            v-for="workflow in visibleWorkflows"
            :key="workflow.code"
            class="work-row"
            :class="{
              'work-row--creating': creatingCode === workflow.code,
              'work-row--subflow': !canManualCreate(workflow)
            }"
          >
            <div class="work-main">
              <el-icon class="form-icon"><Document /></el-icon>
              <button
                class="work-name"
                type="button"
                :title="canManualCreate(workflow) ? '新建工作草稿' : '该流程需由父流程流转触发'"
                @click="quickCreate(workflow)"
              >
                {{ workflow.name }}
              </button>
              <div class="work-summary">{{ workflowSummary(workflow) }}</div>
            </div>

            <button class="work-meta" type="button" @click="openGuide(workflow)">
              <span class="workflow-icon">
                <el-icon><Share /></el-icon>
              </span>
              <span>流程设计图</span>
            </button>

            <button class="work-info" type="button" @click="openGuide(workflow)">
              <el-icon><InfoFilled /></el-icon>
              <span>流程说明</span>
            </button>
          </article>
        </div>
      </main>
    </div>
  </section>
</template>

<style scoped>
.new-work-page {
  min-height: calc(100vh - 92px);
  background: #f6f6f6;
  color: #4d5968;
}

.new-work-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 56px;
  padding: 0 38px;
  background: linear-gradient(#ffffff, #f4f4f4);
  border-bottom: 1px solid #d7d7d7;
}

.new-work-path {
  margin-bottom: 4px;
  color: #566273;
  font-size: 13px;
}

.new-work-header h1 {
  margin: 0;
  color: #c43c20;
  font-size: 24px;
  font-weight: 700;
}

.new-work-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-search {
  width: 260px;
}

.view-switch :deep(.el-radio-button__inner) {
  border-radius: 0;
  color: #596579;
}

.new-work-shell {
  position: relative;
  display: grid;
  grid-template-columns: 178px minmax(0, 1fr);
  min-height: calc(100vh - 149px);
}

.work-category-panel {
  padding-top: 14px;
  background: #f8f8f8;
  border-right: 1px solid #d9d9d9;
}

.work-category {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 48px;
  padding: 0 22px 0 38px;
  border: 0;
  border-left: 3px solid transparent;
  background: transparent;
  color: #4f5b66;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.work-category--active {
  border-left-color: #3b8cff;
  background: #ffffff;
  color: #1677ff;
  font-weight: 600;
}

.category-mail {
  width: 18px;
  height: 12px;
  border: 1px solid currentColor;
  opacity: 0.75;
}

.category-mail::before {
  content: '';
  display: block;
  width: 12px;
  height: 12px;
  margin: -1px auto 0;
  border-right: 1px solid currentColor;
  border-bottom: 1px solid currentColor;
  transform: rotate(45deg) scale(0.75);
  transform-origin: center;
}

.role-filter-title {
  margin: 18px 18px 8px;
  padding-top: 14px;
  border-top: 1px solid #e3e3e3;
  color: #8994a3;
  font-size: 12px;
}

.role-filter {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 36px;
  padding: 0 14px 0 38px;
  border: 0;
  border-left: 3px solid transparent;
  background: transparent;
  color: #415064;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.role-filter:disabled {
  color: #b3bbc6;
  cursor: not-allowed;
}

.role-filter--active {
  border-left-color: #3b8cff;
  background: #ffffff;
  color: #1677ff;
  font-weight: 600;
}

.role-avatar {
  position: relative;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #d9e3f5;
}

.role-avatar::before {
  position: absolute;
  top: 2px;
  left: 5px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8ea2c4;
  content: '';
}

.role-avatar::after {
  position: absolute;
  right: 3px;
  bottom: 2px;
  left: 3px;
  height: 6px;
  border-radius: 6px 6px 3px 3px;
  background: #ffffff;
  content: '';
}

.role-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-filter em {
  min-width: 18px;
  color: #9aa5b5;
  font-size: 12px;
  font-style: normal;
  text-align: right;
}

.work-list-panel {
  padding: 14px 16px 24px;
  background: #ffffff;
}

.work-list-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
  height: 34px;
  padding-left: 12px;
}

.work-list-title strong {
  color: #3f4d5c;
  font-size: 20px;
}

.work-list-title span {
  color: #8b95a1;
  font-size: 13px;
}

.work-list {
  min-height: 360px;
}

.work-row {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) 180px 180px;
  align-items: center;
  min-height: 88px;
  padding: 0 28px 0 12px;
  border: 1px solid #dedede;
  border-left: 3px solid #4d9bff;
  background: #ffffff;
}

.work-row + .work-row {
  margin-top: 6px;
}

.work-row--creating {
  opacity: 0.72;
}

.work-row--subflow {
  border-left-color: #b9c2cf;
  background: #fbfcfe;
}

.work-main {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  column-gap: 10px;
  align-items: center;
}

.form-icon {
  grid-row: 1 / span 2;
  color: #8795a8;
  font-size: 16px;
}

.work-name {
  display: block;
  width: fit-content;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: #697586;
  font-size: 14px;
  font-weight: 700;
  text-align: left;
  cursor: pointer;
}

.work-name:hover {
  color: #1677ff;
}

.work-row--subflow .work-name {
  color: #7d8795;
}

.work-row--subflow .work-name:hover {
  color: #d47900;
}

.work-summary {
  margin-top: 8px;
  overflow: hidden;
  color: #718096;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.work-meta,
.work-info {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  border: 0;
  background: transparent;
  color: #73808f;
  font-size: 14px;
  cursor: pointer;
}

.work-meta:hover,
.work-info:hover {
  color: #1677ff;
}

.workflow-icon,
.work-info .el-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 3px solid #b7b7b7;
  border-radius: 4px;
  color: #9b9b9b;
  font-size: 24px;
}

@media (max-width: 1180px) {
  .new-work-header {
    align-items: flex-start;
    flex-direction: column;
    padding: 12px 20px;
  }

  .new-work-tools {
    flex-wrap: wrap;
    width: 100%;
  }

  .workflow-search {
    width: min(100%, 300px);
  }

  .work-row {
    grid-template-columns: 1fr;
    gap: 12px;
    padding: 14px;
  }
}

@media (max-width: 760px) {
  .new-work-shell {
    grid-template-columns: 1fr;
  }

  .work-category-panel {
    display: flex;
    overflow-x: auto;
    padding: 0;
    border-right: 0;
    border-bottom: 1px solid #d9d9d9;
  }

  .work-category {
    width: auto;
    min-width: 118px;
    padding: 0 16px;
    border-left: 0;
    border-bottom: 3px solid transparent;
  }

  .work-category--active {
    border-bottom-color: #3b8cff;
  }

  .role-filter-title,
  .role-filter {
    display: none;
  }
}
</style>
