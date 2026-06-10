<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

import { useAuthStore } from '../stores/auth';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({
  username: 'admin',
  password: 'Admin123'
});

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;
  try {
    await authStore.signIn(form.username.trim(), form.password);
    ElMessage.success('登录成功');
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/my-work';
    await router.push(redirect);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <div class="login-brand">
        <p class="login-eyebrow">完整 OA 系统重构</p>
        <h1>电子司法鉴定所司法鉴定管理系统</h1>
        <p>基于 Spring Boot + Vue 的完整 OA 平台重构，保留旧系统业务语义并逐步现代化。</p>
      </div>

      <el-form ref="formRef" class="login-form" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button class="login-submit" type="primary" :loading="loading" @click="submit">登录</el-button>
      </el-form>
    </section>
  </main>
</template>
