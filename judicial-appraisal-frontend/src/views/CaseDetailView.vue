<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import {
  fetchCaseDetail,
  submitWorkflowAction,
  type CaseDetail,
  type WorkflowActionCode
} from '../api/judicial';
import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const loading = ref(false);
const acting = ref(false);
const detail = ref<CaseDetail | null>(null);
const opinion = ref('');

const caseId = computed(() => Number(route.params.id));
const returnPath = computed(() => (typeof route.query.from === 'string' ? route.query.from : ''));
const returnLabel = computed(() => (typeof route.query.fromLabel === 'string' ? route.query.fromLabel : '上一页'));
const sourceBoard = computed(() => (typeof route.query.fromBoard === 'string' ? route.query.fromBoard : ''));
const hasEntrustOrg = computed(() => Boolean(detail.value?.entrustOrgName));
const hasStatus = computed(() => Boolean(detail.value?.caseStatus));

function formatDateTime(value: string | null): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN');
}

async function loadDetail(): Promise<void> {
  if (!caseId.value) {
    return;
  }
  loading.value = true;
  try {
    detail.value = await fetchCaseDetail(caseId.value);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载案件详情失败');
  } finally {
    loading.value = false;
  }
}

async function submitAction(actionCode: WorkflowActionCode): Promise<void> {
  if (!detail.value) {
    return;
  }
  acting.value = true;
  try {
    await submitWorkflowAction(detail.value.id, {
      actionCode,
      opinion: opinion.value || undefined,
      assigneeId: authStore.user?.id,
      assigneeName: authStore.user?.realName || authStore.user?.username
    });
    ElMessage.success('流程动作已提交');
    await loadDetail();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交流程动作失败');
  } finally {
    acting.value = false;
  }
}

async function goBack(): Promise<void> {
  if (returnPath.value) {
    await router.push(returnPath.value);
    return;
  }
  router.back();
}

async function openSiblingCasesByOrg(): Promise<void> {
  if (!detail.value?.entrustOrgName) {
    return;
  }
  await router.push({
    path: '/work-query',
    query: {
      keyword: detail.value.entrustOrgName,
      from: route.fullPath,
      fromLabel: '案件详情'
    }
  });
}

async function openSiblingCasesByStatus(): Promise<void> {
  if (!detail.value?.caseStatus) {
    return;
  }
  await router.push({
    path: '/work-query',
    query: {
      caseStatus: detail.value.caseStatus,
      from: route.fullPath,
      fromLabel: '案件详情'
    }
  });
}

onMounted(() => {
  void loadDetail();
});
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">案件详情</h3>
        <p class="panel-subtitle">展示案件主数据、当前环节和基础办理动作；后续接入动态表单渲染。</p>
      </div>
      <div class="query-actions">
        <el-button v-if="returnPath" @click="goBack">返回{{ returnLabel }}</el-button>
        <el-button :loading="loading" @click="loadDetail">刷新</el-button>
      </div>
    </div>

    <div v-if="detail" class="detail-body" v-loading="loading">
      <div v-if="returnPath" class="state-banner">
        当前从 <strong>{{ returnLabel }}</strong> 进入案件详情
        <span v-if="sourceBoard">，可在处理后直接返回原模块。</span>
      </div>

      <el-descriptions border :column="2" title="案件信息">
        <el-descriptions-item label="案件名称">{{ detail.caseTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="案件编号">{{ detail.caseNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="案件类型">{{ detail.caseType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.caseStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前环节">{{ detail.currentNodeName || detail.currentNodeCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前办理人">{{ detail.currentHandlerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="委托单位">{{ detail.entrustOrgName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ formatDateTime(detail.deadlineTime) }}</el-descriptions-item>
      </el-descriptions>

      <div class="action-panel">
        <h4>关联视图</h4>
        <div class="query-actions">
          <el-button v-if="hasEntrustOrg" @click="openSiblingCasesByOrg">查看同单位案件</el-button>
          <el-button v-if="hasStatus" @click="openSiblingCasesByStatus">查看同状态案件</el-button>
          <el-button v-if="returnPath" @click="goBack">返回{{ returnLabel }}</el-button>
        </div>
      </div>

      <div class="action-panel">
        <h4>办理意见</h4>
        <el-input v-model="opinion" type="textarea" :rows="4" placeholder="请输入办理意见" />
        <div class="query-actions">
          <el-button type="primary" :loading="acting" @click="submitAction('SUBMIT')">提交/启动</el-button>
          <el-button :loading="acting" @click="submitAction('APPROVE')">同意</el-button>
          <el-button :loading="acting" @click="submitAction('RETURN')">退回</el-button>
          <el-button type="danger" plain :loading="acting" @click="submitAction('TERMINATE')">终止</el-button>
        </div>
      </div>
    </div>

    <div v-else class="empty-state">正在加载案件详情...</div>
  </section>
</template>
