<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import {
  fetchWorkbenchDone,
  fetchWorkbenchSummary,
  fetchWorkbenchTodo,
  type TaskSummary,
  type WorkbenchSummary
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const loading = ref(false);
const errorMessage = ref('');
const activeTab = ref<'todo' | 'done'>('todo');
const summary = ref<WorkbenchSummary>({
  todoCount: 0,
  doneCount: 0,
  processingCount: 0,
  overdueCount: 0
});
const todoList = ref<TaskSummary[]>([]);
const doneList = ref<TaskSummary[]>([]);

const summaryCards = computed(() => [
  { label: '待办工作', value: summary.value.todoCount },
  { label: '办结工作', value: summary.value.doneCount },
  { label: '办理中案件', value: summary.value.processingCount },
  { label: '超期待办', value: summary.value.overdueCount, accent: true }
]);

const currentList = computed(() => (activeTab.value === 'todo' ? todoList.value : doneList.value));
const currentSummary = computed(() => (activeTab.value === 'todo' ? '待办工作' : '办结工作'));

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

function getDeadlineLevel(deadlineTime: string | null): 'danger' | 'warning' | 'muted' {
  if (!deadlineTime) {
    return 'muted';
  }

  const deadline = new Date(deadlineTime);
  if (Number.isNaN(deadline.getTime())) {
    return 'muted';
  }

  const remaining = deadline.getTime() - Date.now();
  if (remaining <= 0) {
    return 'danger';
  }

  if (remaining <= 24 * 60 * 60 * 1000) {
    return 'warning';
  }

  return 'muted';
}

function getStatusClass(status: string | undefined): string {
  const value = String(status ?? '').toUpperCase();
  if (value.includes('OVERDUE') || value.includes('REJECT') || value.includes('TERMINATED')) {
    return 'is-danger';
  }

  if (value.includes('PROCESS') || value.includes('REVIEW') || value.includes('PENDING') || value.includes('TODO')) {
    return 'is-warning';
  }

  if (value.includes('DONE') || value.includes('COMPLETE') || value.includes('FINISH')) {
    return 'is-success';
  }

  return 'is-primary';
}

async function ensureUserId(): Promise<number | null> {
  if (authStore.user?.id) {
    return authStore.user.id;
  }

  try {
    const user = await authStore.fetchCurrentUser();
    return user.id;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '未获取到当前登录用户';
    return null;
  }
}

async function loadData(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';

  try {
    const assigneeId = await ensureUserId();
    if (!assigneeId) {
      return;
    }

    const [summaryData, todoData, doneData] = await Promise.all([
      fetchWorkbenchSummary(assigneeId),
      fetchWorkbenchTodo(assigneeId),
      fetchWorkbenchDone(assigneeId)
    ]);
    summary.value = summaryData;
    todoList.value = todoData;
    doneList.value = doneData;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载我的工作失败';
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <section class="page-block">
    <div class="overview-strip">
      <el-card
        v-for="item in summaryCards"
        :key="item.label"
        shadow="never"
        class="overview-card"
        :class="{ 'is-accent': item.accent }"
      >
        <p class="overview-label">{{ item.label }}</p>
        <p class="overview-value">{{ item.value }}</p>
      </el-card>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">我的工作</h3>
        <p class="panel-subtitle">查看与当前登录人相关的待办和已办，优先处理临近到期事项。</p>
      </div>
      <div class="card-meta">
        <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
      </div>
    </div>

    <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>

    <div class="subtab-bar">
      <el-radio-group v-model="activeTab">
        <el-radio-button label="todo">待办工作</el-radio-button>
        <el-radio-button label="done">办结工作</el-radio-button>
      </el-radio-group>
      <div class="list-summary">
        <span>当前视图</span>
        <strong>{{ currentSummary }}</strong>
      </div>
    </div>

    <div class="table-frame">
      <el-table :data="currentList" border stripe :loading="loading">
        <el-table-column prop="taskTitle" label="任务名称" min-width="220">
          <template #default="scope">
            <div class="name-cell">
              <span class="primary-text">{{ scope.row.taskTitle || '-' }}</span>
              <span class="secondary-text">案件编号：{{ scope.row.caseId ?? '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="nodeName" label="当前环节" min-width="150">
          <template #default="scope">{{ scope.row.nodeName || scope.row.nodeCode || '-' }}</template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="办理人" width="120">
          <template #default="scope">{{ scope.row.assigneeName || '-' }}</template>
        </el-table-column>
        <el-table-column label="到期时间" min-width="170">
          <template #default="scope">
            <div class="deadline-stack">
              <span class="primary-text">{{ formatDateTime(scope.row.deadlineTime) }}</span>
              <span class="secondary-text" :class="`deadline-text is-${getDeadlineLevel(scope.row.deadlineTime)}`">
                {{
                  getDeadlineLevel(scope.row.deadlineTime) === 'danger'
                    ? '已超期'
                    : getDeadlineLevel(scope.row.deadlineTime) === 'warning'
                      ? '24 小时内到期'
                      : '正常'
                }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="130">
          <template #default="scope">
            <el-tag class="status-tag" :class="getStatusClass(scope.row.status)" effect="plain">
              {{ scope.row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          {{ loading ? '正在加载工作列表...' : activeTab === 'todo' ? '暂无待办工作' : '暂无办结工作' }}
        </template>
      </el-table>
    </div>
  </section>
</template>
