<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  fetchLedgerBoard,
  fetchPlatformModules,
  fetchReconstructionPlan,
  type LedgerBoard,
  type LedgerRow,
  type OaModule,
  type ReconstructionPhase
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';

interface SectionDefinition {
  code: string;
  title: string;
  moduleCode: string;
  focus: string;
  milestoneTitle: string;
  milestones: string[];
  nextActions: string[];
  prefixes: string[];
}

interface MenuEntry {
  menuName: string;
  path: string;
  menuType: string;
  children: MenuEntry[];
}

const SECTION_DEFINITIONS: SectionDefinition[] = [
  {
    code: 'application-center',
    title: '应用中心',
    moduleCode: 'business-suite',
    focus: '先建立应用入口、设计台账和权限承接，再逐步接入低代码应用与部门专属场景。',
    milestoneTitle: '本阶段先做什么',
    milestones: ['梳理我的应用与设计应用入口', '沉淀统一模块中心与权限承接方式', '为后续业务域应用挂接保留固定入口'],
    nextActions: ['补应用分组和收藏能力', '接入角色可见范围', '接入应用配置发布流程'],
    prefixes: ['/placeholder/application']
  },
  {
    code: 'business-suite',
    title: '业务管理',
    moduleCode: 'business-suite',
    focus: '第五阶段已经开始，先把 CRM、合同、项目、仓库和安全风险从菜单目录推进到可运营的模块中心。',
    milestoneTitle: '第一批落地重点',
    milestones: ['建立业务模块中心和统一状态视图', '梳理 CRM、合同、项目、仓库的共享台账能力', '复用统一流程、附件、审计和数据权限底座'],
    nextActions: ['优先补 CRM 与合同台账', '为项目和仓库准备状态流转与查询页面', '补报表和导出基线'],
    prefixes: ['/placeholder/crm', '/placeholder/performance', '/placeholder/contract', '/placeholder/project', '/placeholder/warehouse', '/placeholder/risk']
  },
  {
    code: 'admin-office',
    title: '行政办公',
    moduleCode: 'business-suite',
    focus: '行政办公、督查、门户和报表要逐步长成完整 OA 的公共工作面，不再停留在占位目录。',
    milestoneTitle: '当前建设方向',
    milestones: ['承接公告新闻、会议、资产等日常协同入口', '补督查督办、门户和报表中心的统一入口层', '复用消息、权限和审计能力'],
    nextActions: ['补公告新闻与会议台账', '补门户模块配置入口', '补报表中心分组和权限基线'],
    prefixes: ['/placeholder/admin-office', '/placeholder/supervision', '/placeholder/portal', '/placeholder/report-center']
  },
  {
    code: 'people-docs',
    title: '人资/考勤/公文/档案',
    moduleCode: 'business-suite',
    focus: '这一组是旧 OA 的高频区，后续要重点补人资、公文和档案的主页面、权限和状态查询。',
    milestoneTitle: '当前建设方向',
    milestones: ['为人力资源、考勤、公文、档案建立统一入口页', '预留组织、审批、归档与报表复用位', '为交流园地保留消息与权限承接结构'],
    nextActions: ['优先补公文和档案台账', '补考勤与人资的查询入口', '规划交流园地与消息中心衔接'],
    prefixes: ['/placeholder/hr', '/placeholder/attendance', '/placeholder/official-doc', '/placeholder/archive', '/placeholder/community']
  },
  {
    code: 'integration',
    title: '开放与集成平台',
    moduleCode: 'integration',
    focus: '开放接口、SSO 和统一待办会在第五、六阶段之间衔接推进，当前先把入口和依赖关系放稳。',
    milestoneTitle: '当前建设方向',
    milestones: ['归拢外部系统集成、SSO、统一待办入口', '标记认证、回调、幂等和监控依赖', '为后续移动开放平台留扩展位'],
    nextActions: ['补集成清单与认证方式说明', '补统一待办对接基线', '补集成验收清单'],
    prefixes: ['/placeholder/integration']
  },
  {
    code: 'system-center',
    title: '系统管理',
    moduleCode: 'platform',
    focus: '系统管理继续承接完整 OA 的全局治理能力，包括权限、日志和数据源等后台功能。',
    milestoneTitle: '当前建设方向',
    milestones: ['承接权限管理、管理日志和系统数据源入口', '复用 RBAC、审计和配置基线', '为上线治理做后台准备'],
    nextActions: ['补权限矩阵管理页', '补管理日志查询页', '补数据源与集成配置页'],
    prefixes: ['/placeholder/system']
  }
];

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const errorMessage = ref('');
const modules = ref<OaModule[]>([]);
const phases = ref<ReconstructionPhase[]>([]);
const ledgerBoard = ref<LedgerBoard | null>(null);
const keyword = ref('');
const statusFilter = ref('all');
const detailVisible = ref(false);
const detailRow = ref<LedgerRow | null>(null);

const BOARD_CODE_BY_PREFIX: Array<{ prefix: string; code: string }> = [
  { prefix: '/placeholder/quick/mail', code: 'quick-mail' },
  { prefix: '/placeholder/quick/calendar', code: 'quick-calendar' },
  { prefix: '/placeholder/personal/message', code: 'personal-message' },
  { prefix: '/placeholder/personal/task', code: 'personal-task' },
  { prefix: '/placeholder/personal/log', code: 'personal-log' },
  { prefix: '/placeholder/personal/address', code: 'personal-address' },
  { prefix: '/placeholder/application/mine', code: 'application-mine' },
  { prefix: '/placeholder/application/design', code: 'application-design' },
  { prefix: '/placeholder/crm', code: 'crm' },
  { prefix: '/placeholder/performance', code: 'performance' },
  { prefix: '/placeholder/contract', code: 'contract' },
  { prefix: '/placeholder/project', code: 'project' },
  { prefix: '/placeholder/warehouse', code: 'warehouse' },
  { prefix: '/placeholder/risk', code: 'risk' },
  { prefix: '/placeholder/admin-office/notice', code: 'notice' },
  { prefix: '/placeholder/admin-office/meeting', code: 'meeting' },
  { prefix: '/placeholder/admin-office/asset', code: 'asset' },
  { prefix: '/placeholder/hr', code: 'hr' },
  { prefix: '/placeholder/attendance', code: 'attendance' },
  { prefix: '/placeholder/official-doc', code: 'official-doc' },
  { prefix: '/placeholder/archive', code: 'archive' },
  { prefix: '/placeholder/community', code: 'community' },
  { prefix: '/placeholder/supervision', code: 'supervision' },
  { prefix: '/placeholder/portal', code: 'portal' },
  { prefix: '/placeholder/report-center', code: 'report-center' },
  { prefix: '/placeholder/integration/open-api', code: 'open-api' },
  { prefix: '/placeholder/integration/sso', code: 'sso' },
  { prefix: '/placeholder/integration/todo', code: 'unified-todo' },
  { prefix: '/placeholder/system/permission', code: 'system-permission' },
  { prefix: '/placeholder/system/log', code: 'system-log' },
  { prefix: '/placeholder/system/datasource', code: 'system-datasource' }
];

function statusText(status: string): string {
  const map: Record<string, string> = {
    completed: '已完成',
    in_progress: '建设中',
    partial: '部分完成',
    cataloged: '已建目录',
    planned: '待建设'
  };
  return map[status] || status;
}

function flattenMenuEntries(entries: MenuEntry[]): MenuEntry[] {
  return entries.flatMap((item) => [item, ...flattenMenuEntries(item.children || [])]);
}

function findSectionByPath(path: string): SectionDefinition | null {
  return SECTION_DEFINITIONS.find((item) => item.prefixes.some((prefix) => path.startsWith(prefix))) ?? null;
}

async function loadData(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    if (authStore.menus.length === 0 && authStore.isAuthenticated) {
      await authStore.fetchMenus();
    }
    const moduleCode = currentBoardCode.value;
    const [moduleData, phaseData, boardData] = await Promise.all([
      fetchPlatformModules(),
      fetchReconstructionPlan(),
      moduleCode
        ? fetchLedgerBoard(moduleCode, {
            limit: 8,
            keyword: keyword.value.trim(),
            status: statusFilter.value === 'all' ? undefined : statusFilter.value
          })
        : Promise.resolve(null)
    ]);
    modules.value = moduleData;
    phases.value = phaseData;
    ledgerBoard.value = boardData;
    if (detailRow.value && boardData) {
      detailRow.value = boardData.rows.find((item) => item.rowKey === detailRow.value?.rowKey) ?? null;
      detailVisible.value = Boolean(detailRow.value);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载模块中心失败';
  } finally {
    loading.value = false;
  }
}

const currentSection = computed(() => {
  return findSectionByPath(route.path);
});

const currentModule = computed(() => {
  const section = currentSection.value;
  if (!section) {
    return null;
  }
  return modules.value.find((item) => item.code === section.moduleCode) ?? null;
});

const activePhase = computed(() => phases.value.find((item) => item.status === 'in_progress') ?? null);

const currentGroup = computed(() => {
  const path = route.path;
  return authStore.menus.find((group) => flattenMenuEntries(group.children || []).some((item) => item.path === path)) ?? null;
});

const currentMenuEntry = computed(() => {
  return flattenMenuEntries(authStore.menus as unknown as MenuEntry[]).find((item) => item.path === route.path) ?? null;
});

const currentBoardCode = computed(() => {
  const matched = BOARD_CODE_BY_PREFIX.find((item) => route.path.startsWith(item.prefix));
  return matched?.code ?? '';
});

const siblingEntries = computed(() => {
  const group = currentGroup.value;
  if (!group) {
    return [];
  }
  return flattenMenuEntries((group.children || []) as unknown as MenuEntry[])
    .filter((item) => item.menuType?.toUpperCase() !== 'M')
    .map((item) => ({
      title: item.menuName,
      path: item.path,
      current: item.path === route.path
    }));
});

const currentTitle = computed(() => currentMenuEntry.value?.menuName ?? currentSection.value?.title ?? '模块中心');
const showsLedgerBoard = computed(() => Boolean(currentBoardCode.value));
const statusLabels = computed<Record<string, string>>(() => ({
  all: '全部',
  active: '跟进中',
  urgent: '紧急',
  stabilized: '已沉淀',
  drafting: '草拟中',
  reviewing: '审批中',
  closed: '已收口',
  terminated: '已终止',
  processing: '推进中',
  warning: '预警中',
  pending: '待处理',
  planning: '规划中',
  recent: '最近',
  enabled: '启用',
  disabled: '停用'
}));
const statusButtons = computed(() => ledgerBoard.value?.statusOptions ?? ['all']);

const listMetricLabel = computed(() => {
  const labels: Record<string, string> = {
    crm: '跟进概览',
    contract: '合同事项',
    project: '当前环节',
    'personal-task': '任务摘要',
    performance: '绩效摘要',
    risk: '风险摘要',
    supervision: '督办摘要',
    archive: '归档摘要',
    'system-log': '操作摘要'
  };
  return labels[currentBoardCode.value] ?? '概览';
});

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function getStatusClass(status: string | undefined): string {
  const value = String(status ?? '').toUpperCase();
  if (value.includes('预警') || value.includes('终止') || value.includes('超期')) {
    return 'is-danger';
  }
  if (value.includes('跟进') || value.includes('履约') || value.includes('审批') || value.includes('办理')) {
    return 'is-warning';
  }
  if (value.includes('完成') || value.includes('沉淀')) {
    return 'is-success';
  }
  return 'is-primary';
}

function entryStateLabel(path: string, current: boolean): string {
  if (current) {
    return '当前';
  }
  if (BOARD_CODE_BY_PREFIX.some((item) => path.startsWith(item.prefix))) {
    return '已开工';
  }
  return '待建设';
}

function applyFilters(): void {
  detailVisible.value = false;
  detailRow.value = null;
  void loadData();
}

function resetFilters(): void {
  keyword.value = '';
  statusFilter.value = 'all';
  detailVisible.value = false;
  detailRow.value = null;
  void loadData();
}

function openRowDetail(row: LedgerRow): void {
  detailRow.value = row;
  detailVisible.value = true;
}

onMounted(() => {
  void loadData();
});

watch(
  () => route.path,
  () => {
    void loadData();
  }
);
</script>

<template>
  <section class="page-block">
    <div class="overview-strip">
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">当前域</p>
        <p class="overview-value overview-value--compact">{{ currentSection?.title ?? '完整 OA' }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">当前模块</p>
        <p class="overview-value overview-value--compact">{{ currentTitle }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">共享能力</p>
        <p class="overview-value">{{ currentModule?.requiredCapabilities.length ?? 0 }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card is-accent">
        <p class="overview-label">当前阶段</p>
        <p class="overview-value">{{ activePhase?.phase.replace('第', '').replace('阶段', '') ?? '5' }}</p>
      </el-card>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">{{ currentTitle }}</h3>
        <p class="panel-subtitle">
          {{ currentSection?.focus ?? '该模块已纳入完整 OA 重构范围，当前开始进入第五阶段业务域建设。' }}
        </p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>
    <div v-else class="state-banner">
      第五阶段已经开工。当前页面不再是纯占位，而是作为该业务域的模块中心，承接共享能力、建设重点和后续落地顺序。
    </div>

    <div class="module-center-layout">
      <div class="module-center-panel">
        <div class="module-center-panel__head">
          <h4>同域入口</h4>
          <span>{{ currentGroup?.menuName ?? currentSection?.title ?? '模块分组' }}</span>
        </div>
        <div class="entry-list">
          <button
            v-for="entry in siblingEntries"
            :key="entry.path"
            type="button"
            class="entry-item"
            :class="{ 'is-current': entry.current }"
            @click="router.push(entry.path)"
          >
            <span>{{ entry.title }}</span>
            <el-tag size="small" effect="plain">{{ entryStateLabel(entry.path, entry.current) }}</el-tag>
          </button>
        </div>
      </div>

      <div class="module-center-panel">
        <div class="module-center-panel__head">
          <h4>{{ currentSection?.milestoneTitle ?? '当前建设方向' }}</h4>
          <span>{{ statusText(currentModule?.implementationStatus ?? 'planned') }}</span>
        </div>
        <ul class="compact-list">
          <li v-for="item in currentSection?.milestones ?? []" :key="item">{{ item }}</li>
        </ul>
      </div>
    </div>
  </section>

  <section v-if="showsLedgerBoard && ledgerBoard" class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">{{ ledgerBoard.moduleName }}</h3>
        <p class="panel-subtitle">{{ ledgerBoard.description }}</p>
      </div>
      <el-tag :type="ledgerBoard.sourceType === 'live' ? 'success' : 'warning'" effect="plain">
        {{ ledgerBoard.sourceType === 'live' ? '实时案件派生' : '示例台账' }}
      </el-tag>
    </div>

    <el-form class="query-bar" :inline="true" @submit.prevent="applyFilters">
      <el-form-item label="关键词">
        <el-input v-model="keyword" placeholder="名称、编号、委托单位" clearable style="width: 260px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="statusFilter" class="ledger-filter-group">
          <el-radio-button v-for="option in statusButtons" :key="option" :label="option">
            {{ statusLabels[option] ?? option }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <div class="query-actions">
          <el-button type="primary" :loading="loading" @click="applyFilters">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </el-form-item>
    </el-form>

    <div class="ledger-metric-grid">
      <article v-for="metric in ledgerBoard.metrics" :key="metric.label" class="ledger-metric-card" :class="{ 'is-accent': metric.accent }">
        <p class="overview-label">{{ metric.label }}</p>
        <p class="overview-value ledger-metric-value">{{ metric.value }}</p>
      </article>
    </div>

    <div class="table-frame">
      <el-table :data="ledgerBoard.rows" border stripe>
        <el-table-column label="名称" min-width="220">
          <template #default="scope">
            <div class="name-cell">
              <span class="primary-text">{{ scope.row.primaryText || '-' }}</span>
              <span class="secondary-text">{{ scope.row.secondaryText || '-' }}</span>
              <span class="secondary-text">{{ scope.row.tertiaryText || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" min-width="120">
          <template #default="scope">{{ scope.row.ownerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="scope">
            <el-tag class="status-tag" :class="getStatusClass(scope.row.statusLabel)" effect="plain">
              {{ scope.row.statusLabel || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="listMetricLabel" min-width="220">
          <template #default="scope">{{ scope.row.metricText || '-' }}</template>
        </el-table-column>
        <el-table-column label="推进说明" min-width="220">
          <template #default="scope">{{ scope.row.progressLabel || '-' }}</template>
        </el-table-column>
        <el-table-column label="标签" min-width="180">
          <template #default="scope">
            <div class="tag-row">
              <el-tag v-for="tag in scope.row.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近更新" min-width="170">
          <template #default="scope">{{ formatDateTime(scope.row.updatedTime) }}</template>
        </el-table-column>
        <el-table-column label="截止时间" min-width="170">
          <template #default="scope">{{ formatDateTime(scope.row.deadlineTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="openRowDetail(scope.row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">共享能力承接</h3>
        <p class="panel-subtitle">
          业务域会统一复用权限、流程、附件、审计、数据权限和报表能力，不再为每个模块各自造一套底层。
        </p>
      </div>
    </div>

    <div class="module-grid">
      <article class="module-card">
        <div class="module-card-head">
          <h4>{{ currentModule?.name ?? '完整 OA 业务域' }}</h4>
          <el-tag effect="plain">{{ statusText(currentModule?.implementationStatus ?? 'in_progress') }}</el-tag>
        </div>
        <p>{{ currentModule?.scope ?? '按模块逐步复刻管理员可见的完整 OA 能力。' }}</p>
        <div class="tag-row">
          <el-tag
            v-for="capability in currentModule?.requiredCapabilities ?? ['流程能力', '附件服务', '数据权限', '统一审计']"
            :key="capability"
            size="small"
            effect="plain"
          >
            {{ capability }}
          </el-tag>
        </div>
      </article>

      <article class="module-card">
        <div class="module-card-head">
          <h4>下一步</h4>
          <el-tag effect="plain" type="warning">收口顺序</el-tag>
        </div>
        <ul class="compact-list">
          <li v-for="item in ledgerBoard?.nextActions ?? currentSection?.nextActions ?? []" :key="item">{{ item }}</li>
        </ul>
      </article>
    </div>
  </section>

  <el-drawer v-model="detailVisible" :with-header="false" size="420px">
    <div v-if="detailRow" class="ledger-drawer">
      <div class="ledger-drawer__head">
        <div>
          <h3 class="panel-title">{{ detailRow.primaryText }}</h3>
          <p class="panel-subtitle">{{ detailRow.secondaryText }}</p>
        </div>
        <el-tag class="status-tag" :class="getStatusClass(detailRow.statusLabel)" effect="plain">
          {{ detailRow.statusLabel }}
        </el-tag>
      </div>

      <div class="drawer-metric">
        <p class="drawer-metric__label">{{ listMetricLabel }}</p>
        <p class="drawer-metric__value">{{ detailRow.metricText }}</p>
      </div>

      <div class="module-center-panel">
        <div class="module-center-panel__head">
          <h4>当前说明</h4>
          <span>{{ detailRow.ownerName || '-' }}</span>
        </div>
        <ul class="compact-list">
          <li>{{ detailRow.progressLabel || '-' }}</li>
          <li>{{ detailRow.actionHint || '-' }}</li>
          <li>最近更新：{{ formatDateTime(detailRow.updatedTime) }}</li>
          <li>截止时间：{{ formatDateTime(detailRow.deadlineTime) }}</li>
        </ul>
      </div>

      <div class="module-center-panel">
        <div class="module-center-panel__head">
          <h4>业务事实</h4>
          <span>{{ detailRow.tertiaryText || '详情' }}</span>
        </div>
        <ul class="compact-list">
          <li v-for="fact in detailRow.facts" :key="fact">{{ fact }}</li>
        </ul>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
.overview-value--compact {
  font-size: 22px;
}

.module-center-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 18px 20px 22px;
}

.ledger-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  padding: 18px 20px 10px;
}

.ledger-metric-card {
  padding: 16px 18px;
  border: 1px solid rgba(229, 215, 199, 0.9);
  border-radius: 12px;
  background: linear-gradient(180deg, #fffdfa 0%, #faf4eb 100%);
}

.ledger-metric-card.is-accent {
  border-color: rgba(196, 60, 47, 0.2);
  background: linear-gradient(180deg, #fff8f6 0%, #fdf0ec 100%);
}

.ledger-metric-value {
  font-size: 26px;
}

.ledger-filter-group {
  flex-wrap: wrap;
}

.module-center-panel {
  min-width: 0;
  border: 1px solid var(--td-border);
  border-radius: 12px;
  background: var(--td-card-muted);
  padding: 16px;
}

.module-center-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.module-center-panel__head h4 {
  margin: 0;
  font-size: 16px;
}

.module-center-panel__head span {
  color: var(--td-text-light);
  font-size: 12px;
}

.entry-list {
  display: grid;
  gap: 10px;
}

.entry-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--td-border);
  border-radius: 10px;
  background: #fff;
  color: var(--td-text);
  text-align: left;
}

.entry-item.is-current {
  border-color: var(--td-accent);
  background: var(--td-accent-soft);
}

.ledger-drawer {
  display: grid;
  gap: 16px;
}

.ledger-drawer__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.drawer-metric {
  padding: 16px;
  border: 1px solid var(--td-border);
  border-radius: 12px;
  background: var(--td-card-muted);
}

.drawer-metric__label {
  margin: 0;
  color: var(--td-text-secondary);
  font-size: 13px;
}

.drawer-metric__value {
  margin: 10px 0 0;
  color: var(--td-text);
  font-size: 18px;
  font-weight: 600;
}

@media (max-width: 960px) {
  .module-center-layout {
    grid-template-columns: 1fr;
  }

  .ledger-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
