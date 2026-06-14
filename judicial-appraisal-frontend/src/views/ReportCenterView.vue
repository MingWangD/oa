<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Download, Refresh, Search } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import {
  fetchLedgerBoard,
  type LedgerBoard,
  type LedgerRow
} from '../api/judicial';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const errorMessage = ref('');
const keyword = ref('');
const activeStatus = ref('all');
const pageSize = ref(20);

const board = ref<LedgerBoard | null>(null);

const filteredRows = computed(() => {
  if (!board.value) return [];
  return board.value.rows;
});

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

async function loadData(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    const data = await fetchLedgerBoard('report-center', {
      keyword: keyword.value,
      status: activeStatus.value === 'all' ? undefined : activeStatus.value,
      limit: pageSize.value
    });
    board.value = data;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载报表数据失败';
  } finally {
    loading.value = false;
  }
}

function handleExport(action: string): void {
  ElMessage.success(`正在准备数据并导出：${action}`);
  // 模拟导出延迟
  setTimeout(() => {
    ElMessage.success(`${action} 导出成功`);
  }, 1500);
}

function openDetail(row: LedgerRow): void {
  if (row.relatedPath) {
    void router.push(row.relatedPath);
  }
}

watch([activeStatus, pageSize], () => {
  void loadData();
});

onMounted(() => {
  void loadData();
});
</script>

<template>
  <main class="page-container">
    <!-- 1. 数据报表核心内容区 -->
    <section class="content-card page-block">
      <div class="panel-heading panel-heading--warm">
        <div>
          <h3 class="panel-title">{{ board?.moduleName || '数据报表' }}</h3>
          <p class="panel-subtitle">{{ board?.description || '根据流程表单填写情况自动收集统计信息。' }}</p>
        </div>
        <div class="panel-actions">
          <el-button-group>
            <el-button :icon="Refresh" @click="loadData">刷新数据</el-button>
            <el-dropdown @command="handleExport">
              <el-button type="primary" :icon="Download">
                导出选项<el-icon class="el-icon--right"><arrow-down /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="action in board?.nextActions" :key="action" :command="action">
                    {{ action }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </el-button-group>
        </div>
      </div>

      <div class="filter-row">
        <div class="filter-main">
          <el-radio-group v-model="activeStatus" size="small">
            <el-radio-button label="all">全部状态</el-radio-button>
            <el-radio-button v-for="opt in board?.statusOptions" :key="opt" :label="opt">
              {{ opt }}
            </el-radio-button>
          </el-radio-group>
        </div>
        <div class="filter-aside">
          <el-input
            v-model="keyword"
            placeholder="搜索案件编号、标题或委托方..."
            prefix-icon="Search"
            clearable
            @keyup.enter="loadData"
            style="width: 280px"
          >
            <template #append>
              <el-button :icon="Search" @click="loadData" />
            </template>
          </el-input>
        </div>
      </div>

      <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>

      <el-table :data="filteredRows" v-loading="loading" stripe border style="width: 100%; margin-top: 16px">
        <el-table-column prop="primaryText" label="案件编号" width="220" fixed="left" />
        <el-table-column prop="secondaryText" label="案件标题" min-width="280" show-overflow-tooltip />
        <el-table-column prop="ownerName" label="委托单位" width="200" />
        <el-table-column prop="tertiaryText" label="案件类别" width="150" />
        <el-table-column prop="statusLabel" label="业务状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.statusLabel === '已办结' ? 'success' : 'warning'" size="small">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="progressLabel" label="当前节点" width="180" />
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">查看详情</el-link>
          </template>
        </el-table-column>
      </el-table>

      <!-- 5. 每页展示数据条数设置 -->
      <div class="pagination-footer">
        <div class="stats-info">
          共 {{ board?.rows.length || 0 }} 条记录
        </div>
        <el-pagination
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="sizes, prev, pager, next"
          :total="board?.rows.length || 0"
        />
      </div>
    </section>
  </main>
</template>

<style scoped>
.page-container {
  padding: 0;
}

.filter-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  gap: 16px;
}

.pagination-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  padding: 16px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.stats-info {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

:deep(.el-table) {
  --el-table-header-bg-color: var(--el-fill-color-light);
}
</style>
