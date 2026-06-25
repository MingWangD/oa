<script setup lang="ts">
import type {
  AdminUser,
  FileUploadResponse,
  FormVersionDesign,
  JudicialFormDefinition,
} from '../../api/judicial';

interface DynamicFormField {
  key: string;
  label: string;
  type: string;
  group: string;
  required: boolean;
  readonly: boolean;
  options: string[];
}

interface FieldGroup {
  title: string;
  fields: DynamicFormField[];
}

interface FormRequirementRow {
  label: string;
  value: string;
}

const props = defineProps<{
  formPreview: FormVersionDesign | null;
  currentForm: JudicialFormDefinition | null;
  caseType: string | null | undefined;
  fieldGroups: FieldGroup[];
  /** formData 对象由父组件维护响应式状态，字段值在此处原地修改 */
  formData: Record<string, unknown>;
  formRequirementRows: FormRequirementRow[];
  canHandle: boolean;
  uploadedFiles: FileUploadResponse[];
  userOptions: AdminUser[];
  uploadNodeFile: (options: { file: File; onSuccess?: (response: unknown) => void; onError?: (error: Error) => void }) => Promise<void>;
}>();

function getCandidateUsers(fieldKey: string): AdminUser[] {
  let requiredRole = '';
  if (fieldKey === 'departmentHeadId') requiredRole = 'DEPT_LEADER';
  else if (fieldKey === 'projectLeaderId') requiredRole = 'PROJECT_LEADER';
  else if (fieldKey === 'projectAssistantId') requiredRole = 'PROJECT_ASSISTANT';
  if (!requiredRole) return props.userOptions;
  return props.userOptions.filter(u =>
    u.roles && u.roles.some((r: any) => r.roleCode === requiredRole)
  );
}
</script>

<template>
  <section class="process-section">
    <div class="section-title">
      <h2>动态表单</h2>
      <span>{{ formPreview?.formName || currentForm?.name || caseType || '待匹配表单' }}</span>
    </div>
    <div class="form-board">
      <article v-for="row in formRequirementRows" :key="row.label">
        <span>{{ row.label }}</span>
        <strong>{{ row.value }}</strong>
      </article>
    </div>
    <el-empty v-if="fieldGroups.length === 0" description="暂未匹配到已发布表单字段" />
    <el-form v-else class="dynamic-form" label-position="top" :model="formData" :disabled="!canHandle">
      <section v-for="group in fieldGroups" :key="group.title" class="dynamic-form-group">
        <h3>{{ group.title }}</h3>
        <div class="dynamic-field-grid">
          <el-form-item
            v-for="field in group.fields"
            :key="field.key"
            :required="field.required && !field.readonly"
            :class="{
              'highlight-required': field.required && !field.readonly,
              'field-is-readonly': field.readonly
            }"
          >
            <template #label>
              <span class="field-label" :class="{ 'field-label--required': field.required && !field.readonly }">
                <span>{{ field.label }}</span>
                <span v-if="field.required && !field.readonly" class="required-star" aria-hidden="true">*</span>
              </span>
            </template>
            <el-input
              v-if="field.type === 'textarea'"
              v-model="formData[field.key]"
              :readonly="field.readonly"
              type="textarea"
              :rows="3"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="formData[field.key]"
              :disabled="field.readonly || !canHandle"
              clearable
            >
              <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
            </el-select>
            <el-switch
              v-else-if="field.type === 'boolean'"
              v-model="formData[field.key]"
              :disabled="field.readonly || !canHandle"
              active-text="是"
              inactive-text="否"
            />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="formData[field.key]"
              :readonly="field.readonly"
              type="date"
              value-format="YYYY-MM-DD"
            />
            <el-date-picker
              v-else-if="field.type === 'datetime'"
              v-model="formData[field.key]"
              :readonly="field.readonly"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="formData[field.key]"
              :disabled="field.readonly || !canHandle"
              :controls="false"
              class="number-field"
            />
            <el-select
              v-else-if="field.type === 'user'"
              v-model="formData[field.key]"
              :disabled="field.readonly || !canHandle"
              filterable
              clearable
              placeholder="请选择办理人"
            >
              <el-option
                v-for="user in getCandidateUsers(field.key)"
                :key="user.id"
                :label="user.realName || user.username"
                :value="user.id"
              />
            </el-select>
            <el-input
              v-else
              v-model="formData[field.key]"
              :readonly="field.readonly"
            />
          </el-form-item>
        </div>
      </section>
    </el-form>
    <div class="attachment-panel">
      <div class="section-title attachment-title">
        <h2>节点附件</h2>
        <span>上传后随本次办理动作归档</span>
      </div>
      <el-upload drag multiple :disabled="!canHandle" :http-request="uploadNodeFile" :show-file-list="true">
        <div class="upload-text">拖拽文件到此处，或点击上传输入/输出文件</div>
      </el-upload>
      <div v-if="uploadedFiles.length" class="uploaded-file-list">
        <span v-for="file in uploadedFiles" :key="file.fileId">
          {{ file.originalName }} · v{{ file.versionNo }}{{ file.duplicate ? ' · 重复文件' : '' }}
        </span>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* Base section styles (duplicated from parent — scoped styles don't cross boundaries) */
.process-section {
  border: 1px solid #dce3ee;
  background: #ffffff;
  padding: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title h2 {
  margin: 0;
  color: #243244;
  font-size: 16px;
}

.section-title span,
.form-board span {
  color: #728096;
  font-size: 13px;
}

/* Form-specific styles */
.form-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.form-board article {
  min-height: 74px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  background: #fafafa;
}

.form-board strong {
  display: block;
  margin-top: 6px;
  color: #1f2937;
  font-size: 15px;
}

.dynamic-form {
  margin-top: 14px;
}

.dynamic-form-group + .dynamic-form-group {
  margin-top: 16px;
}

.dynamic-form-group h3 {
  margin: 0 0 10px;
  color: #334155;
  font-size: 15px;
}

.dynamic-field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.dynamic-field-grid :deep(.el-select),
.dynamic-field-grid :deep(.el-date-editor),
.number-field {
  width: 100%;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #5f6877;
  font-weight: 500;
  line-height: 1.3;
}

.field-label--required {
  color: #374151;
}

.required-star {
  color: #d92d20;
  font-size: 16px;
  font-weight: 700;
  line-height: 1;
}

.attachment-panel {
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e2e8f0;
}

.attachment-title {
  margin-bottom: 10px;
}

.upload-text {
  color: #526174;
  font-size: 14px;
}

.uploaded-file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.uploaded-file-list span {
  padding: 5px 8px;
  border: 1px solid #d8e0ec;
  background: #f8fafc;
  color: #475569;
  font-size: 12px;
}

.highlight-required :deep(.el-input__inner),
.highlight-required :deep(.el-input__wrapper),
.highlight-required :deep(.el-textarea__inner),
.highlight-required :deep(.el-textarea__wrapper),
.highlight-required :deep(.el-select__wrapper) {
  background-color: #fff2f0 !important;
  box-shadow: 0 0 0 1px #ffccc7 inset !important;
}

/* Read-only and disabled field styling */
.field-is-readonly :deep(.el-input__wrapper),
.field-is-readonly :deep(.el-textarea__wrapper),
.field-is-readonly :deep(.el-textarea__inner),
.field-is-readonly :deep(.el-select__wrapper),
.field-is-readonly :deep(.el-input-number),
.field-is-readonly :deep(.el-date-editor) {
  background-color: #f5f7fa !important;
  box-shadow: 0 0 0 1px #e4e7ed inset !important;
  cursor: not-allowed !important;
}

.field-is-readonly :deep(.el-input__inner),
.field-is-readonly :deep(.el-textarea__inner),
.field-is-readonly :deep(.el-select__wrapper *),
.field-is-readonly :deep(.el-input-number *),
.field-is-readonly :deep(.el-date-editor *) {
  cursor: not-allowed !important;
  color: #a8abb2 !important;
}

.field-is-readonly :deep(.el-switch) {
  cursor: not-allowed !important;
}

.field-is-readonly :deep(.el-switch__core),
.field-is-readonly :deep(.el-switch__label) {
  cursor: not-allowed !important;
  opacity: 0.6;
}

/* General disabled styling for dynamic form fields */
.dynamic-form :deep(.el-input.is-disabled .el-input__wrapper),
.dynamic-form :deep(.el-textarea.is-disabled .el-textarea__inner),
.dynamic-form :deep(.el-select.is-disabled .el-select__wrapper),
.dynamic-form :deep(.el-switch.is-disabled),
.dynamic-form :deep(.el-switch.is-disabled *),
.dynamic-form :deep(.el-input-number.is-disabled),
.dynamic-form :deep(.el-input-number.is-disabled *),
.dynamic-form :deep(.el-date-editor.is-disabled),
.dynamic-form :deep(.el-date-editor.is-disabled *) {
  cursor: not-allowed !important;
}

@media (max-width: 900px) {
  .form-board,
  .dynamic-field-grid {
    grid-template-columns: 1fr;
  }
}
</style>
