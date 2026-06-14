<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Document, Download, EditPen, Search, Upload, View } from '@element-plus/icons-vue';

import {
  fetchJudicialCatalog,
  fetchFormDesigns,
  fetchFormDraft,
  fetchFormPreview,
  fetchFormVersions,
  publishForm,
  restoreFormVersion,
  saveFormDraft,
  type FormDesignPayload,
  type FormDefinitionDesign,
  type FormVersionDesign
} from '../api/judicial';

const loading = ref(false);
const saving = ref(false);
const publishing = ref(false);
const forms = ref<FormDefinitionDesign[]>([]);
const versions = ref<FormVersionDesign[]>([]);
const preview = ref<FormVersionDesign | null>(null);
const keyword = ref('');
const editorVisible = ref(false);
const selectedFormCode = ref('');
const catalogOrder = ref<string[]>([]);
const sortMode = ref<'catalog' | 'createdDesc'>('catalog');
const previewDialogVisible = ref(false);
const historyDialogVisible = ref(false);
const importInputRef = ref<HTMLInputElement | null>(null);

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

const visibleForms = computed(() => {
  const query = keyword.value.trim().toLowerCase();
  return forms.value
    .filter((item) => {
      if (!query) {
        return true;
      }
      return [item.formName, item.formCode, item.category || ''].some((value) => value.toLowerCase().includes(query));
    })
    .sort((left, right) => {
      if (sortMode.value === 'createdDesc') {
        return (right.createdTime || '').localeCompare(left.createdTime || '');
      }
      const leftIndex = catalogOrder.value.indexOf(left.formCode);
      const rightIndex = catalogOrder.value.indexOf(right.formCode);
      if (leftIndex !== -1 || rightIndex !== -1) {
        return (leftIndex === -1 ? 999 : leftIndex) - (rightIndex === -1 ? 999 : rightIndex);
      }
      return left.formName.localeCompare(right.formName, 'zh-Hans-CN');
    });
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

function parseJson<T>(value: string | null | undefined, fallback: T): T {
  if (!value) {
    return fallback;
  }
  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

const previewFields = computed(() => {
  const source = preview.value?.fieldSchemaJson || form.fieldSchemaJson;
  const fields = parseJson<Array<Record<string, unknown>>>(source, []);
  return fields.map((field, index) => ({
    key: String(field.field || field.code || index),
    label: String(field.label || field.name || field.field || `字段 ${index + 1}`),
    type: String(field.type || 'text'),
    group: String(field.group || '基础信息'),
    required: Boolean(field.required)
  }));
});

const previewFiles = computed(() => ({
  input: parseJson<string[]>(preview.value?.inputFilesJson || form.inputFilesJson, []),
  output: parseJson<string[]>(preview.value?.outputFilesJson || form.outputFilesJson, []),
  versioned: parseJson<string[]>(preview.value?.versionedArtifactsJson || form.versionedArtifactsJson, [])
}));

async function loadForms(): Promise<void> {
  loading.value = true;
  try {
    const catalog = await fetchJudicialCatalog();
    catalogOrder.value = catalog.forms.map((item) => item.code);
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
  selectedFormCode.value = row.formCode;
  editorVisible.value = true;
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

function newForm(): void {
  selectedFormCode.value = '';
  editorVisible.value = true;
  preview.value = null;
  versions.value = [];
  form.formCode = '';
  form.formName = '';
  form.category = 'judicial';
  form.inputFilesJson = '[]';
  form.outputFilesJson = '[]';
  form.versionedArtifactsJson = '[]';
  form.fieldSchemaJson = '[]';
  form.layoutSchemaJson = '{}';
  form.validationSchemaJson = '{}';
  form.permissionSchemaJson = '{}';
  form.linkageSchemaJson = '{}';
  form.calculationSchemaJson = '{}';
  form.attachmentSchemaJson = '{}';
  form.subtableSchemaJson = '[]';
  form.notesJson = '[]';
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

async function previewForm(row: FormDefinitionDesign): Promise<void> {
  selectedFormCode.value = row.formCode;
  form.formCode = row.formCode;
  form.formName = row.formName;
  form.category = row.category || '';
  try {
    fillFromVersion(await fetchFormPreview(row.formCode));
    previewDialogVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '表单预览加载失败');
  }
}

async function openHistory(row: FormDefinitionDesign): Promise<void> {
  await selectForm(row);
  historyDialogVisible.value = true;
}

async function exportForm(row: FormDefinitionDesign): Promise<void> {
  try {
    const version = await fetchFormPreview(row.formCode);
    const payload = JSON.stringify(version, null, 2);
    const blob = new Blob([payload], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${row.formCode || 'form'}.json`;
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  }
}

function triggerImport(): void {
  importInputRef.value?.click();
}

async function importForm(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }
  try {
    const text = await file.text();
    const payload = JSON.parse(text) as Partial<FormVersionDesign & FormDesignPayload>;
    editorVisible.value = true;
    selectedFormCode.value = payload.formCode || '';
    form.formCode = payload.formCode || '';
    form.formName = payload.formName || '';
    form.category = payload.category || 'judicial';
    form.inputFilesJson = payload.inputFilesJson || '[]';
    form.outputFilesJson = payload.outputFilesJson || '[]';
    form.versionedArtifactsJson = payload.versionedArtifactsJson || '[]';
    form.fieldSchemaJson = payload.fieldSchemaJson || '[]';
    form.layoutSchemaJson = payload.layoutSchemaJson || '{}';
    form.validationSchemaJson = payload.validationSchemaJson || '{}';
    form.permissionSchemaJson = payload.permissionSchemaJson || '{}';
    form.linkageSchemaJson = payload.linkageSchemaJson || '{}';
    form.calculationSchemaJson = payload.calculationSchemaJson || '{}';
    form.attachmentSchemaJson = payload.attachmentSchemaJson || '{}';
    form.subtableSchemaJson = payload.subtableSchemaJson || '[]';
    form.notesJson = payload.notesJson || '[]';
    preview.value = null;
    versions.value = form.formCode ? await fetchFormVersions(form.formCode).catch(() => []) : [];
    ElMessage.success('表单 JSON 已载入编辑器，确认后可保存草稿');
  } catch {
    ElMessage.error('导入文件不是有效的表单 JSON');
  } finally {
    input.value = '';
  }
}

function placeholderAction(name: string): void {
  ElMessage.info(`${name}正在按原 OA 行为补齐`);
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
    <div class="form-list-toolbar">
      <div class="breadcrumb">全部 &gt;&gt; 司法鉴定</div>
      <div class="toolbar-row">
        <div class="toolbar-actions">
          <el-button type="primary" @click="newForm">新建表单</el-button>
          <el-button type="danger" @click="placeholderAction('批量删除')">批量删除</el-button>
          <el-button :icon="Upload" @click="triggerImport">导入</el-button>
          <input ref="importInputRef" class="hidden-file-input" type="file" accept="application/json,.json" @change="importForm" />
        </div>
        <div class="toolbar-search">
          <el-input
            v-model="keyword"
            clearable
            :prefix-icon="Search"
            placeholder="请输入表单名称或分类名称"
          />
          <el-button type="primary">查询</el-button>
          <el-button :icon="Download" @click="sortMode = sortMode === 'catalog' ? 'createdDesc' : 'catalog'">
            {{ sortMode === 'catalog' ? '创建时间' : '目录排序' }}
          </el-button>
        </div>
      </div>
    </div>

    <el-table
      v-loading="loading"
      class="legacy-form-table"
      :data="visibleForms"
      border
      stripe
      row-key="formCode"
      @row-dblclick="selectForm"
    >
      <el-table-column type="selection" width="42" />
      <el-table-column label="表单名称" min-width="520">
        <template #default="{ row }">
          <button class="form-name-link" type="button" @click="selectForm(row)">
            <el-icon><Document /></el-icon>
            <span>{{ row.formName }}</span>
          </button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="520" align="center">
        <template #default="{ row }">
          <div class="legacy-actions">
            <el-button link type="primary" :icon="EditPen" @click="selectForm(row)">修改表单</el-button>
            <el-button link type="primary" @click="selectForm(row)">智能设计器</el-button>
            <el-button link type="primary" @click="placeholderAction('移动表单设计器')">移动表单设计器</el-button>
            <el-button link type="primary" :icon="View" @click="previewForm(row)">预览</el-button>
            <el-button link type="primary" :icon="Upload" @click="triggerImport">导入</el-button>
            <el-button link type="primary" :icon="Download" @click="exportForm(row)">导出</el-button>
            <el-button link type="primary" @click="openHistory(row)">历史版本</el-button>
            <el-button link type="danger" @click="placeholderAction('删除')">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="editorVisible" class="designer-header">
      <div>
        <h2>{{ selectedFormCode ? '修改表单' : '新建表单' }}</h2>
        <p>维护字段、布局、校验、权限、联动、计算、附件、子表和版本。发布版本不可变。</p>
      </div>
      <div class="designer-actions">
        <el-button :loading="saving" type="primary" @click="saveDraft">保存草稿</el-button>
        <el-button :loading="publishing" type="success" @click="publishDraft">发布版本</el-button>
      </div>
    </div>

    <el-row v-if="editorVisible" :gutter="16">
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

    <el-dialog v-model="previewDialogVisible" :title="`${form.formName || '表单'} - 预览`" width="760px">
      <div class="form-preview">
        <section class="preview-section">
          <h3>文件要求</h3>
          <div class="file-groups">
            <span>输入：{{ previewFiles.input.join('、') || '无' }}</span>
            <span>输出：{{ previewFiles.output.join('、') || '无' }}</span>
            <span>版本产物：{{ previewFiles.versioned.join('、') || '无' }}</span>
          </div>
        </section>

        <section class="preview-section">
          <h3>字段预览</h3>
          <div class="preview-grid">
            <label v-for="field in previewFields" :key="field.key" class="preview-field">
              <span>
                {{ field.label }}
                <em v-if="field.required">*</em>
              </span>
              <input :placeholder="field.type" disabled />
            </label>
          </div>
        </section>
      </div>
    </el-dialog>

    <el-dialog v-model="historyDialogVisible" :title="`${form.formName || '表单'} - 历史版本`" width="820px">
      <el-table :data="versions" border stripe max-height="420">
        <el-table-column prop="versionNo" label="版本" width="80" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="publishedTime" label="发布时间" min-width="180" />
        <el-table-column prop="createdTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button v-if="row.status === 'published'" link type="primary" @click="restore(row)">恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-list-toolbar {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.breadcrumb {
  color: #34495e;
  font-size: 14px;
}

.toolbar-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar-actions,
.toolbar-search {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-search {
  margin-left: auto;
}

.toolbar-search :deep(.el-input) {
  width: 250px;
}

.hidden-file-input {
  display: none;
}

.legacy-form-table {
  border-radius: 4px;
  overflow: hidden;
}

.legacy-form-table :deep(.el-table__header th) {
  height: 38px;
  background: #eef4fa;
  color: #1f2937;
  font-weight: 700;
}

.legacy-form-table :deep(.el-table__row) {
  height: 37px;
}

.legacy-form-table :deep(.el-table__cell) {
  padding: 5px 0;
}

.form-name-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: #1f2937;
  font-size: 14px;
  text-align: left;
  cursor: pointer;
}

.form-name-link:hover {
  color: #0b73e8;
}

.form-name-link .el-icon {
  color: #7d8da3;
  font-size: 15px;
}

.legacy-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0 2px;
}

.legacy-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0 2px;
  font-size: 13px;
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

.form-preview {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.preview-section {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #ffffff;
}

.preview-section h3 {
  margin: 0;
  padding: 10px 14px;
  border-bottom: 1px solid #e4e7ed;
  background: #f5f7fa;
  color: #303642;
  font-size: 15px;
}

.file-groups {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 14px;
  color: #4f5b66;
  font-size: 13px;
}

.preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
  padding: 14px;
}

.preview-field {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.preview-field span {
  overflow: hidden;
  color: #5f6b7a;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-field em {
  color: #d93026;
  font-style: normal;
}

.preview-field input {
  min-width: 0;
  height: 30px;
  padding: 0 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f8fafc;
  color: #9aa5b1;
}

@media (max-width: 980px) {
  .toolbar-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-search {
    flex-wrap: wrap;
    width: 100%;
    margin-left: 0;
  }

  .toolbar-search :deep(.el-input) {
    width: min(100%, 320px);
  }

  .file-groups,
  .preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
