<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Download, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import {
  exportReportCenter,
  fetchReportCenter,
  type LedgerRow,
  type ReportCenter,
  type ReportChart
} from '../api/judicial';

const router = useRouter();
const loading = ref(false);
const exporting = ref(false);
const errorMessage = ref('');
const keyword = ref('');
const activeStatus = ref('全部状态');
const currentPage = ref(1);
const pageSize = ref(20);
const board = ref<ReportCenter | null>(null);

const chartColors = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#db2777', '#4b5563'];

const rows = computed(() => board.value?.rows ?? []);
const total = computed(() => board.value?.total ?? 0);
const statusOptions = computed(() => board.value?.statusOptions ?? ['全部状态']);
const charts = computed(() => board.value?.charts ?? []);

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date);
}

function chartMax(chart: ReportChart): number {
  return Math.max(...chart.items.map((item) => item.value), 1);
}

function barWidth(chart: ReportChart, value: number): string {
  return `${Math.max(4, Math.round((value / chartMax(chart)) * 100))}%`;
}

function donutStyle(chart: ReportChart): Record<string, string> {
  const totalValue = chart.items.reduce((sum, item) => sum + item.value, 0);
  if (totalValue <= 0) {
    return { background: '#e5e7eb' };
  }
  let cursor = 0;
  const segments = chart.items.map((item, index) => {
    const start = cursor;
    cursor += (item.value / totalValue) * 100;
    return `${chartColors[index % chartColors.length]} ${start}% ${cursor}%`;
  });
  return { background: `conic-gradient(${segments.join(', ')})` };
}

async function loadData(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    board.value = await fetchReportCenter({
      keyword: keyword.value,
      status: activeStatus.value === '全部状态' ? undefined : activeStatus.value,
      page: currentPage.value,
      pageSize: pageSize.value
    });
    currentPage.value = board.value.page;
    pageSize.value = board.value.pageSize;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载报表数据失败';
  } finally {
    loading.value = false;
  }
}

async function handleExport(): Promise<void> {
  exporting.value = true;
  try {
    const { blob, filename } = await exportReportCenter({
      keyword: keyword.value,
      status: activeStatus.value === '全部状态' ? undefined : activeStatus.value
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename || `report-center-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    ElMessage.success('报表已导出');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    exporting.value = false;
  }
}

function submitSearch(): void {
  currentPage.value = 1;
  void loadData();
}

function resetFilters(): void {
  keyword.value = '';
  activeStatus.value = '全部状态';
  currentPage.value = 1;
  void loadData();
}

function openDetail(row: LedgerRow): void {
  if (row.relatedPath) {
    void router.push(row.relatedPath);
  }
}

watch([activeStatus, pageSize], () => {
  currentPage.value = 1;
  void loadData();
});

watch(currentPage, () => {
  void loadData();
});

onMounted(() => {
  void loadData();
});
</script>

<template>
  <main class="report-page">
    <section class="report-header">
      <div>
        <h3 class="panel-title">{{ board?.moduleName || '报表中心' }}</h3>
        <p class="panel-subtitle">{{ board?.description || '根据流程表单填写情况自动收集统计信息。' }}</p>
      </div>
      <div class="report-actions">
        <el-button :icon="Refresh" @click="loadData">刷新数据</el-button>
        <el-button type="primary" :icon="Download" :loading="exporting" @click="handleExport">导出 CSV</el-button>
      </div>
    </section>

    <section class="metric-grid" v-loading="loading">
      <article v-for="metric in board?.metrics || []" :key="metric.label" class="metric-card" :class="{ 'is-accent': metric.accent }">
        <span>{{ metric.label }}</span>
        <strong>{{ metric.value }}</strong>
      </article>
    </section>

    <section class="filter-band">
      <el-radio-group v-model="activeStatus" size="small">
        <el-radio-button v-for="opt in statusOptions" :key="opt" :label="opt">
          {{ opt }}
        </el-radio-button>
      </el-radio-group>
      <div class="filter-search">
        <el-input
          v-model="keyword"
          placeholder="搜索案件编号、标题或委托方..."
          :prefix-icon="Search"
          clearable
          @keyup.enter="submitSearch"
        />
        <el-button type="primary" :icon="Search" @click="submitSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>

    <section class="chart-grid" v-loading="loading">
      <article v-for="chart in charts" :key="chart.code" class="chart-panel">
        <header>
          <h4>{{ chart.title }}</h4>
          <span>{{ chart.items.reduce((sum, item) => sum + item.value, 0) }}</span>
        </header>

        <div v-if="chart.type === 'donut'" class="donut-layout">
          <div class="donut" :style="donutStyle(chart)" />
          <ul class="chart-legend">
            <li v-for="(item, index) in chart.items" :key="item.label">
              <i :style="{ backgroundColor: chartColors[index % chartColors.length] }" />
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </li>
          </ul>
        </div>

        <div v-else class="bar-list">
          <div v-for="(item, index) in chart.items" :key="item.label" class="bar-row">
            <div class="bar-row__meta">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
            <div class="bar-track">
              <div class="bar-fill" :style="{ width: barWidth(chart, item.value), backgroundColor: chartColors[index % chartColors.length] }" />
            </div>
          </div>
        </div>
      </article>
    </section>

    <section class="table-panel">
      <div class="table-heading">
        <div>
          <h4>案件明细</h4>
          <p>共 {{ total }} 条，当前第 {{ board?.page || currentPage }} / {{ board?.totalPages || 0 }} 页</p>
        </div>
      </div>

      <el-table :data="rows" v-loading="loading" stripe border>
        <el-table-column prop="primaryText" label="案件编号" width="210" fixed="left" />
        <el-table-column prop="secondaryText" label="案件标题" min-width="260" show-overflow-tooltip />
        <el-table-column prop="ownerName" label="委托单位" width="180" show-overflow-tooltip />
        <el-table-column prop="tertiaryText" label="案件类别" width="150" />
        <el-table-column prop="statusLabel" label="业务状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.statusLabel === '已办结' ? 'success' : row.statusLabel === '超期提醒' ? 'danger' : 'warning'" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progressLabel" label="当前节点" width="190" show-overflow-tooltip />
        <el-table-column prop="metricText" label="归档" width="130" />
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-footer">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
        />
      </div>
    </section>
  </main>
</template>

<style scoped>
.report-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-header,
.filter-band,
.table-panel,
.chart-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 16px;
}

.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.report-actions,
.filter-search {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-search {
  min-width: 420px;
}

.filter-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
}

.metric-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: var(--el-bg-color);
  padding: 14px;
}

.metric-card span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  color: var(--el-text-color-primary);
  font-size: 22px;
}

.metric-card.is-accent strong {
  color: var(--el-color-primary);
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
}

.chart-panel header,
.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.chart-panel h4,
.table-heading h4 {
  margin: 0;
  font-size: 16px;
}

.chart-panel header span,
.table-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.bar-row__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
}

.bar-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--el-fill-color-light);
}

.bar-fill {
  height: 100%;
  border-radius: inherit;
}

.donut-layout {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.donut {
  width: 112px;
  height: 112px;
  border-radius: 50%;
  position: relative;
}

.donut::after {
  content: "";
  position: absolute;
  inset: 28px;
  border-radius: 50%;
  background: var(--el-bg-color);
}

.chart-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.chart-legend li {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.chart-legend i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.pagination-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.state-banner.is-error {
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 8px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  padding: 12px 14px;
}

@media (max-width: 900px) {
  .report-header,
  .filter-band {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-search {
    min-width: 0;
    width: 100%;
  }
}
</style>
