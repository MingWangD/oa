<script setup lang="ts">
import type { LedgerRow } from '../../api/judicial';

defineProps<{
  detailRow: LedgerRow | null;
  listMetricLabel: string;
  supportsWorkQueryDrilldown: boolean;
  workQueryActionLabel: string;
}>();

const emit = defineEmits<{
  'open-related-path': [row: LedgerRow];
  'open-work-query-drilldown': [row: LedgerRow];
}>();

const visible = defineModel<boolean>('visible', { required: true });

function formatDateTime(value: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  }).format(date);
}

function getStatusClass(status: string | undefined): string {
  const value = String(status ?? '').toUpperCase();
  if (value.includes('预警') || value.includes('终止') || value.includes('超期')) return 'is-danger';
  if (value.includes('跟进') || value.includes('履约') || value.includes('审批') || value.includes('办理')) return 'is-warning';
  if (value.includes('完成') || value.includes('沉淀')) return 'is-success';
  return 'is-primary';
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
  <el-drawer v-model="visible" :with-header="false" size="420px">
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

      <div v-if="detailRow.relatedPath" class="drawer-actions">
        <el-button type="primary" @click="emit('open-related-path', detailRow)">{{ relatedActionLabel(detailRow) }}</el-button>
        <el-button v-if="supportsWorkQueryDrilldown" @click="emit('open-work-query-drilldown', detailRow)">{{ workQueryActionLabel }}</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped>
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

.drawer-actions {
  display: flex;
  justify-content: flex-start;
  gap: 10px;
  flex-wrap: wrap;
}

/* Duplicated from parent (scoped styles don't cross component boundaries) */
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
</style>
