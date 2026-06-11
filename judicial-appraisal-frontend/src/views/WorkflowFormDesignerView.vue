<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  fetchFormDesigns,
  fetchFormDraft,
  fetchFormVersions,
  publishForm,
  restoreFormVersion,
  saveFormDraft,
  type FormDefinitionDesign,
  type FormVersionDesign
} from '../api/judicial';

const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const forms = ref<FormDefinitionDesign[]>([]);
const versions = ref<FormVersionDesign[]>([]);
const preview = ref<FormVersionDesign | null>(null);

const form = reactive({
  formCode: 'received-entrust',
  formName: '收到委托书',
  category: 'judicial',
  inputFilesJson: '[\"委托书\", \"初始材料\"]',
  outputFilesJson: '[\"收案登记信息\"]',
  versionedArtifactsJson: '[]',
  fieldSchemaJson:
    '[{\"field\":\"caseTitle\",\"label\":\"案件名称\",\"type\":\"input\",\"required\":true},{\"field\":\"entrustOrgName\",\"label\":\"委托机构\",\"type\":\"input\"}]',
  layoutSchemaJson: '{\"columns\":2,\"sections\":[{\"title\":\"基础信息\",\"fields\":[\"caseTitle\",\"entrustOrgName\"]}]}',
  validationSchemaJson: '{\"caseTitle\":[{\"required\":true,\"message\":\"案件名称不能为空\"}]}',
  permissionSchemaJson: '{\"default\":\"editable\",\"readonlyRoles\":[],\"hiddenFields\":{}}',
  linkageSchemaJson: '{}',
  calculationSchemaJson: '{}',
  attachmentSchemaJson: '{\"enabled\":true,\"requiredCategories\":[\"委托材料\"]}',
  subtableSchemaJson: '[]',
  notesJson: '[\"发布后的版本不可变，历史实例继续绑定原版本\"]'
});

function asPayload() {
  return {
    ...form
  };
}

function fillFromVersion(version: FormVersionDesign): void {
  form.formCode = version.formCode;
  form.formName = version.formName;
  form.inputFilesJson = version.inputFilesJson || '[]';
  form.outputFilesJson = version.outputFilesJson || '[]';
  form.versionedArtifactsJson = version.versionedArtifactsJson || '[]';
  form.fieldSchemaJson = version.fieldSchemaJson || '[]';
  form.layoutSchemaJson = version.layoutSchemaJson || '{}';
  form.validationSchemaJson = version.validationSchemaJson || '{}';
  form.permissionSchemaJson = version.permissionSchemaJson || '{}';
  form.linkageSchemaJson = version.linkageSchemaJson || '{}';
  form.calculationSchemaJson = version.calculationSchemaJson || '{}';
  form.attachmentSchemaJson = version.attachmentSchemaJson || '{}';
  form.subtableSchemaJson = version.subtableSchemaJson || '[]';
  form.notesJson = version.notesJson || '[]';
  preview.value = version;
}

async function loadForms(): Promise<void> {
  loading.value = true;
  try {
    forms.value = await fetchFormDesigns();
  } finally {
    loading.value = false;
  }
}

async function loadVersions(): Promise<void> {
  if (!form.formCode) {
    return;
  }
  versions.value = await fetchFormVersions(form.formCode).catch(() => []);
}

async function selectForm(row: FormDefinitionDesign): Promise<void> {
  form.formCode = row.formCode;
  form.formName = row.formName;
  form.category = row.category || '';
  try {
    fillFromVersion(await fetchFormDraft(row.formCode));
  } catch {
    ElMessage.info('该表单暂无草稿，可直接编辑后保存');
  }
  await loadVersions();
}

async function saveDraft(): Promise<void> {
  saving.value = true;
  try {
    const saved = await saveFormDraft(asPayload());
    fillFromVersion(saved);
    await loadForms();
    await loadVersions();
    ElMessage.success('表单草稿已保存');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

async function publishDraft(): Promise<void> {
  publishing.value = true;
  try {
    const published = await publishForm(form.formCode);
    preview.value = published;
    await loadForms();
    await loadVersions();
    ElMessage.success(`表单已发布为 v${published.versionNo}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败');
  } finally {
    publishing.value = false;
  }
}

async function restore(row: FormVersionDesign): Promise<void> {
  await ElMessageBox.confirm(`确认将 v${row.versionNo} 恢复为草稿？`, '恢复版本', { type: 'warning' });
  const draft = await restoreFormVersion(row.formCode, row.versionNo);
  fillFromVersion(draft);
  await loadVersions();
  ElMessage.success('已恢复为草稿');
}

function prettyJson(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

onMounted(async () => {
  await loadForms();
  await loadVersions();
});
</script>

<template>
  <div class="designer-page">
    <div class="designer-header">
      <div>
        <h2>动态表单设计</h2>
        <p>维护字段、布局、校验、权限、联动、计算、附件、子表和版本。发布版本不可变。</p>
      </div>
      <div class="designer-actions">
        <el-button :loading="saving" type="primary" @click="saveDraft">保存草稿</el-button>
        <el-button :loading="publishing" type="success" @click="publishDraft">发布版本</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="7">
        <el-card shadow="never">
          <template #header>表单定义</template>
          <el-table v-loading="loading" :data="forms" height="360" stripe @row-click="selectForm">
            <el-table-column prop="formCode" label="编码" min-width="130" />
            <el-table-column prop="formName" label="名称" min-width="160" />
            <el-table-column prop="currentPublishedVersion" label="版本" width="70" />
          </el-table>
        </el-card>

        <el-card class="designer-card" shadow="never">
          <template #header>版本记录</template>
          <el-table :data="versions" height="300" stripe>
            <el-table-column prop="versionNo" label="版本" width="70" />
            <el-table-column prop="status" label="状态" width="90" />
            <el-table-column prop="publishedTime" label="发布时间" min-width="160" />
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button v-if="row.status === 'published'" link type="primary" @click="restore(row)">恢复</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="17">
        <el-card shadow="never">
          <el-form class="designer-form" label-position="top">
            <div class="designer-grid">
              <el-form-item label="表单编码">
                <el-input v-model="form.formCode" />
              </el-form-item>
              <el-form-item label="表单名称">
                <el-input v-model="form.formName" />
              </el-form-item>
              <el-form-item label="分类">
                <el-input v-model="form.category" />
              </el-form-item>
            </div>

            <el-tabs>
              <el-tab-pane label="文件与产物">
                <el-form-item label="输入文件 JSON">
                  <el-input v-model="form.inputFilesJson" type="textarea" :rows="4" />
                </el-form-item>
                <el-form-item label="输出文件 JSON">
                  <el-input v-model="form.outputFilesJson" type="textarea" :rows="4" />
                </el-form-item>
                <el-form-item label="版本化产物 JSON">
                  <el-input v-model="form.versionedArtifactsJson" type="textarea" :rows="4" />
                </el-form-item>
              </el-tab-pane>

              <el-tab-pane label="字段布局">
                <el-form-item label="字段定义 JSON">
                  <el-input v-model="form.fieldSchemaJson" type="textarea" :rows="8" />
                </el-form-item>
                <el-form-item label="布局定义 JSON">
                  <el-input v-model="form.layoutSchemaJson" type="textarea" :rows="6" />
                </el-form-item>
              </el-tab-pane>

              <el-tab-pane label="规则">
                <el-form-item label="校验规则 JSON">
                  <el-input v-model="form.validationSchemaJson" type="textarea" :rows="5" />
                </el-form-item>
                <el-form-item label="字段权限 JSON">
                  <el-input v-model="form.permissionSchemaJson" type="textarea" :rows="5" />
                </el-form-item>
                <el-form-item label="联动规则 JSON">
                  <el-input v-model="form.linkageSchemaJson" type="textarea" :rows="5" />
                </el-form-item>
                <el-form-item label="计算规则 JSON">
                  <el-input v-model="form.calculationSchemaJson" type="textarea" :rows="5" />
                </el-form-item>
              </el-tab-pane>

              <el-tab-pane label="附件子表">
                <el-form-item label="附件规则 JSON">
                  <el-input v-model="form.attachmentSchemaJson" type="textarea" :rows="5" />
                </el-form-item>
                <el-form-item label="子表规则 JSON">
                  <el-input v-model="form.subtableSchemaJson" type="textarea" :rows="5" />
                </el-form-item>
                <el-form-item label="注意事项 JSON">
                  <el-input v-model="form.notesJson" type="textarea" :rows="5" />
                </el-form-item>
              </el-tab-pane>

              <el-tab-pane label="预览">
                <pre class="json-preview">{{ prettyJson(JSON.stringify(preview || form)) }}</pre>
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
