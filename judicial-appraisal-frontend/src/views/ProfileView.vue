<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

import { useAuthStore } from '../stores/auth';

const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const rules: FormRules<typeof passwordForm> = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '新密码至少 6 位', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请确认新密码', trigger: 'blur' }]
};

async function submitPassword(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.error('两次输入的新密码不一致');
    return;
  }

  loading.value = true;
  try {
    await authStore.changePassword(passwordForm);
    ElMessage.success('密码已更新');
    formRef.value?.resetFields();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修改密码失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">个人资料</h3>
        <p class="panel-subtitle">查看当前登录用户信息，并维护个人密码。</p>
      </div>
    </div>

    <div class="profile-layout">
      <el-descriptions border :column="2" title="基本信息">
        <el-descriptions-item label="账号">{{ authStore.user?.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ authStore.user?.realName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ authStore.user?.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="岗位">{{ authStore.user?.postName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ authStore.user?.mobile || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ authStore.user?.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色" :span="2">
          {{ authStore.roleNames.join('、') || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-form ref="formRef" class="password-form" :model="passwordForm" :rules="rules" label-position="top">
        <h4>修改密码</h4>
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="passwordForm.currentPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submitPassword">保存密码</el-button>
      </el-form>
    </div>
  </section>
</template>
