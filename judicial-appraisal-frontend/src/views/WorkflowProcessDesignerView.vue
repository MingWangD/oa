<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  fetchWorkflowDesigns,
  fetchWorkflowDraft,
  fetchWorkflowVersions,
  publishWorkflow,
  restoreWorkflowVersion,
  saveWorkflowDraft,
  type WorkflowDefinitionDesign,
  type WorkflowNodeDesign,
  type WorkflowTransitionDesign,
  type WorkflowVersionDesign
} from '../api/judicial';

const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const workflows = ref<WorkflowDefinitionDesign[]>([]);
const versions = ref<WorkflowVersionDesign[]>([]);
const preview = ref<WorkflowDefinitionDesign | null>(null);

const designer = reactive({
  wfCode: 'JUDICIAL_MAIN',
  wfName: '司法鉴定主流程',
  wfType: 'main',
  formCode: 'received-entrust',
  remark: '动态流程平台草稿',
  definitionJson: '{\"timeoutPolicy\":\"business-day\",\"archiveRequired\":true}',
  nodesJson:
    '[{\"nodeCode\":\"START\",\"nodeName\":\"开始\",\"nodeType\":\"start\",\"taskType\":\"single\",\"sortNo\":10,\"enabled\":1},{\"nodeCode\":\"ACCEPT_REVIEW\",\"nodeName\":\"受理审查\",\"nodeType\":\"task\",\"taskType\":\"single\",\"handlerRoleRule\":\"收案员\",\"sortNo\":20,\"enabled\":1},{\"nodeCode\":\"END\",\"nodeName\":\"结束\",\"nodeType\":\"end\",\"taskType\":\"single\",\"sortNo\":999,\"enabled\":1}]',
  transitionsJson:
    '[{\"fromNodeCode\":\"START\",\"toNodeCode\":\"ACCEPT_REVIEW\",\"actionCode\":\"SUBMIT\",\"actionName\":\"提交\",\"sortNo\":10,\"enabled\":1},{\"fromNodeCode\":\"ACCEPT_REVIEW\",\"toNodeCode\":\"END\",\"actionCode\":\"APPROVE\",\"actionName\":\"通过\",\"sortNo\":20,\"enabled\":1}]'
});

function parseJsonArray<T>(value: string, label: string): T[] {
  try {
    const parsed = JSON.parse(value);
    if (!Array.isArray(parsed)) {
      throw new Error(`${label} 必须是数组 JSON`);
    }
    return parsed as T[];
  } catch (error) {
    throw new Error(error instanceof Error ? error.message : `${label} JSON 格式错误`);
  }
}

function buildPayload() {
  return {
    wfCode: designer.wfCode,
    wfName: designer.wfName,
    wfType: designer.wfType,
    formCode: designer.formCode,
    remark: designer.remark,
    definitionJson: designer.definitionJson,
    nodes: parseJsonArray<WorkflowNodeDesign>(designer.nodesJson, '节点'),
    transitions: parseJsonArray<WorkflowTransitionDesign>(designer.transitionsJson, '流转')
  };
}

function fillFromDefinition(definition: WorkflowDefinitionDesign): void {
  designer.wfCode = definition.wfCode;
  designer.wfName = definition.wfName;
  designer.wfType = definition.wfType;
  designer.formCode = definition.formCode || '';
  designer.remark = definition.remark || '';
  designer.definitionJson = definition.definitionJson || '{}';
  designer.nodesJson = JSON.stringify(definition.nodes || [], null, 2);
  designer.transitionsJson = JSON.stringify(definition.transitions || [], null, 2);
  preview.value = definition;
}

async function loadWorkflows(): Promise<void> {
  loading.value = true;
  try {
    workflows.value = await fetchWorkflowDesigns();
  } finally {
    loading.value = false;
  }
}

async function loadVersions(): Promise<void> {
  if (!designer.wfCode) {
    return;
  }
  versions.value = await fetchWorkflowVersions(designer.wfCode).catch(() => []);
}

async function selectWorkflow(row: WorkflowDefinitionDesign): Promise<void> {
  designer.wfCode = row.wfCode;
  try {
    fillFromDefinition(await fetchWorkflowDraft(row.wfCode));
  } catch {
    fillFromDefinition(row);
    ElMessage.info('该流程暂无草稿，当前展示最新版本，可恢复历史版本后编辑');
  }
  await loadVersions();
}

async function saveDraft(): Promise<void> {
  saving.value = true;
  try {
    const saved = await saveWorkflowDraft(buildPayload());
    fillFromDefinition(saved);
    await loadWorkflows();
    await loadVersions();
    ElMessage.success('流程草稿已保存');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function publishDraft(): Promise<void> {
  publishing.value = true;
  try {
    const published = await publishWorkflow(designer.wfCode);
    fillFromDefinition(published);
    await loadWorkflows();
    await loadVersions();
    ElMessage.success(`流程已发布为 v${published.versionNo}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败');
  } finally {
    publishing.value = false;
  }
}

async function restore(row: WorkflowVersionDesign): Promise<void> {
  await ElMessageBox.confirm(`确认将 ${row.wfName} v${row.versionNo} 恢复为草稿？`, '恢复版本', { type: 'warning' });
  const draft = await restoreWorkflowVersion(row.wfCode, row.versionNo);
  fillFromDefinition(draft);
  await loadVersions();
  ElMessage.success('已恢复为草稿');
}

function pretty(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

onMounted(async () => {
  await loadWorkflows();
  await loadVersions();
});
</script>

<template>
  <div class="designer-page">
    <div class="designer-header">
      <div>
        <h2>动态流程设计</h2>
        <p>维护开始、办理、审批、并行、汇聚、子流程、结束节点，以及办理人、条件、退回和超时规则。</p>
      </div>
      <div class="designer-actions">
        <el-button :loading="saving" type="primary" @click="saveDraft">保存草稿</el-button>
        <el-button :loading="publishing" type="success" @click="publishDraft">发布版本</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="7">
        <el-card shadow="never">
          <template #header>流程定义</template>
          <el-table v-loading="loading" :data="workflows" height="360" stripe @row-click="selectWorkflow">
            <el-table-column prop="wfCode" label="编码" min-width="150" />
            <el-table-column prop="wfName" label="名称" min-width="170" />
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="publishStatus" label="状态" width="90" />
          </el-table>
        </el-card>

        <el-card class="designer-card" shadow="never">
          <template #header>版本记录</template>
          <el-table :data="versions" height="300" stripe>
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="publishStatus" label="状态" width="90" />
            <el-table-column prop="publishedTime" label="发布时间" min-width="160" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="row.publishStatus === 'published'" link type="primary" @click="restore(row)">恢复</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="17">
        <el-card shadow="never">
          <el-form class="designer-form" label-position="top">
            <div class="designer-grid">
              <el-form-item label="流程编码">
                <el-input v-model="designer.wfCode" />
              </el-form-item>
              <el-form-item label="流程名称">
                <el-input v-model="designer.wfName" />
              </el-form-item>
              <el-form-item label="流程类型">
                <el-select v-model="designer.wfType">
                  <el-option label="主流程" value="main" />
                  <el-option label="子流程" value="subflow" />
                  <el-option label="独立流程" value="direct" />
                </el-select>
              </el-form-item>
              <el-form-item label="关联表单编码">
                <el-input v-model="designer.formCode" />
              </el-form-item>
              <el-form-item class="span-2" label="备注">
                <el-input v-model="designer.remark" />
              </el-form-item>
            </div>

            <el-tabs>
              <el-tab-pane label="全局配置">
                <el-input v-model="designer.definitionJson" type="textarea" :rows="10" />
              </el-tab-pane>
              <el-tab-pane label="节点 JSON">
                <el-alert
                  class="designer-alert"
                  type="info"
                  show-icon
                  :closable="false"
                  title="节点必须包含且只能包含一个 start 节点，并至少包含一个 end 节点。"
                />
                <el-input v-model="designer.nodesJson" type="textarea" :rows="18" />
              </el-tab-pane>
              <el-tab-pane label="流转 JSON">
                <el-alert
                  class="designer-alert"
                  type="info"
                  show-icon
                  :closable="false"
                  title="流转的 fromNodeCode / toNodeCode 必须引用已存在节点，可通过 conditionExpression 保存条件表达式。"
                />
                <el-input v-model="designer.transitionsJson" type="textarea" :rows="18" />
              </el-tab-pane>
              <el-tab-pane label="预览">
                <pre class="json-preview">{{ pretty(preview || designer) }}</pre>
              </el-tab-pane>
            </el-tabs>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.designer-header h2 {
  margin: 0 0 6px;
}

.designer-header p {
  margin: 0;
  color: #64748b;
}

.designer-actions {
  display: flex;
  gap: 8px;
  white-space: nowrap;
}

.designer-card {
  margin-top: 16px;
}

.designer-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.span-2 {
  grid-column: span 2;
}

.designer-alert {
  margin-bottom: 12px;
}

.json-preview {
  min-height: 420px;
  max-height: 620px;
  overflow: auto;
  padding: 16px;
  border-radius: 8px;
  background: #0f172a;
  color: #dbeafe;
  white-space: pre-wrap;
}
</style>
