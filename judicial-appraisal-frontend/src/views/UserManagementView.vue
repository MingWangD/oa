<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';

import {
  assignAdminUserRoles,
  createAdminUser,
  fetchAdminDepts,
  fetchAdminPosts,
  fetchAdminRoles,
  fetchAdminUsers,
  updateAdminUser,
  updateRoleDataScope,
  type AdminRole,
  type AdminUser,
  type OrganizationDept,
  type OrganizationPost
} from '../api/judicial';

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const roleDialogVisible = ref(false);
const dataScopeDialogVisible = ref(false);
const editingUser = ref<AdminUser | null>(null);
const editingRole = ref<AdminRole | null>(null);
const selectedRoleIds = ref<number[]>([]);
const formRef = ref<FormInstance>();
const keyword = ref('');
const users = ref<AdminUser[]>([]);
const roles = ref<AdminRole[]>([]);
const depts = ref<OrganizationDept[]>([]);
const posts = ref<OrganizationPost[]>([]);
const dataScopeForm = reactive({
  dataScope: 'self',
  deptIds: [] as number[]
});

const form = reactive({
  username: '',
  realName: '',
  password: '',
  mobile: '',
  email: '',
  deptId: null as number | null,
  postId: null as number | null,
  status: 'enabled'
});

const rules = computed<FormRules<typeof form>>(() => ({
  username: editingUser.value ? [] : [{ required: true, message: '请输入账号', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  password: editingUser.value ? [] : [{ required: true, min: 6, message: '密码至少 6 位', trigger: 'blur' }]
}));

function resetForm(): void {
  form.username = '';
  form.realName = '';
  form.password = '';
  form.mobile = '';
  form.email = '';
  form.deptId = null;
  form.postId = null;
  form.status = 'enabled';
}

async function loadData(): Promise<void> {
  loading.value = true;
  try {
    const [userData, roleData, deptData, postData] = await Promise.all([
      fetchAdminUsers(keyword.value.trim()),
      fetchAdminRoles(),
      fetchAdminDepts(),
      fetchAdminPosts()
    ]);
    users.value = userData;
    roles.value = roleData;
    depts.value = deptData;
    posts.value = postData;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户管理失败');
  } finally {
    loading.value = false;
  }
}

function openCreate(): void {
  editingUser.value = null;
  resetForm();
  dialogVisible.value = true;
}

function openEdit(user: AdminUser): void {
  editingUser.value = user;
  form.username = user.username;
  form.realName = user.realName;
  form.password = '';
  form.mobile = user.mobile || '';
  form.email = user.email || '';
  form.deptId = user.deptId;
  form.postId = user.postId;
  form.status = user.status || 'enabled';
  dialogVisible.value = true;
}

async function saveUser(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  saving.value = true;
  try {
    if (editingUser.value) {
      await updateAdminUser(editingUser.value.id, {
        realName: form.realName,
        mobile: form.mobile || undefined,
        email: form.email || undefined,
        deptId: form.deptId,
        postId: form.postId,
        status: form.status
      });
    } else {
      await createAdminUser({
        username: form.username,
        realName: form.realName,
        password: form.password,
        mobile: form.mobile || undefined,
        email: form.email || undefined,
        deptId: form.deptId,
        postId: form.postId,
        status: form.status
      });
    }
    ElMessage.success('用户已保存');
    dialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存用户失败');
  } finally {
    saving.value = false;
  }
}

function openRoles(user: AdminUser): void {
  editingUser.value = user;
  selectedRoleIds.value = user.roles.map((role) => role.id);
  roleDialogVisible.value = true;
}

async function saveRoles(): Promise<void> {
  if (!editingUser.value) {
    return;
  }

  saving.value = true;
  try {
    await assignAdminUserRoles(editingUser.value.id, selectedRoleIds.value);
    ElMessage.success('角色已更新');
    roleDialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存角色失败');
  } finally {
    saving.value = false;
  }
}

function scopeLabel(scope: string | null | undefined): string {
  const value = scope || 'self';
  const labels: Record<string, string> = {
    all: '全部数据',
    dept_sub: '本部门及下级',
    custom: '自定义组织',
    dept: '本部门',
    self: '本人'
  };
  return labels[value] || value;
}

function statusLabel(status: string | null | undefined): string {
  return status === 'enabled' ? '启用' : '禁用';
}

function openDataScope(role: AdminRole): void {
  editingRole.value = role;
  dataScopeForm.dataScope = role.dataScope || 'self';
  dataScopeForm.deptIds = [...(role.customDeptIds || [])];
  dataScopeDialogVisible.value = true;
}

async function saveDataScope(): Promise<void> {
  if (!editingRole.value) {
    return;
  }
  saving.value = true;
  try {
    await updateRoleDataScope(editingRole.value.id, {
      dataScope: dataScopeForm.dataScope,
      deptIds: dataScopeForm.dataScope === 'custom' ? dataScopeForm.deptIds : []
    });
    ElMessage.success('角色数据权限已更新');
    dataScopeDialogVisible.value = false;
    await loadData();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存数据权限失败');
  } finally {
    saving.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <section class="content-card page-block">
    <div class="panel-heading panel-heading--warm">
      <div>
        <h3 class="panel-title">用户管理</h3>
        <p class="panel-subtitle">维护系统用户基础信息和角色，为后续 RBAC 与数据权限打底。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增用户</el-button>
    </div>

    <div class="query-bar">
      <el-input v-model="keyword" placeholder="账号、姓名、手机号" clearable style="width: 260px" @keyup.enter="loadData" />
      <el-button type="primary" :loading="loading" @click="loadData">查询</el-button>
    </div>

    <div class="table-frame">
      <el-table :data="users" border stripe :loading="loading">
        <el-table-column prop="username" label="账号" min-width="130" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="deptName" label="部门" min-width="140">
          <template #default="scope">{{ scope.row.deptName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="postName" label="岗位" min-width="140">
          <template #default="scope">{{ scope.row.postName || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="220">
          <template #default="scope">
            {{ (scope.row.roles || []).map((role: any) => role.roleName).join('、') || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'enabled' ? 'success' : 'danger'" effect="plain">
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="scope">
            <el-button link type="primary" @click="openEdit(scope.row)">编辑</el-button>
            <el-button link type="primary" @click="openRoles(scope.row)">角色</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <section class="content-card page-block">
    <div class="panel-heading">
      <div>
        <h3 class="panel-title">角色数据权限</h3>
        <p class="panel-subtitle">维护 全部数据、本部门及下级、自定义组织、本部门、本人 五类数据范围；自定义组织 可绑定指定组织。</p>
      </div>
    </div>
    <div class="table-frame">
      <el-table :data="roles" border stripe>
        <el-table-column prop="roleCode" label="角色编码" min-width="140" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column label="数据范围" min-width="140">
          <template #default="scope">{{ scopeLabel(scope.row.dataScope) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'enabled' ? 'success' : 'danger'" effect="plain">
              {{ statusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="openDataScope(scope.row)">设置范围</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>

  <el-dialog v-model="dialogVisible" :title="editingUser ? '编辑用户' : '新增用户'" width="620px">
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <div class="workflow-form-grid">
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" :disabled="Boolean(editingUser)" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="初始密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status">
            <el-option label="启用" value="enabled" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="部门" prop="deptId">
          <el-select v-model="form.deptId" clearable filterable>
            <el-option v-for="item in depts" :key="item.id" :label="item.deptName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位" prop="postId">
          <el-select v-model="form.postId" clearable filterable>
            <el-option v-for="item in posts" :key="item.id" :label="item.postName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="mobile">
          <el-input v-model="form.mobile" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" />
        </el-form-item>
      </div>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="roleDialogVisible" title="分配角色" width="520px">
    <el-checkbox-group v-model="selectedRoleIds" class="role-check-list">
      <el-checkbox v-for="role in roles" :key="role.id" :label="role.id">{{ role.roleName }}</el-checkbox>
    </el-checkbox-group>
    <template #footer>
      <el-button @click="roleDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dataScopeDialogVisible" title="设置角色数据权限" width="560px">
    <el-form label-position="top">
      <el-form-item label="角色">
        <el-input :model-value="editingRole ? `${editingRole.roleName}（${editingRole.roleCode}）` : ''" disabled />
      </el-form-item>
      <el-form-item label="数据范围">
        <el-select v-model="dataScopeForm.dataScope">
          <el-option label="全部数据" value="all" />
          <el-option label="本部门及下级" value="dept_sub" />
          <el-option label="自定义组织" value="custom" />
          <el-option label="本部门" value="dept" />
          <el-option label="本人" value="self" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="dataScopeForm.dataScope === 'custom'" label="可访问部门">
        <el-select v-model="dataScopeForm.deptIds" multiple filterable clearable>
          <el-option v-for="item in depts" :key="item.id" :label="item.deptName" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dataScopeDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveDataScope">保存</el-button>
    </template>
  </el-dialog>
</template>
