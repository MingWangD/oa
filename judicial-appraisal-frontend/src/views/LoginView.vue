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
  username: '',
  password: ''
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
  <main class="auth-page">
    <section class="auth-shell">
      <div class="auth-hero">
        <div>
          <p class="auth-kicker">电子司法鉴定所</p>
          <h1 class="auth-title">司法鉴定业务管理系统</h1>
        </div>

        <div class="auth-metrics" aria-label="系统能力">
          <div class="auth-metric">
            <strong>流程</strong>
            <span>鉴定节点闭环</span>
          </div>
          <div class="auth-metric">
            <strong>材料</strong>
            <span>收发留痕管理</span>
          </div>
          <div class="auth-metric">
            <strong>权限</strong>
            <span>角色分级控制</span>
          </div>
        </div>
      </div>

      <section class="auth-card" aria-labelledby="login-title">
        <div class="auth-card-header">
          <p class="auth-card-label">账号登录</p>
          <h2 id="login-title">进入工作台</h2>
        </div>

        <el-form
          ref="formRef"
          class="auth-form"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" autocomplete="off" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" autocomplete="off" show-password placeholder="请输入密码" />
          </el-form-item>
          <el-button class="auth-submit" type="primary" :loading="loading" @click="submit">登录</el-button>
          <p class="auth-switch">
            没有账号？
            <router-link to="/register">申请注册</router-link>
          </p>
        </el-form>
      </section>
    </section>
  </main>
</template>
