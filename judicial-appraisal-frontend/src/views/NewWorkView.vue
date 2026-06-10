<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

import { createCaseDraft } from '../api/judicial';

const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);
const form = reactive({
  caseTitle: '',
  caseType: '',
  entrustOrgName: '',
  acceptDeptId: undefined as number | undefined
});

const rules: FormRules<typeof form> = {
  caseTitle: [{ required: true, message: '请输入案件名称', trigger: 'blur' }]
};

async function submit(): Promise<void> {
  const isValid = await formRef.value?.validate().catch(() => false);
  if (!isValid) {
    return;
  }

  submitting.value = true;
  try {
    const created = await createCaseDraft({
      caseTitle: form.caseTitle.trim(),
      caseType: form.caseType.trim() || undefined,
      entrustOrgName: form.entrustOrgName.trim() || undefined,
      acceptDeptId: form.acceptDeptId ?? null
    });
    ElMessage.success('案件草稿已创建');
    await router.push(`/case/${created.id}`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '创建案件草稿失败');
  } finally {
    submitting.value = false;
  }
}

function resetForm(): void {
  formRef.value?.resetFields();
}
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">新建工作</h3>
        <p class="panel-subtitle">创建案件草稿，保存后进入案件详情继续查看和办理。</p>
      </div>
    </div>

    <div class="form-page-body">
      <el-form ref="formRef" class="detail-form" label-position="top" :model="form" :rules="rules" @submit.prevent="submit">
        <div class="workflow-form-grid">
          <el-form-item class="workflow-form-span-2" label="案件名称" prop="caseTitle">
            <el-input v-model="form.caseTitle" placeholder="请输入案件名称" clearable />
          </el-form-item>

          <el-form-item label="案件类型" prop="caseType">
            <el-input v-model="form.caseType" placeholder="请输入案件类型" clearable />
          </el-form-item>

          <el-form-item label="受理部门 ID" prop="acceptDeptId">
            <el-input-number v-model="form.acceptDeptId" :min="1" :step="1" controls-position="right" />
          </el-form-item>

          <el-form-item class="workflow-form-span-2" label="委托机构名称" prop="entrustOrgName">
            <el-input v-model="form.entrustOrgName" placeholder="请输入委托机构名称" clearable />
          </el-form-item>
        </div>

        <div class="query-actions">
          <el-button type="primary" :loading="submitting" @click="submit">创建草稿</el-button>
          <el-button @click="resetForm">重置</el-button>
        </div>
      </el-form>
    </div>
  </section>
</template>
