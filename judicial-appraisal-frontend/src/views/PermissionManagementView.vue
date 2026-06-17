<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  fetchAdminRoles,
  fetchPlatformMenus,
  fetchRoleMenuIds,
  assignRoleMenus,
  type AdminRole,
  type MenuDto
} from '../api/judicial';

const loading = ref(false);
const saving = ref(false);
const roles = ref<AdminRole[]>([]);
const selectedRoleId = ref<number | null>(null);
const allMenus = ref<MenuDto[]>([]);
const selectedMenuIds = ref<number[]>([]);
const treeRef = ref<any>(null);

const selectedRole = computed(() => {
  return roles.value.find((role) => role.id === selectedRoleId.value) || null;
});

function translateDataScope(scope: string | null | undefined): string {
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

function translateStatus(status: string | null | undefined): string {
  return status === 'enabled' ? '启用' : '禁用';
}

async function initData(): Promise<void> {
  loading.value = true;
  try {
    const [roleData, menuData] = await Promise.all([
      fetchAdminRoles(),
      fetchPlatformMenus()
    ]);
    roles.value = roleData;
    allMenus.value = menuData;
    if (roleData.length > 0) {
      void selectRole(roleData[0].id);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载基础数据失败');
  } finally {
    loading.value = false;
  }
}

async function selectRole(roleId: number): Promise<void> {
  selectedRoleId.value = roleId;
  loading.value = true;
  try {
    const menuIds = await fetchRoleMenuIds(roleId);
    selectedMenuIds.value = menuIds;
    if (treeRef.value) {
      const leafIds = getLeafNodeIds(allMenus.value, menuIds);
      treeRef.value.setCheckedKeys(leafIds);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载角色权限失败');
  } finally {
    loading.value = false;
  }
}

function getLeafNodeIds(menus: MenuDto[], checkedIds: number[]): number[] {
  const leafIds: number[] = [];
  const checkedSet = new Set(checkedIds);
  
  function traverse(nodes: MenuDto[]) {
    for (const node of nodes) {
      if (checkedSet.has(node.id)) {
        if (!node.children || node.children.length === 0) {
          leafIds.push(node.id);
        } else {
          traverse(node.children);
        }
      }
    }
  }
  
  traverse(menus);
  return leafIds;
}

async function savePermissions(): Promise<void> {
  if (!selectedRoleId.value) {
    ElMessage.warning('请先选择一个角色');
    return;
  }
  
  if (!treeRef.value) {
    return;
  }
  
  saving.value = true;
  try {
    const checkedKeys = treeRef.value.getCheckedKeys();
    const halfCheckedKeys = treeRef.value.getHalfCheckedKeys();
    const allKeys = [...checkedKeys, ...halfCheckedKeys];
    
    await assignRoleMenus(selectedRoleId.value, allKeys);
    ElMessage.success('权限保存成功');
    selectedMenuIds.value = allKeys;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存权限失败');
  } finally {
    saving.value = false;
  }
}

function setAllChecked(checked: boolean): void {
  if (!treeRef.value) return;
  if (checked) {
    const allIds: number[] = [];
    function traverse(nodes: MenuDto[]) {
      for (const node of nodes) {
        allIds.push(node.id);
        if (node.children && node.children.length > 0) {
          traverse(node.children);
        }
      }
    }
    traverse(allMenus.value);
    treeRef.value.setCheckedKeys(allIds);
  } else {
    treeRef.value.setCheckedKeys([]);
  }
}

onMounted(() => {
  void initData();
});
</script>

<template>
  <main class="page-container" v-loading="loading">
    <section class="content-card page-block">
      <div class="panel-heading panel-heading--warm">
        <div>
          <h3 class="panel-title">权限管理</h3>
          <p class="panel-subtitle">分配角色与菜单功能按钮的关联，控制系统各模块的访问与操作权限。</p>
        </div>
        <el-button type="primary" :loading="loading" @click="initData">刷新数据</el-button>
      </div>
    </section>

    <div class="permission-layout">
      <section class="content-card role-panel">
        <div class="panel-heading">
          <h3 class="panel-title">系统角色列表</h3>
        </div>
        <div class="role-list">
          <div
            v-for="role in roles"
            :key="role.id"
            class="role-item"
            :class="{ 'is-active': role.id === selectedRoleId }"
            @click="selectRole(role.id)"
          >
            <div class="role-item__main">
              <span class="role-name">{{ role.roleName }}</span>
              <span class="role-code">{{ role.roleCode }}</span>
            </div>
            <div class="role-item__sub">
              <el-tag size="small" :type="role.status === 'enabled' ? 'success' : 'danger'" effect="plain">
                {{ translateStatus(role.status) }}
              </el-tag>
              <el-tag size="small" type="info" effect="plain" style="margin-left: 8px;">
                {{ translateDataScope(role.dataScope) }}
              </el-tag>
            </div>
          </div>
        </div>
      </section>

      <section class="content-card tree-panel">
        <div class="panel-heading" style="justify-content: space-between; align-items: center; display: flex;">
          <div>
            <h3 class="panel-title">
              功能权限配置 
              <span v-if="selectedRole" class="active-role-tag">
                （当前配置角色：{{ selectedRole.roleName }}）
              </span>
            </h3>
            <p class="panel-subtitle">勾选右侧树状菜单和按钮以授予对应角色相应的访问及操作权限。</p>
          </div>
          <div class="action-buttons">
            <el-button size="small" @click="setAllChecked(true)">全选</el-button>
            <el-button size="small" @click="setAllChecked(false)">清空</el-button>
            <el-button type="primary" :loading="saving" @click="savePermissions" style="margin-left: 10px;">
              保存权限
            </el-button>
          </div>
        </div>

        <div class="tree-container">
          <el-tree
            ref="treeRef"
            :data="allMenus"
            show-checkbox
            node-key="id"
            :default-expand-all="true"
            :check-strictly="false"
            :props="{ label: 'menuName', children: 'children' }"
            class="permission-tree"
          >
            <template #default="{ node, data }">
              <div class="tree-node-layout">
                <span class="node-label">{{ data.menuName }}</span>
                <div class="node-tags">
                  <el-tag v-if="data.menuType === 'M'" size="small" type="primary" effect="light">目录</el-tag>
                  <el-tag v-else-if="data.menuType === 'C'" size="small" type="success" effect="light">菜单</el-tag>
                  <el-tag v-else-if="data.menuType === 'F'" size="small" type="warning" effect="light">按钮</el-tag>
                  <span class="node-code-text">{{ data.menuCode }}</span>
                </div>
              </div>
            </template>
          </el-tree>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.permission-layout {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 16px;
  margin-top: 16px;
  align-items: start;
}

.role-panel {
  display: flex;
  flex-direction: column;
  background: var(--td-card-bg);
  min-height: 550px;
}

.role-list {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: 650px;
}

.role-item {
  padding: 12px 16px;
  border-radius: 10px;
  border: 1px solid var(--td-border);
  background: var(--td-card-bg);
  cursor: pointer;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.role-item:hover {
  background: #f8ede8;
  border-color: var(--td-primary);
}

.role-item.is-active {
  background: linear-gradient(90deg, rgba(166, 31, 36, 0.08) 0%, rgba(184, 138, 74, 0.1) 100%);
  border-color: var(--td-primary);
}

.role-item__main {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.role-name {
  font-weight: 600;
  font-size: 15px;
  color: var(--td-text);
}

.role-code {
  font-size: 12px;
  color: var(--td-text-light);
  font-family: monospace;
}

.role-item__sub {
  display: flex;
  align-items: center;
}

.tree-panel {
  background: var(--td-card-bg);
  min-height: 550px;
  padding-bottom: 24px;
}

.active-role-tag {
  color: var(--td-primary);
  font-size: 14px;
  font-weight: 600;
}

.tree-container {
  padding: 20px 24px;
  border-top: 1px solid var(--td-border);
}

.permission-tree {
  font-size: 14px;
}

.tree-node-layout {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 24px;
}

.node-label {
  color: var(--td-text);
  font-weight: 500;
}

.node-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-code-text {
  font-size: 12px;
  color: var(--td-text-light);
  font-family: monospace;
}

.action-buttons {
  display: flex;
  align-items: center;
}
</style>
