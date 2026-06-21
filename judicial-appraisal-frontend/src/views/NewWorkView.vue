<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Document, InfoFilled, Search, Share } from '@element-plus/icons-vue';

import {
  createCaseDraft,
  fetchAvailableJudicialCatalog,
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
const judicialRoles = ref<string[]>([]);

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
const currentRoleLabel = computed(() => isAdmin.value ? '系统管理员' : (userRoleNames.value.join('、') || '未分配角色'));

const visibleWorkflows = computed(() => {
  const selectedCategory = categories.find((item) => item.key === activeCategory.value);
  const categoryCodes = selectedCategory?.workflowCodes;
  const query = keyword.value.trim().toLowerCase();

  let filtered = workflows.value.slice();

  if (activeCategory.value !== 'all' && categoryCodes) {
    filtered = filtered.filter((workflow) => categoryCodes.includes(workflow.code));
  }

  if (query) {
    filtered = filtered.filter((workflow) => {
      return [
        workflow.name,
        workflow.code,
        workflow.formCode,
        workflow.roles.join(' ')
      ].some((value) => value.toLowerCase().includes(query));
    });
  }

  return filtered.sort((left, right) => {
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

onMounted(async () => {
  loading.value = true;
  try {
    const catalog = await fetchAvailableJudicialCatalog();
    workflows.value = catalog.workflows;
    judicialRoles.value = catalog.dedicatedRoles;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载可新建工作失败');
  } finally {
    loading.value = false;
  }
});

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

const flowchartVisible = ref(false);
const explanationVisible = ref(false);
const currentWorkflow = ref<JudicialWorkflowDefinition | null>(null);
const flowchartTab = ref<'current' | 'overview' | 'detailed'>('current');

const flowchartMap: Record<string, string> = {
  'received-entrust': '收到委托书流程.png',
  'preliminary-survey': '初步勘验.jpg',
  'payment-notice': '发交费通知书及相关函件.jpg',
  'quality-control': '编制内部质量控制文件.jpg',
  'field-survey': '现场勘验.jpg',
  'material-receive-return': '材料接收与返还.jpg',
  'draft-opinion-review': '鉴定意见书征求意见稿送审稿编.jpg',
  'final-opinion-review': '鉴定意见书征求意见稿送审稿编.jpg',
  'reject-acceptance': '不予受理.jpg',
  'refund': '退费.jpg',
  'terminate-appraisal': '终止鉴定.jpg',
  'archive': '归档.jpg',
};

function hasSpecificFlowchart(workflow: JudicialWorkflowDefinition): boolean {
  return !!flowchartMap[workflow.code];
}

function openFlowchart(workflow: JudicialWorkflowDefinition) {
  currentWorkflow.value = workflow;
  flowchartTab.value = hasSpecificFlowchart(workflow) ? 'current' : 'overview';
  flowchartVisible.value = true;
}

function openExplanation(workflow: JudicialWorkflowDefinition) {
  currentWorkflow.value = workflow;
  explanationVisible.value = true;
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

        <div class="current-role-card">
          <span>当前登录角色</span>
          <strong>{{ currentRoleLabel }}</strong>
        </div>
      </aside>

      <main class="work-list-panel">
        <div class="work-list-title">
          <strong>{{ activeCategoryLabel }}</strong>
          <span>共 {{ visibleWorkflows.length }} 张表单，权限来自当前登录账号</span>
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

            <button class="work-meta" type="button" @click="openFlowchart(workflow)">
              <span class="workflow-icon">
                <el-icon><Share /></el-icon>
              </span>
              <span>流程设计图</span>
            </button>

            <button class="work-info" type="button" @click="openExplanation(workflow)">
              <el-icon><InfoFilled /></el-icon>
              <span>流程说明</span>
            </button>
          </article>
        </div>
      </main>
    </div>

    <!-- 流程设计图弹窗 -->
    <el-dialog
      v-model="flowchartVisible"
      title="流程设计图"
      width="80%"
      top="5vh"
      destroy-on-close
      custom-class="flowchart-dialog"
    >
      <!-- 提示横幅移到最上方 -->
      <div class="flowchart-tip" style="background: #fff7e6; border: 1px solid #ffd591; border-radius: 4px; padding: 8px 16px; color: #d46b08; font-size: 13px; font-weight: 500; display: flex; align-items: center; gap: 8px; text-align: left; margin-bottom: 16px; box-shadow: 0 2px 8px rgba(212, 107, 8, 0.05);">
        <span style="font-size: 16px;">💡</span>
        <span>提示：<strong>单击图片</strong>可开启全屏大图预览，支持鼠标滚轮缩放与按住拖拽查看。</span>
      </div>

      <div style="text-align: center; margin-bottom: 20px;">
        <el-radio-group v-model="flowchartTab" size="large">
          <el-radio-button
            v-if="currentWorkflow && hasSpecificFlowchart(currentWorkflow)"
            label="current"
          >
            当前步骤流程图
          </el-radio-button>
          <el-radio-button label="overview">全景工作流程图</el-radio-button>
          <el-radio-button label="detailed">细化工作流程图</el-radio-button>
        </el-radio-group>
      </div>

      <div class="flowchart-viewer" style="display: flex; justify-content: center; align-items: center; min-height: 400px; max-height: 70vh; overflow: auto; background: #fafafa; border: 1px solid #ebeef5; border-radius: 4px; padding: 10px;">
        <template v-if="flowchartTab === 'current' && currentWorkflow">
          <el-image
            :src="'/flowcharts/' + flowchartMap[currentWorkflow.code]"
            :preview-src-list="['/flowcharts/' + flowchartMap[currentWorkflow.code]]"
            fit="contain"
            style="max-width: 100%; max-height: 65vh;"
            hide-on-click-modal
          >
            <template #placeholder>
              <div class="image-slot" style="color: #909399; font-size: 14px;">加载中...</div>
            </template>
          </el-image>
        </template>
        <template v-else-if="flowchartTab === 'overview'">
          <el-image
            src="/flowcharts/b6130e9b27eacb5fd5b365f3272d4eba.png"
            :preview-src-list="['/flowcharts/b6130e9b27eacb5fd5b365f3272d4eba.png']"
            fit="contain"
            style="max-width: 100%; max-height: 65vh;"
            hide-on-click-modal
          >
            <template #placeholder>
              <div class="image-slot" style="color: #909399; font-size: 14px;">加载中...</div>
            </template>
          </el-image>
        </template>
        <template v-else-if="flowchartTab === 'detailed'">
          <el-image
            src="/flowcharts/ff4fe8d56d1e2775159a261ad55e2f74.png"
            :preview-src-list="['/flowcharts/ff4fe8d56d1e2775159a261ad55e2f74.png']"
            fit="contain"
            style="max-width: 100%; max-height: 65vh;"
            hide-on-click-modal
          >
            <template #placeholder>
              <div class="image-slot" style="color: #909399; font-size: 14px;">加载中...</div>
            </template>
          </el-image>
        </template>
      </div>

      <template #footer>
        <div style="text-align: right;">
          <el-button type="primary" @click="flowchartVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 流程说明弹窗 -->
    <el-dialog
      v-model="explanationVisible"
      :title="currentWorkflow ? currentWorkflow.name + ' - 流程说明' : '流程说明'"
      width="680px"
      destroy-on-close
      custom-class="explanation-dialog"
    >
      <div v-if="currentWorkflow" class="explanation-container" style="padding: 10px 5px;">

        <!-- 基础信息排版 -->
        <div class="guide-cards" style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px;">
          <div class="guide-card" style="background: #f4f7fc; border-radius: 8px; padding: 14px 18px; border-left: 4px solid #1677ff;">
            <h4 style="margin: 0 0 8px 0; color: #1f2d3d; font-size: 15px; display: flex; align-items: center; gap: 6px;">
              <el-icon style="color: #1677ff;"><InfoFilled /></el-icon>
              入口触发规则
            </h4>
            <p style="margin: 0; color: #5e6d82; font-size: 13px; line-height: 1.5;">
              {{ entryModeLabel(currentWorkflow) }}
            </p>
          </div>

          <div class="guide-card" style="background: #f4f7fc; border-radius: 8px; padding: 14px 18px; border-left: 4px solid #52c41a;">
            <h4 style="margin: 0 0 8px 0; color: #1f2d3d; font-size: 15px; display: flex; align-items: center; gap: 6px;">
              <el-icon style="color: #52c41a;"><Document /></el-icon>
              关联业务表单
            </h4>
            <p style="margin: 0; color: #5e6d82; font-size: 13px; line-height: 1.5; font-family: monospace;">
              {{ currentWorkflow.formCode }}
            </p>
          </div>
        </div>

        <!-- 办理角色 -->
        <div style="margin-bottom: 24px; background: #fafafa; border-radius: 8px; padding: 14px 18px;">
          <h4 style="margin: 0 0 10px 0; color: #1f2d3d; font-size: 15px; display: flex; align-items: center; gap: 6px;">
            <span style="display: inline-block; width: 4px; height: 14px; background: #3b8cff; border-radius: 2px;"></span>
            有权发起/办理角色
          </h4>
          <div style="display: flex; flex-wrap: wrap; gap: 8px;">
            <el-tag
              v-for="role in currentWorkflow.roles"
              :key="role"
              type="primary"
              effect="light"
              style="font-size: 13px; padding: 6px 12px; height: auto;"
            >
              {{ role }}
            </el-tag>
            <span v-if="!currentWorkflow.roles || currentWorkflow.roles.length === 0" style="color: #909399; font-size: 13px;">
              不限角色
            </span>
          </div>
        </div>

        <!-- 核心步骤与审核规则 -->
        <div style="margin-bottom: 24px;">
          <h4 style="margin: 0 0 16px 0; color: #1f2d3d; font-size: 15px; display: flex; align-items: center; gap: 6px;">
            <span style="display: inline-block; width: 4px; height: 14px; background: #3b8cff; border-radius: 2px;"></span>
            核心节点与办理规则
          </h4>
          <el-timeline style="padding-left: 5px;">
            <el-timeline-item
              v-for="(rule, index) in currentWorkflow.keyRules"
              :key="index"
              type="primary"
              size="large"
              :color="index === 0 ? '#1677ff' : '#909399'"
            >
              <div style="background: #f8fafc; border: 1px solid #eef2f6; border-radius: 6px; padding: 12px 16px; margin-top: -4px;">
                <span style="font-weight: 600; color: #3f4d5c; font-size: 13px; display: block; margin-bottom: 4px;">步骤 {{ index + 1 }}</span>
                <span style="color: #5e6d82; font-size: 13.5px; line-height: 1.6;">{{ rule }}</span>
              </div>
            </el-timeline-item>
            <el-timeline-item v-if="!currentWorkflow.keyRules || currentWorkflow.keyRules.length === 0">
              <span style="color: #909399; font-size: 13px;">暂无特定流转规则。</span>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 后续流转流程 -->
        <div style="background: #fafafa; border-radius: 8px; padding: 14px 18px;">
          <h4 style="margin: 0 0 10px 0; color: #1f2d3d; font-size: 15px; display: flex; align-items: center; gap: 6px;">
            <span style="display: inline-block; width: 4px; height: 14px; background: #3b8cff; border-radius: 2px;"></span>
            后续关联流程
          </h4>
          <div style="display: flex; flex-wrap: wrap; gap: 8px; align-items: center;">
            <template v-if="currentWorkflow.nextFlows && currentWorkflow.nextFlows.length > 0">
              <template v-for="(next, i) in currentWorkflow.nextFlows" :key="next">
                <el-tag type="info" effect="plain" style="font-size: 13px; padding: 6px 12px; height: auto;">
                  {{ next }}
                </el-tag>
                <span v-if="i < currentWorkflow.nextFlows.length - 1" style="color: #c0c4cc; font-size: 14px;">➔</span>
              </template>
            </template>
            <span v-else style="color: #67c23a; font-size: 13.5px; font-weight: 500; display: flex; align-items: center; gap: 4px;">
              🏁 流程结束
            </span>
          </div>
        </div>

      </div>
      <template #footer>
        <div style="text-align: right;">
          <el-button type="primary" @click="explanationVisible = false">知道了</el-button>
        </div>
      </template>
    </el-dialog>
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

.current-role-card {
  margin: 18px 14px 0;
  padding: 12px 14px;
  border: 1px solid #e3e3e3;
  background: #ffffff;
}

.current-role-card span {
  display: block;
  color: #8994a3;
  font-size: 12px;
}

.current-role-card strong {
  display: block;
  margin-top: 6px;
  color: #3f4d5c;
  font-size: 13px;
  line-height: 1.45;
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

  .current-role-card {
    display: none;
  }
}
</style>
