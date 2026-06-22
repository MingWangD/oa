<script setup lang="ts">
import type { LedgerBoard as LedgerBoardData, LedgerRow } from '../../api/judicial';

interface QuickAction {
  label: string;
  type: 'route' | 'export' | 'copy';
  path?: string;
}

defineProps<{
  ledgerBoard: LedgerBoardData;
  loading: boolean;
  exporting: boolean;
  sourceTag: { type: 'success' | 'primary' | 'warning'; label: string };
  filterSummary: string;
  boardActions: QuickAction[];
  listMetricLabel: string;
  statusLabels: Record<string, string>;
  statusButtons: string[];
  supportsWorkQueryDrilldown: boolean;
  workQueryActionLabel: string;
}>();

const emit = defineEmits<{
  'apply-filters': [];
  'reset-filters': [];
  'export-board': [];
  'run-quick-action': [action: QuickAction];
  'open-row-detail': [row: LedgerRow];
  'open-related-path': [row: LedgerRow];
  'open-work-query-drilldown': [row: LedgerRow];
}>();

const keyword = defineModel<string>('keyword', { required: true });
const statusFilter = defineModel<string>('statusFilter', { required: true });

function getStatusClass(status: string | undefined): string {
  const value = String(status ?? '').toUpperCase();
  if (value.includes('预警') || value.includes('终止') || value.includes('超期')) return 'is-danger';
  if (value.includes('跟进') || value.includes('履约') || value.includes('审批') || value.includes('办理')) return 'is-warning';
  if (value.includes('完成') || value.includes('沉淀')) return 'is-success';
  return 'is-primary';
}

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  }).format(date);
}

function relatedActionLabel(row: LedgerRow): string {
  if (!row.relatedPath) return '继续办理';
  if (row.relatedPath.startsWith('/case/')) return '查看案件';
  if (row.relatedPath.startsWith('/knowledge')) return '查看知识库';
  if (row.relatedPath.startsWith('/admin/users')) return '查看用户';
  return '继续办理';
}
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">{{ ledgerBoard.moduleName }}</h3>
      </div>
      <div class="inline-actions">
        <el-tag :type="sourceTag.type" effect="plain">{{ sourceTag.label }}</el-tag>
        <el-button :loading="exporting" @click="emit('export-board')">导出 CSV</el-button>
      </div>
    </div>

    <el-form class="query-bar" :inline="true" @submit.prevent="emit('apply-filters')">
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
          <el-button type="primary" :loading="loading" @click="emit('apply-filters')">查询</el-button>
          <el-button @click="emit('reset-filters')">重置</el-button>
        </div>
      </el-form-item>
    </el-form>

    <div class="query-summary">
      <p class="query-summary-text">当前条件：<strong>{{ filterSummary }}</strong></p>
      <p class="query-summary-text">当前记录：<strong>{{ ledgerBoard.rows.length }}</strong> 条</p>
    </div>

    <div class="board-action-bar">
      <el-button
        v-for="action in boardActions"
        :key="action.label"
        :type="action.type === 'route' ? 'primary' : 'default'"
        plain
        @click="emit('run-quick-action', action)"
      >
        {{ action.label }}
      </el-button>
    </div>

    <div class="ledger-metric-grid">
      <article
        v-for="metric in ledgerBoard.metrics"
        :key="metric.label"
        class="ledger-metric-card"
        :class="{ 'is-accent': metric.accent }"
      >
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
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <div class="row-actions">
              <el-button link type="primary" @click="emit('open-row-detail', scope.row)">查看详情</el-button>
              <el-button v-if="scope.row.relatedPath" link @click="emit('open-related-path', scope.row)">
                {{ relatedActionLabel(scope.row) }}
              </el-button>
              <el-button v-if="supportsWorkQueryDrilldown" link @click="emit('open-work-query-drilldown', scope.row)">
                {{ workQueryActionLabel }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.ledger-metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  padding: 18px 20px 10px;
}

.board-action-bar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  padding: 0 20px 8px;
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

.row-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .ledger-metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
