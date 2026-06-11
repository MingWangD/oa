<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { fetchCases, type CaseItem, type PageResult } from '../api/judicial';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const errorMessage = ref('');
const pageData = ref<PageResult<CaseItem>>({
  records: [],
  total: 0,
  pageNo: 1,
  pageSize: 10
});

const filters = reactive({
  keyword: '',
  caseStatus: ''
});

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '待受理', value: 'TO_ACCEPT' },
  { label: '受理审核中', value: 'ACCEPT_REVIEWING' },
  { label: '受理退回', value: 'REJECTED_ACCEPTANCE' },
  { label: '补正中', value: 'CORRECTION_PENDING' },
  { label: '鉴定办理中', value: 'PROCESSING' },
  { label: '审核中', value: 'REVIEWING' },
  { label: '文书出具中', value: 'DOC_ISSUING' },
  { label: '已办结', value: 'COMPLETED' },
  { label: '待归档', value: 'ARCHIVED' },
  { label: '已终止', value: 'TERMINATED' }
];

const hasFilters = computed(() => Boolean(filters.keyword.trim() || filters.caseStatus));
const querySummary = computed(() => {
  const parts: string[] = [];
  if (filters.keyword.trim()) {
    parts.push(`关键词“${filters.keyword.trim()}”`);
  }
  if (filters.caseStatus) {
    const matched = statusOptions.find((item) => item.value === filters.caseStatus);
    if (matched) {
      parts.push(`状态“${matched.label}”`);
    }
  }
  return parts.length > 0 ? parts.join('，') : '全部案件';
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
  if (value.includes('TERMINATED') || value.includes('REJECTED')) {
    return 'is-danger';
  }
  if (value.includes('PROCESS') || value.includes('REVIEW') || value.includes('PENDING') || value.includes('DOC')) {
    return 'is-warning';
  }
  if (value.includes('COMPLETED') || value.includes('ARCHIVED')) {
    return 'is-success';
  }
  return 'is-primary';
}

async function loadCases(pageNo = pageData.value.pageNo): Promise<void> {
  loading.value = true;
  errorMessage.value = '';

  try {
    pageData.value = await fetchCases({
      keyword: filters.keyword.trim(),
      caseStatus: filters.caseStatus,
      pageNo,
      pageSize: pageData.value.pageSize
    });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载工作查询失败';
  } finally {
    loading.value = false;
  }
}

function search(): void {
  persistFiltersToRoute(1);
  void loadCases(1);
}

function resetFilters(): void {
  filters.keyword = '';
  filters.caseStatus = '';
  persistFiltersToRoute(1);
  void loadCases(1);
}

function handleCurrentChange(pageNo: number): void {
  persistFiltersToRoute(pageNo);
  void loadCases(pageNo);
}

function openCaseDetail(caseId: number): void {
  void router.push({
    path: `/case/${caseId}`,
    query: {
      from: route.fullPath,
      fromLabel: '工作查询'
    }
  });
}

function syncFiltersFromRoute(): void {
  filters.keyword = typeof route.query.keyword === 'string' ? route.query.keyword : '';
  filters.caseStatus = typeof route.query.caseStatus === 'string' ? route.query.caseStatus : '';
}

function persistFiltersToRoute(pageNo = 1): void {
  void router.replace({
    path: route.path,
    query: {
      ...(filters.keyword.trim() ? { keyword: filters.keyword.trim() } : {}),
      ...(filters.caseStatus ? { caseStatus: filters.caseStatus } : {}),
      ...(pageNo > 1 ? { pageNo: String(pageNo) } : {})
    }
  });
}

onMounted(() => {
  syncFiltersFromRoute();
  const initialPage = Number(route.query.pageNo);
  void loadCases(Number.isFinite(initialPage) && initialPage > 0 ? initialPage : 1);
});

watch(
  () => route.query,
  () => {
    syncFiltersFromRoute();
  }
);
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">工作查询</h3>
        <p class="panel-subtitle">按关键字和状态筛选当前有权限查看的业务流程。</p>
      </div>
    </div>

    <el-form class="query-bar" :inline="true" @submit.prevent="search">
      <el-form-item label="关键词">
        <el-input v-model="filters.keyword" placeholder="请输入案件名称或案号" clearable />
      </el-form-item>

      <el-form-item label="流程状态">
        <el-select v-model="filters.caseStatus" placeholder="请选择状态" clearable style="width: 220px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>

      <el-form-item>
        <div class="query-actions">
          <el-button type="primary" :loading="loading" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </el-form-item>
    </el-form>

    <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>

    <div v-else class="query-summary">
      <p class="query-summary-text">
        当前条件：<strong>{{ querySummary }}</strong>
      </p>
      <p class="query-summary-text">共找到<strong>{{ pageData.total }}</strong> 条记录</p>
    </div>

    <div class="table-frame">
      <el-table :data="pageData.records" border stripe :loading="loading">
        <el-table-column prop="caseNo" label="案号" min-width="160">
          <template #default="scope">{{ scope.row.caseNo || '-' }}</template>
        </el-table-column>
        <el-table-column prop="caseTitle" label="案件名称" min-width="220">
          <template #default="scope">
            <div class="name-cell">
              <span class="primary-text">{{ scope.row.caseTitle || '-' }}</span>
              <span class="secondary-text">委托单位：{{ scope.row.entrustOrgName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="130">
          <template #default="scope">
            <el-tag class="status-tag" :class="getStatusClass(scope.row.caseStatus)" effect="plain">
              {{ scope.row.caseStatusName || scope.row.caseStatus || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前环节" min-width="150">
          <template #default="scope">{{ scope.row.currentNodeName || scope.row.currentNodeCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="currentHandlerName" label="办理人" width="120">
          <template #default="scope">{{ scope.row.currentHandlerName || '-' }}</template>
        </el-table-column>
        <el-table-column label="截止时间" min-width="170">
          <template #default="scope">{{ formatDateTime(scope.row.deadlineTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="openCaseDetail(scope.row.id)">查看详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          {{
            loading
              ? '正在加载工作查询列表...'
              : hasFilters
                ? '当前筛选条件下暂无数据'
                : '暂无可展示的案件数据'
          }}
        </template>
      </el-table>
    </div>

    <div class="pager-bar">
      <p class="pager-text">第 {{ pageData.pageNo }} 页，每页 {{ pageData.pageSize }} 条</p>
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="pageData.pageNo"
        :page-size="pageData.pageSize"
        :total="pageData.total"
        @current-change="handleCurrentChange"
      />
    </div>
  </section>
</template>
