<script setup lang="ts">
import type { WorkflowActionCode } from '../../api/judicial';

interface TransitionOption {
  value: WorkflowActionCode;
  label: string;
  targetNode?: string;
  condition?: string | null;
}

defineProps<{
  canHandle: boolean;
  isDraftCase: boolean;
  transitionOptions: TransitionOption[];
  acting: boolean;
  saving: boolean;
}>();

const emit = defineEmits<{
  'save-form': [];
  'submit-action': [code: WorkflowActionCode];
}>();

const opinion = defineModel<string>('opinion', { required: true });
const selectedTransition = defineModel<WorkflowActionCode>('selectedTransition', { required: true });
</script>

<template>
  <template v-if="canHandle">
    <section class="process-section">
      <div class="section-title">
        <h2>办理意见</h2>
        <span>退回或终止时必须填写明确原因</span>
      </div>
      <el-input
        v-model="opinion"
        :disabled="!canHandle"
        type="textarea"
        :rows="4"
        placeholder="请输入办理意见"
      />
    </section>

    <section class="process-section transition-section">
      <div class="section-title">
        <h2>选择下一步骤</h2>
        <span>请确认流转节点符合预期后再转交</span>
      </div>
      <div class="transition-row">
        <el-select v-model="selectedTransition" :disabled="!canHandle" class="transition-select">
          <el-option
            v-for="option in transitionOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
        <el-button :disabled="!canHandle" :loading="saving" @click="emit('save-form')">保存表单</el-button>
        <el-button
          type="primary"
          :disabled="!canHandle"
          :loading="acting"
          @click="emit('submit-action', selectedTransition)"
        >
          {{ isDraftCase ? '草稿转正' : '提交流转' }}
        </el-button>
        <el-button :disabled="!canHandle" :loading="acting" @click="emit('submit-action', 'RETURN')">退回</el-button>
        <el-button type="danger" plain :disabled="!canHandle" :loading="acting" @click="emit('submit-action', 'TERMINATE')">
          终止
        </el-button>
      </div>
      <div v-if="transitionOptions.length" class="next-step-preview">
        <div v-for="option in transitionOptions" :key="`${option.value}-${option.targetNode}`" class="next-step-item">
          <strong>{{ option.targetNode || option.label }}</strong>
          <span>{{ option.label }}</span>
          <el-tag v-if="option.condition" size="small" type="warning" effect="plain">条件：{{ option.condition }}</el-tag>
        </div>
      </div>
    </section>
  </template>
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

.section-title span {
  color: #728096;
  font-size: 13px;
}

.transition-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.transition-select {
  width: min(100%, 320px);
}

.next-step-preview {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.next-step-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
}

.next-step-item strong {
  color: #1d4ed8;
}

.next-step-item span {
  color: #64748b;
  font-size: 13px;
}
</style>
