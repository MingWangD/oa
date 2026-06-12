<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ChatDotRound, InfoFilled, Search, Share } from '@element-plus/icons-vue';

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
const viewMode = ref<'task' | 'list' | 'overview'>('task');
const workflows = ref<JudicialWorkflowDefinition[]>([]);

const categories: WorkCategory[] = [
  {
    key: 'common',
    label: '常用工作',
    workflowCodes: [
      'payment-notice',
      'material-receive-return',
      'case-suspension',
      'received-entrust',
      'preliminary-survey',
      'reject-acceptance',
      'issue-draft-opinion',
      'final-opinion-review',
      'court-letter',
      'terminate-appraisal'
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
      'expense-reimbursement',
      'case-suspension'
    ]
  }
];

const userRoleNames = computed(() => authStore.roleNames);
const isAdmin = computed(() => authStore.isAdmin);

const visibleWorkflows = computed(() => {
  const selectedCategory = categories.find((item) => item.key === activeCategory.value);
  const categoryCodes = selectedCategory?.workflowCodes;
  const query = keyword.value.trim().toLowerCase();

  return workflows.value
    .filter((workflow) => canCreateWorkflow(workflow))
    .filter((workflow) => !categoryCodes || categoryCodes.includes(workflow.code))
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
    });
});

const activeCategoryLabel = computed(
  () => categories.find((item) => item.key === activeCategory.value)?.label ?? '全部工作'
);

const todoHint = computed(() => {
  const first = visibleWorkflows.value[0];
  if (!first) {
    return '当前角色暂无可新建工作';
  }
  return `${first.name} 等 ${visibleWorkflows.value.length} 项`;
});

onMounted(async () => {
  loading.value = true;
  try {
    const catalog = await fetchJudicialCatalog();
    workflows.value = catalog.workflows;
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
  return workflow.roles.some((role) => roleNames.includes(role));
}

function workflowSummary(workflow: JudicialWorkflowDefinition): string {
  const prefix = workflow.entryMode === 'direct' || workflow.entryMode.includes('direct') ? '可直接发起' : '按角色发起';
  const next = workflow.nextFlows.length ? `，后续：${workflow.nextFlows.slice(0, 2).join('、')}` : '';
  return `${prefix}${next}`;
}

function workflowTypeLabel(workflow: JudicialWorkflowDefinition): string {
  if (workflow.entryMode === 'direct') {
    return '直接发起';
  }
  if (workflow.entryMode.includes('linked')) {
    return '关联发起';
  }
  if (workflow.entryMode.includes('subflow')) {
    return '子流程';
  }
  return '流程';
}

async function quickCreate(workflow: JudicialWorkflowDefinition): Promise<void> {
  creatingCode.value = workflow.code;
  try {
    const created = await createCaseDraft({
      caseTitle: workflow.name,
      caseType: workflow.name,
      entrustOrgName: workflow.entryMode.includes('linked') ? '关联流程待补' : undefined,
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
      <h1>新建工作</h1>
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
          <el-radio-button label="task">任务视图</el-radio-button>
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
      </aside>

      <main class="work-list-panel">
        <div class="work-list-title">
          <strong>{{ activeCategoryLabel }}</strong>
          <span>根据当前角色显示可新建工作</span>
        </div>

        <el-empty v-if="!loading && visibleWorkflows.length === 0" description="当前角色暂无可新建工作" />

        <div v-loading="loading" class="work-list">
          <article v-for="workflow in visibleWorkflows" :key="workflow.code" class="work-row">
            <div class="work-main">
              <div class="work-name">{{ workflow.name }}</div>
              <div class="work-summary">{{ workflowSummary(workflow) }}</div>
            </div>

            <div class="work-meta">
              <span class="workflow-icon">
                <el-icon><Share /></el-icon>
              </span>
              <span>流程设计图</span>
            </div>

            <button class="work-info" type="button" @click="openGuide(workflow)">
              <el-icon><InfoFilled /></el-icon>
              <span>流程说明</span>
            </button>

            <div class="work-tags">
              <el-tag size="small" effect="plain">{{ workflowTypeLabel(workflow) }}</el-tag>
              <el-tag v-if="isAdmin" size="small" type="info" effect="plain">管理员可见</el-tag>
            </div>

            <div class="work-actions">
              <el-button
                type="primary"
                size="small"
                :loading="creatingCode === workflow.code"
                @click="quickCreate(workflow)"
              >
                快速新建
              </el-button>
              <el-button size="small" type="warning" @click="quickCreate(workflow)">新建向导</el-button>
            </div>
          </article>
        </div>
      </main>

      <aside class="new-work-float">
        <el-icon><ChatDotRound /></el-icon>
        <div>
          <strong>{{ authStore.displayName }}</strong>
          <span>{{ todoHint }}</span>
        </div>
        <em>{{ visibleWorkflows.length }}</em>
      </aside>
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
  grid-template-columns: minmax(280px, 1fr) 148px 142px minmax(130px, 190px) 178px;
  align-items: center;
  min-height: 78px;
  padding: 0 38px 0 12px;
  border: 1px solid #dedede;
  border-left: 3px solid #4d9bff;
  background: #ffffff;
}

.work-row + .work-row {
  margin-top: 6px;
}

.work-name {
  color: #697586;
  font-size: 14px;
  font-weight: 700;
}

.work-summary {
  width: min(520px, 100%);
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
  color: #73808f;
  font-size: 14px;
}

.work-info {
  border: 0;
  background: transparent;
  cursor: pointer;
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

.work-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.work-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.new-work-float {
  position: absolute;
  top: 38px;
  right: 66px;
  display: grid;
  grid-template-columns: 34px minmax(140px, 1fr) 24px;
  align-items: center;
  gap: 10px;
  min-width: 240px;
  padding: 10px 12px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #ffffff;
  box-shadow: 0 2px 12px rgb(0 0 0 / 12%);
}

.new-work-float .el-icon {
  color: #1683ff;
  font-size: 30px;
}

.new-work-float strong,
.new-work-float span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-work-float strong {
  color: #303642;
  font-size: 13px;
}

.new-work-float span {
  margin-top: 3px;
  color: #667281;
  font-size: 12px;
}

.new-work-float em {
  min-width: 24px;
  padding: 2px 5px;
  border-radius: 3px;
  background: #f52f5b;
  color: #ffffff;
  font-size: 12px;
  font-style: normal;
  text-align: center;
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

  .work-actions {
    justify-content: flex-start;
  }

  .new-work-float {
    display: none;
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
}
</style>
