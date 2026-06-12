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
        <p class="overview-label">完整 OA 模块</p>
        <p class="overview-value">{{ modules.length }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">司法鉴定流程</p>
        <p class="overview-value">{{ judicialCatalog?.workflowCount ?? 0 }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">司法鉴定表单</p>
        <p class="overview-value">{{ judicialCatalog?.formCount ?? 0 }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card is-accent">
        <p class="overview-label">当前阶段</p>
        <p class="overview-value">{{ activePhase?.phase.replace('第', '').replace('阶段', '') ?? '-' }}</p>
      </el-card>
      <el-card shadow="never" class="overview-card">
        <p class="overview-label">流程验收</p>
        <p class="overview-value">{{ workflowVerification?.passedWorkflowCount ?? 0 }}/{{ workflowVerification?.expectedWorkflowCount ?? 0 }}</p>
      </el-card>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">重构总览</h3>
        <p class="panel-subtitle">按最新需求规格说明书推进完整 OA，而不是只做司法鉴定一期。</p>
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

  <section class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">分阶段实施计划</h3>
        <p class="panel-subtitle">先平台底座，再流程/文件/知识，当前集中完成司法鉴定使用手册级验收。</p>
      </div>
    </div>
    <el-timeline class="phase-timeline">
      <el-timeline-item v-for="item in phases" :key="item.phase" :timestamp="item.phase" placement="top">
        <h4 class="timeline-title">{{ item.goal }}</h4>
        <p class="section-note">状态：{{ statusText(item.status) }}</p>
        <ul class="compact-list">
          <li v-for="deliverable in item.deliverables" :key="deliverable">{{ deliverable }}</li>
        </ul>
      </el-timeline-item>
    </el-timeline>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">司法鉴定流程目录</h3>
        <p class="panel-subtitle">按使用手册登记 19 个验收流程和 19 个表单定义，可一键同步到动态表单/流程设计器。</p>
      </div>
      <el-button type="primary" :loading="importLoading" @click="handleImportCatalog">导入平台配置</el-button>
    </div>
    <div v-if="importResult" class="state-banner">
      本次导入：表单 {{ importResult.formsCreated }} 个，跳过 {{ importResult.formsSkipped }} 个；流程
      {{ importResult.workflowsCreated }} 个，跳过 {{ importResult.workflowsSkipped }} 个。
    </div>
    <div class="table-frame">
      <el-table :data="workflowPreview" border stripe>
        <el-table-column prop="name" label="流程名称" min-width="220" />
        <el-table-column prop="entryMode" label="入口类型" width="150" />
        <el-table-column label="办理角色" min-width="260">
          <template #default="scope">{{ scope.row.roles.join('、') }}</template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">第四阶段验收矩阵</h3>
        <p class="panel-subtitle">自动检查发布版本、表单、节点、连线、退回路径、结束路径和子流程目标。</p>
      </div>
      <el-tag :type="failedVerificationRows.length ? 'danger' : 'success'" effect="plain">
        {{ workflowVerification?.passedWorkflowCount ?? 0 }}/{{ workflowVerification?.expectedWorkflowCount ?? 0 }} 通过
      </el-tag>
    </div>
    <el-progress
      :percentage="verificationPercent"
      :status="failedVerificationRows.length ? 'exception' : 'success'"
      :stroke-width="10"
    />
    <div class="table-frame">
      <el-table :data="verificationRows" border stripe>
        <el-table-column prop="name" label="流程名称" min-width="220" />
        <el-table-column prop="publishedVersion" label="发布版本" width="110" />
        <el-table-column label="节点/连线" width="120">
          <template #default="scope">{{ scope.row.nodeCount }}/{{ scope.row.transitionCount }}</template>
        </el-table-column>
        <el-table-column label="子流程" min-width="180">
          <template #default="scope">{{ scope.row.subflowTargets.join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="验收状态" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.passed ? 'success' : 'danger'" effect="plain">
              {{ scope.row.passed ? '通过' : '待修正' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="问题" min-width="220">
          <template #default="scope">{{ scope.row.issues.join('；') || '-' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
