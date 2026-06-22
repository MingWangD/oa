<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';

import {
  fetchJudicialCatalog,
  fetchJudicialWorkflowVerification,
  fetchPlatformModules,
  fetchReconstructionPlan,
  importJudicialCatalog,
  type JudicialCatalog,
  type JudicialConfigImportResult,
  type JudicialWorkflowVerificationReport,
  type OaModule,
  type ReconstructionPhase
} from '../api/judicial';

const loading = ref(false);
const errorMessage = ref('');
const modules = ref<OaModule[]>([]);
const phases = ref<ReconstructionPhase[]>([]);
const judicialCatalog = ref<JudicialCatalog | null>(null);
const workflowVerification = ref<JudicialWorkflowVerificationReport | null>(null);
const importLoading = ref(false);
const importResult = ref<JudicialConfigImportResult | null>(null);

const priorityModules = computed(() => modules.value.filter((item) => item.priority === 'P0'));
const workflowPreview = computed(() => judicialCatalog.value?.workflows.slice(0, 8) ?? []);
const activePhase = computed(() => phases.value.find((item) => item.status === 'in_progress') ?? null);
const verificationRows = computed(() => workflowVerification.value?.workflows ?? []);
const failedVerificationRows = computed(() => verificationRows.value.filter((item) => !item.passed));
const verificationPercent = computed(() => {
  const report = workflowVerification.value;
  if (!report || report.expectedWorkflowCount === 0) {
    return 0;
  }
  return Math.round((report.passedWorkflowCount / report.expectedWorkflowCount) * 100);
});

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

async function loadData(): Promise<void> {
  loading.value = true;
  errorMessage.value = '';
  try {
    const [moduleData, phaseData, catalogData, verificationData] = await Promise.all([
      fetchPlatformModules(),
      fetchReconstructionPlan(),
      fetchJudicialCatalog(),
      fetchJudicialWorkflowVerification()
    ]);
    modules.value = moduleData;
    phases.value = phaseData;
    judicialCatalog.value = catalogData;
    workflowVerification.value = verificationData;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载平台总览失败';
  } finally {
    loading.value = false;
  }
}

async function handleImportCatalog(): Promise<void> {
  importLoading.value = true;
  errorMessage.value = '';
  try {
    importResult.value = await importJudicialCatalog(false);
    await loadData();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '导入司法鉴定配置失败';
  } finally {
    importLoading.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <section class="page-block">
    <div class="overview-strip">
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">活跃案件</p>
        <p class="overview-value">{{ workflowVerification?.passedWorkflowCount ?? 0 }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">流程总量</p>
        <p class="overview-value">{{ judicialCatalog?.workflowCount ?? 0 }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">表单定义</p>
        <p class="overview-value">{{ judicialCatalog?.formCount ?? 0 }}</p>
      </el-card>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">业务模块中心</h3>
      </div>
      <el-button type="primary" :loading="loading" @click="loadData">刷新</el-button>
    </div>

    <div v-if="errorMessage" class="state-banner is-error">{{ errorMessage }}</div>

    <div class="module-grid">
      <article v-for="item in priorityModules" :key="item.code" class="module-card">
        <div class="module-card-head">
          <h4>{{ item.name }}</h4>
          <el-tag effect="plain">{{ statusText(item.implementationStatus) }}</el-tag>
        </div>
        <p>{{ item.scope }}</p>
        <div class="tag-row">
          <el-tag v-for="capability in item.requiredCapabilities" :key="capability" size="small" effect="plain">
            {{ capability }}
          </el-tag>
        </div>
      </article>
    </div>
  </section>
</template>
