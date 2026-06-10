<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import * as ElementPlusIcons from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

import { useAuthStore } from './stores/auth';

interface MenuItem {
  title: string;
  path: string;
  icon: unknown;
}

interface MenuGroup {
  title: string;
  items: MenuItem[];
}

interface TabItem {
  title: string;
  path: string;
  closable: boolean;
}

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const homePath = '/home';
const loggingOut = ref(false);
const isLoginPage = computed(() => route.path === '/login');
const currentUserName = computed(() => authStore.displayName);
const currentUserMeta = computed(() => authStore.roleNames.join(' / ') || authStore.statusLabel);
const avatarText = computed(() => currentUserName.value.slice(0, 1) || '管');

function getIcon(name: string): unknown {
  if (!name || name === '#') {
    return ElementPlusIcons.Files;
  }
  return (ElementPlusIcons as any)[name] || ElementPlusIcons.Files;
}

const menuGroups = computed<MenuGroup[]>(() => {
  if (authStore.menus && authStore.menus.length > 0) {
    return authStore.menus.map((m) => ({
      title: m.menuName,
      items: (m.children || []).map((c) => ({
        title: c.menuName,
        path: c.path,
        icon: getIcon(c.icon)
      }))
    }));
  }

  // Fallback to hardcoded menus if dynamic ones are not yet loaded
  const groups: MenuGroup[] = [
    {
      title: '流程中心',
      items: [
        { title: '新建工作', path: '/case/new', icon: ElementPlusIcons.DocumentAdd },
        { title: '我的工作', path: '/my-work', icon: ElementPlusIcons.House },
        { title: '工作查询', path: '/work-query', icon: ElementPlusIcons.Files }
      ]
    }
  ];

  if (authStore.isAdmin) {
    groups.push({
      title: '系统管理',
      items: [{ title: '用户管理', path: '/admin/users', icon: ElementPlusIcons.User }]
    });
  }

  return groups;
});

const openTabs = ref<TabItem[]>([{ title: '首页', path: homePath, closable: false }]);
const activePath = computed(() => route.path);

function ensureTab(path: string, title: string): void {
  if (!openTabs.value.some((item) => item.path === path)) {
    openTabs.value.push({ path, title, closable: path !== homePath });
  }
}

function openMenu(path: string): void {
  void router.push(path);
}

function openProfile(): void {
  if (route.path !== '/profile') {
    void router.push('/profile');
  }
}

function activateTab(name: string | number): void {
  const path = String(name);
  if (path !== route.path) {
    void router.push(path);
  }
}

function closeTab(path: string): void {
  const index = openTabs.value.findIndex((item) => item.path === path);
  if (index === -1 || path === homePath) {
    return;
  }

  const isActive = route.path === path;
  openTabs.value.splice(index, 1);

  if (openTabs.value.length === 0) {
    openTabs.value.push({ title: '首页', path: homePath, closable: false });
    void router.push(homePath);
    return;
  }

  if (isActive) {
    const fallback = openTabs.value[index] ?? openTabs.value[index - 1] ?? openTabs.value[0];
    void router.push(fallback.path);
  }
}

async function handleLogout(): Promise<void> {
  loggingOut.value = true;
  try {
    await authStore.signOut();
    openTabs.value = [{ title: '首页', path: homePath, closable: false }];
    ElMessage.success('已退出登录');
    void router.push('/login');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '退出登录失败');
  } finally {
    loggingOut.value = false;
  }
}

watch(
  () => route.fullPath,
  () => {
    const title = String(route.meta.title ?? '');
    if (title && route.path !== '/login') {
      ensureTab(route.path, title);
    }
  },
  { immediate: true }
);
</script>

<template>
  <RouterView v-if="isLoginPage" />

  <el-container v-else class="td-layout">
    <el-header class="td-header">
      <div class="td-logo">电子司法鉴定所司法鉴定管理系统</div>

      <div class="td-userbox">
        <button type="button" class="td-profile-trigger" @click="openProfile">
          <el-avatar class="td-avatar">{{ avatarText }}</el-avatar>
          <span class="td-useridentity">
            <span class="td-username">{{ currentUserName }}</span>
            <span class="td-userhint">{{ currentUserMeta }}</span>
          </span>
        </button>
        <el-button text class="td-logout" :loading="loggingOut" @click="handleLogout">退出登录</el-button>
      </div>
    </el-header>

    <div class="td-tabbar">
      <el-tabs :model-value="activePath" type="card" @tab-change="activateTab" @tab-remove="closeTab">
        <el-tab-pane
          v-for="item in openTabs"
          :key="item.path"
          :name="item.path"
          :label="item.title"
          :closable="item.closable"
        />
      </el-tabs>
    </div>

    <el-container class="td-main">
      <el-aside class="td-sidebar" width="220px">
        <section v-for="group in menuGroups" :key="group.title" class="menu-section">
          <h3 class="menu-section-title">{{ group.title }}</h3>
          <el-menu class="td-menu" :default-active="activePath" :router="false">
            <el-menu-item v-for="item in group.items" :key="item.path" :index="item.path" @click="openMenu(item.path)">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </el-menu>
        </section>
      </el-aside>

      <el-main class="td-content">
        <el-breadcrumb class="td-breadcrumb" separator=">">
          <el-breadcrumb-item>当前位置</el-breadcrumb-item>
          <el-breadcrumb-item>{{ String(route.meta.title ?? '') }}</el-breadcrumb-item>
        </el-breadcrumb>
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
