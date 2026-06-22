<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import {
  fetchLedgerBoard,
  fetchPlatformModules,
  fetchReconstructionPlan,
  type LedgerBoard as LedgerBoardData,
  type LedgerRow,
  type OaModule,
  type ReconstructionPhase
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';
import LedgerBoard from '../components/placeholder/LedgerBoard.vue';
import LedgerDrawer from '../components/placeholder/LedgerDrawer.vue';

interface SectionDefinition {
  code: string;
  title: string;
  moduleCode: string;
  prefixes: string[];
}

interface MenuEntry {
  menuName: string;
  path: string;
  menuType: string;
  children: MenuEntry[];
}

interface QuickAction {
  label: string;
  type: 'route' | 'export' | 'copy';
  path?: string;
}

const SECTION_DEFINITIONS: SectionDefinition[] = [
  {
    code: 'application-center',
    title: '应用中心',
    moduleCode: 'business-suite',
    prefixes: ['/placeholder/application']
  },
  {
    code: 'business-suite',
    title: '业务管理',
    moduleCode: 'business-suite',
    prefixes: ['/placeholder/crm', '/placeholder/performance', '/placeholder/contract', '/placeholder/project', '/placeholder/warehouse', '/placeholder/risk']
  },
  {
    code: 'admin-office',
    title: '行政办公',
    moduleCode: 'business-suite',
    prefixes: ['/placeholder/admin-office', '/placeholder/supervision', '/placeholder/portal', '/placeholder/report-center']
  },
  {
    code: 'people-docs',
    title: '人资/考勤/公文/档案',
    moduleCode: 'business-suite',
    prefixes: ['/placeholder/hr', '/placeholder/attendance', '/placeholder/official-doc', '/placeholder/archive', '/placeholder/community']
  },
  {
    code: 'integration',
    title: '开放与集成平台',
    moduleCode: 'integration',
    prefixes: ['/placeholder/integration']
  },
  {
    code: 'system-center',
    title: '系统管理',
    moduleCode: 'platform',
    prefixes: ['/placeholder/system']
  },
  {
    code: 'workflow-report',
    title: '数据报表',
    moduleCode: 'business-suite',
    prefixes: ['/placeholder/workflow/report']
  }
];

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const loading = ref(false);
const errorMessage = ref('');
const modules = ref<OaModule[]>([]);
const phases = ref<ReconstructionPhase[]>([]);
const ledgerBoard = ref<LedgerBoardData | null>(null);
const keyword = ref('');
const statusFilter = ref('all');
const detailVisible = ref(false);
const detailRow = ref<LedgerRow | null>(null);
const exporting = ref(false);

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
  { prefix: '/placeholder/workflow/report', code: 'report-center' },
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
    implemented: '已实现',
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

function getFilterStorageKey(boardCode: string): string {
  return `oa:module-board:${boardCode}:filters`;
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
  archived: '已入库',
  pending: '待补充',
  planning: '规划中',
  recent: '最近',
  enabled: '启用',
  disabled: '停用',
  success: '成功',
  failed: '失败'
}));
const statusButtons = computed(() => ledgerBoard.value?.statusOptions ?? ['all']);
const sourceTag = computed(() => {
  const sourceType = ledgerBoard.value?.sourceType;
  if (sourceType === 'live') {
    return { type: 'success' as const, label: '实时数据' };
  }
  if (sourceType === 'structured') {
    return { type: 'primary' as const, label: '结构化看板' };
  }
  return { type: 'warning' as const, label: '示例台账' };
});
const filterSummary = computed(() => {
  const parts: string[] = [];
  if (keyword.value.trim()) {
    parts.push(`关键词“${keyword.value.trim()}”`);
  }
  if (statusFilter.value !== 'all') {
    parts.push(`状态“${statusLabels.value[statusFilter.value] ?? statusFilter.value}”`);
  }
  return parts.length > 0 ? parts.join('，') : '全部数据';
});
const boardActions = computed<QuickAction[]>(() => {
  const boardCode = currentBoardCode.value;
  const map: Record<string, QuickAction[]> = {
    crm: [
      { label: '工作查询', type: 'route', path: '/work-query' },
      { label: '新建工作', type: 'route', path: '/case/new' },
      { label: '导出清单', type: 'export' }
    ],
    contract: [
      { label: '工作查询', type: 'route', path: '/work-query' },
      { label: '新建工作', type: 'route', path: '/case/new' },
      { label: '导出清单', type: 'export' }
    ],
    project: [
      { label: '我的工作', type: 'route', path: '/my-work' },
      { label: '工作查询', type: 'route', path: '/work-query' },
      { label: '导出清单', type: 'export' }
    ],
    'personal-task': [
      { label: '我的工作', type: 'route', path: '/my-work' },
      { label: '工作查询', type: 'route', path: '/work-query' },
      { label: '复制摘要', type: 'copy' }
    ],
    'unified-todo': [
      { label: '我的工作', type: 'route', path: '/my-work' },
      { label: '工作查询', type: 'route', path: '/work-query' },
      { label: '复制摘要', type: 'copy' }
    ],
    archive: [
      { label: '知识库', type: 'route', path: '/knowledge' },
      { label: '工作查询', type: 'route', path: '/work-query' },
      { label: '导出清单', type: 'export' }
    ],
    hr: [
      { label: '用户管理', type: 'route', path: '/admin/users' },
      { label: '复制摘要', type: 'copy' }
    ],
    'system-permission': [
      { label: '用户管理', type: 'route', path: '/admin/users' },
      { label: '导出清单', type: 'export' }
    ],
    'system-log': [
      { label: '导出清单', type: 'export' },
      { label: '复制摘要', type: 'copy' }
    ],
    'system-datasource': [
      { label: '刷新数据', type: 'copy' },
      { label: '导出清单', type: 'export' }
    ]
  };
  return map[boardCode] ?? [
    { label: '工作查询', type: 'route', path: '/work-query' },
    { label: '导出清单', type: 'export' },
    { label: '复制摘要', type: 'copy' }
  ];
});

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



function entryStateLabel(path: string, current: boolean): string {
  if (current) {
    return '当前';
  }
  if (path === '/admin/users' || path === '/placeholder/system/permission') {
    return '已开工';
  }
  if (BOARD_CODE_BY_PREFIX.some((item) => path.startsWith(item.prefix))) {
    return '已开工';
  }
  return '待建设';
}

/** 格式化日期时间（供 exportBoard 等函数内部使用） */
function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  }).format(date);
}

function persistFilters(): void {
  if (!currentBoardCode.value || typeof window === 'undefined') {
    return;
  }
  window.sessionStorage.setItem(
    getFilterStorageKey(currentBoardCode.value),
    JSON.stringify({
      keyword: keyword.value,
      statusFilter: statusFilter.value
    })
  );
}

function restoreFilters(boardCode: string): void {
  if (!boardCode || typeof window === 'undefined') {
    return;
  }
  const raw = window.sessionStorage.getItem(getFilterStorageKey(boardCode));
  if (!raw) {
    keyword.value = '';
    statusFilter.value = 'all';
    return;
  }
  try {
    const parsed = JSON.parse(raw) as { keyword?: string; statusFilter?: string };
    keyword.value = parsed.keyword ?? '';
    statusFilter.value = parsed.statusFilter ?? 'all';
  } catch {
    keyword.value = '';
    statusFilter.value = 'all';
  }
}

function applyFilters(): void {
  detailVisible.value = false;
  detailRow.value = null;
  persistFilters();
  void loadData();
}

function resetFilters(): void {
  keyword.value = '';
  statusFilter.value = 'all';
  detailVisible.value = false;
  detailRow.value = null;
  persistFilters();
  void loadData();
}

function openRowDetail(row: LedgerRow): void {
  detailRow.value = row;
  detailVisible.value = true;
}

async function openRelatedPath(row: LedgerRow): Promise<void> {
  if (!row.relatedPath) {
    return;
  }
  detailVisible.value = false;
  detailRow.value = null;
  await router.push({
    path: row.relatedPath,
    query: {
      from: route.fullPath,
      fromLabel: currentTitle.value,
      fromBoard: currentBoardCode.value
    }
  });
}

const supportsWorkQueryDrilldown = computed(() => ['crm', 'contract', 'project'].includes(currentBoardCode.value));

function buildWorkQueryForRow(row: LedgerRow): { keyword?: string; caseStatus?: string } {
  if (currentBoardCode.value === 'crm') {
    return { keyword: row.primaryText || undefined };
  }
  if (currentBoardCode.value === 'contract') {
    return { keyword: row.primaryText || row.secondaryText || undefined };
  }
  if (currentBoardCode.value === 'project') {
    return { keyword: row.tertiaryText || row.primaryText || undefined };
  }
  return { keyword: row.primaryText || undefined };
}

async function openWorkQueryDrilldown(row: LedgerRow): Promise<void> {
  const query = buildWorkQueryForRow(row);
  detailVisible.value = false;
  detailRow.value = null;
  await router.push({
    path: '/work-query',
    query: {
      ...query,
      from: route.fullPath,
      fromLabel: currentTitle.value
    }
  });
}

const workQueryActionLabel = computed<string>(() => {
  const map: Record<string, string> = {
    crm: '查看客户案件',
    contract: '查看合同清单',
    project: '查看项目清单'
  };
  return map[currentBoardCode.value] ?? '查看相关清单';
});

function exportBoard(): void {
  if (!ledgerBoard.value || typeof window === 'undefined') {
    return;
  }
  exporting.value = true;
  try {
    const headers = ['名称', '补充信息', '第三信息', '负责人', '状态', listMetricLabel.value, '推进说明', '操作提示', '标签', '最近更新', '截止时间', '业务事实'];
    const rows = ledgerBoard.value.rows.map((row) => [
      row.primaryText,
      row.secondaryText,
      row.tertiaryText,
      row.ownerName,
      row.statusLabel,
      row.metricText,
      row.progressLabel,
      row.actionHint,
      row.tags.join(' / '),
      formatDateTime(row.updatedTime),
      formatDateTime(row.deadlineTime),
      row.facts.join(' / ')
    ]);
    const csv = [headers, ...rows]
      .map((line) =>
        line
          .map((cell) => `"${String(cell ?? '').replace(/"/g, '""')}"`)
          .join(',')
      )
      .join('\n');
    const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${ledgerBoard.value.moduleCode}-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    window.URL.revokeObjectURL(url);
  } finally {
    exporting.value = false;
  }
}

async function copyBoardSummary(): Promise<void> {
  if (!ledgerBoard.value || typeof window === 'undefined' || !navigator.clipboard) {
    return;
  }
  const summary = [
    `${ledgerBoard.value.moduleName}`,
    `条件：${filterSummary.value}`,
    `记录数：${ledgerBoard.value.rows.length}`,
    ...ledgerBoard.value.metrics.map((item) => `${item.label}：${item.value}`)
  ].join('\n');
  await navigator.clipboard.writeText(summary);
}

async function runQuickAction(action: QuickAction): Promise<void> {
  if (action.type === 'route' && action.path) {
    await router.push(action.path);
    return;
  }
  if (action.type === 'export') {
    exportBoard();
    return;
  }
  await copyBoardSummary();
}

onMounted(() => {
  restoreFilters(currentBoardCode.value);
  void loadData();
});

watch(
  () => currentBoardCode.value,
  (boardCode) => {
    restoreFilters(boardCode);
    void loadData();
  }
);

watch([keyword, statusFilter], () => {
  persistFilters();
});
</script>

<template>
  <main class="page-container">
    <section class="content-card page-block">
      <div class="panel-heading panel-heading--warm">
        <div>
          <h3 class="panel-title">{{ currentTitle }}</h3>
        </div>
        <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
      </div>

      <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>

      <div class="module-center-layout">
        <div class="module-center-panel">
          <div class="module-center-panel__head">
            <h4>功能入口</h4>
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

      </div>
    </section>

    <LedgerBoard
      v-if="showsLedgerBoard && ledgerBoard"
      :ledger-board="ledgerBoard"
      :loading="loading"
      :exporting="exporting"
      v-model:keyword="keyword"
      v-model:status-filter="statusFilter"
      :source-tag="sourceTag"
      :filter-summary="filterSummary"
      :board-actions="boardActions"
      :list-metric-label="listMetricLabel"
      :status-labels="statusLabels"
      :status-buttons="statusButtons"
      :supports-work-query-drilldown="supportsWorkQueryDrilldown"
      :work-query-action-label="workQueryActionLabel"
      @apply-filters="applyFilters"
      @reset-filters="resetFilters"
      @export-board="exportBoard"
      @run-quick-action="runQuickAction"
      @open-row-detail="openRowDetail"
      @open-related-path="openRelatedPath"
      @open-work-query-drilldown="openWorkQueryDrilldown"
    />

    <LedgerDrawer
      v-model:visible="detailVisible"
      :detail-row="detailRow"
      :list-metric-label="listMetricLabel"
      :supports-work-query-drilldown="supportsWorkQueryDrilldown"
      :work-query-action-label="workQueryActionLabel"
      @open-related-path="openRelatedPath"
      @open-work-query-drilldown="openWorkQueryDrilldown"
    />
  </main>
</template>

<style scoped>
.module-center-layout {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 18px 20px 22px;
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

@media (max-width: 960px) {
  .module-center-layout {
    grid-template-columns: 1fr;
  }
}
</style>
