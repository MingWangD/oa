<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

import { register } from '../api/judicial';

const router = useRouter();
const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  mobile: '',
  email: ''
});

const validateConfirmPassword = (rule: any, value: any, callback: any) => {
  if (value === '') {
    callback(new Error('请再次输入密码'));
  } else if (value !== form.password) {
    callback(new Error('两次输入密码不一致!'));
  } else {
    callback();
  }
};

const rules: FormRules<typeof form> = {
  username: [
    { required: true, message: '请输入账号', trigger: 'blur' },
    { min: 3, max: 32, message: '长度在 3 到 32 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '长度在 6 到 64 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validateConfirmPassword, trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  mobile: [
    { pattern: /^(1[3-9]\d{9})?$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' }
  ]
};

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;
  try {
    const payload = {
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      mobile: form.mobile.trim() || undefined,
      email: form.email.trim() || undefined
    };
    await register(payload);
    ElMessage.success('注册成功，请登录');
    await router.push('/login');
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || error?.message || '注册失败');
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
          <p class="auth-kicker">账号申请</p>
          <h1 class="auth-title">建立可追溯的业务身份</h1>
        </div>
      </div>

      <section class="auth-card auth-card--wide" aria-labelledby="register-title">
        <div class="auth-card-header">
          <p class="auth-card-label">注册账号</p>
          <h2 id="register-title">填写人员信息</h2>
        </div>

        <el-form
          ref="formRef"
          class="auth-form auth-form--compact"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="submit"
        >
          <el-form-item label="账号" prop="username">
            <el-input v-model="form.username" autocomplete="off" placeholder="3-32 个字符" />
          </el-form-item>
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="form.realName" autocomplete="off" placeholder="请输入真实姓名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" autocomplete="off" show-password placeholder="6-64 个字符" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" type="password" autocomplete="off" show-password placeholder="请再次输入密码" />
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model="form.mobile" autocomplete="off" placeholder="选填" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" autocomplete="off" placeholder="选填" />
          </el-form-item>
          <el-button class="auth-submit auth-form-span" type="primary" :loading="loading" @click="submit">注册</el-button>
          <p class="auth-switch auth-form-span">
            已有账号？
            <router-link to="/login">返回登录</router-link>
          </p>
        </el-form>
      </section>
    </section>
  </main>
</template>
