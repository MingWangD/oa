<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DocumentAdd, Files, FolderOpened, House, User } from '@element-plus/icons-vue';
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

const menuGroups = computed<MenuGroup[]>(() => {
  const groups: MenuGroup[] = [
    {
      title: '快捷菜单',
      items: [
        { title: '电子邮件', path: '/placeholder/quick/mail', icon: Files },
        { title: '日程', path: '/placeholder/quick/calendar', icon: House },
        { title: '知识库', path: '/knowledge', icon: FolderOpened }
      ]
    },
    {
      title: '个人事务',
      items: [
        { title: '消息', path: '/placeholder/personal/message', icon: Files },
        { title: '任务', path: '/placeholder/personal/task', icon: Files },
        { title: '工作日志', path: '/placeholder/personal/log', icon: Files },
        { title: '通讯簿', path: '/placeholder/personal/address', icon: User }
      ]
    },
    {
      title: '流程中心',
      items: [
        { title: '新建工作', path: '/case/new', icon: DocumentAdd },
        { title: '我的工作', path: '/my-work', icon: House },
        { title: '工作查询', path: '/work-query', icon: Files },
        { title: '工作监控', path: '/placeholder/workflow/monitor', icon: Files },
        { title: '超时统计分析', path: '/placeholder/workflow/timeout', icon: Files },
        { title: '工作委托', path: '/placeholder/workflow/delegate', icon: Files },
        { title: '工作销毁', path: '/placeholder/workflow/destroy', icon: Files },
        { title: '流程日志查询', path: '/placeholder/workflow/log', icon: Files },
        { title: '数据归档', path: '/placeholder/workflow/archive', icon: FolderOpened },
        { title: '数据报表', path: '/placeholder/workflow/report', icon: Files },
        { title: '设计表单', path: '/placeholder/workflow/forms', icon: Files },
        { title: '设计流程', path: '/placeholder/workflow/processes', icon: Files },
        { title: '报表设置', path: '/placeholder/workflow/report-setting', icon: Files },
        { title: '数据源管理', path: '/placeholder/workflow/datasource', icon: Files }
      ]
    },
    {
      title: '应用中心',
      items: [
        { title: '我的应用', path: '/placeholder/application/mine', icon: Files },
        { title: '设计应用', path: '/placeholder/application/design', icon: Files },
        { title: '基础代码', path: '/placeholder/application/code', icon: Files }
      ]
    },
    {
      title: '业务管理',
      items: [
        { title: 'CRM', path: '/placeholder/crm', icon: Files },
        { title: '绩效', path: '/placeholder/performance', icon: Files },
        { title: '合同', path: '/placeholder/contract', icon: Files },
        { title: '项目', path: '/placeholder/project', icon: Files },
        { title: '仓库', path: '/placeholder/warehouse', icon: Files },
        { title: '安全风险', path: '/placeholder/risk', icon: Files }
      ]
    },
    {
      title: '行政与知识',
      items: [
        { title: '行政办公', path: '/placeholder/admin-office', icon: Files },
        { title: '知识库', path: '/knowledge', icon: FolderOpened },
        { title: '案件自动归档', path: '/placeholder/knowledge/archive', icon: FolderOpened },
        { title: '网络硬盘', path: '/placeholder/knowledge/disk', icon: FolderOpened }
      ]
    },
    {
      title: '督查门户报表',
      items: [
        { title: '督查督办', path: '/placeholder/supervision', icon: Files },
        { title: '智能门户', path: '/placeholder/portal', icon: House },
        { title: '报表中心', path: '/placeholder/report-center', icon: Files }
      ]
    },
    {
      title: '人资公文档案',
      items: [
        { title: '人力资源', path: '/placeholder/hr', icon: User },
        { title: '考勤', path: '/placeholder/attendance', icon: Files },
        { title: '公文', path: '/placeholder/official-doc', icon: Files },
        { title: '档案', path: '/placeholder/archive', icon: FolderOpened },
        { title: '交流园地', path: '/placeholder/community', icon: Files }
      ]
    },
    {
      title: '开放与集成',
      items: [
        { title: '外部系统集成', path: '/placeholder/integration/open-api', icon: Files },
        { title: 'SSO', path: '/placeholder/integration/sso', icon: Files },
        { title: '统一待办', path: '/placeholder/integration/todo', icon: Files },
        { title: '附件程序', path: '/placeholder/integration/tools', icon: Files }
      ]
    },
    {
      title: '知识管理',
      items: [{ title: '知识库', path: '/knowledge', icon: FolderOpened }]
    }
  ];

  if (authStore.isAdmin) {
    groups.push({
      title: '系统管理',
      items: [{ title: '用户管理', path: '/admin/users', icon: User }]
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
