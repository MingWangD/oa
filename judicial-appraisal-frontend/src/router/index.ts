import { createRouter, createWebHistory, type Router, type RouteRecordRaw } from 'vue-router';
import type { Pinia } from 'pinia';

import { useAuthStore } from '../stores/auth';

const LoginView = () => import('../views/LoginView.vue');
const NewWorkView = () => import('../views/NewWorkView.vue');
const CaseDetailView = () => import('../views/CaseDetailView.vue');
const WorkbenchView = () => import('../views/WorkbenchView.vue');
const CaseListView = () => import('../views/CaseListView.vue');
const KnowledgeBaseView = () => import('../views/KnowledgeBaseView.vue');
const ContractManagementView = () => import('../views/ContractManagementView.vue');
const WorkflowFormDesignerView = () => import('../views/WorkflowFormDesignerView.vue');
const WorkflowProcessDesignerView = () => import('../views/WorkflowProcessDesignerView.vue');
const UserManagementView = () => import('../views/UserManagementView.vue');
const ProfileView = () => import('../views/ProfileView.vue');
const PlaceholderView = () => import('../views/PlaceholderView.vue');
const ReportCenterView = () => import('../views/ReportCenterView.vue');

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/my-work'
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: {
      title: '登录',
      public: true
    }
  },
  {
    path: '/case/new',
    name: 'case-new',
    component: NewWorkView,
    meta: {
      title: '新建工作',
      tabKey: 'case-new',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/case/:id',
    name: 'case-detail',
    component: CaseDetailView,
    meta: {
      title: '案件详情',
      tabKey: 'case-detail',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/my-work',
    name: 'my-work',
    component: WorkbenchView,
    meta: {
      title: '我的工作',
      tabKey: 'my-work',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/work-query',
    name: 'work-query',
    component: CaseListView,
    meta: {
      title: '工作查询',
      tabKey: 'work-query',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/work-report',
    name: 'report-center',
    component: ReportCenterView,
    meta: {
      title: '数据报表',
      tabKey: 'report-center',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/knowledge',
    name: 'knowledge',
    component: KnowledgeBaseView,
    meta: {
      title: '知识库',
      tabKey: 'knowledge',
      menuGroup: 'knowledge',
      requiresAuth: true
    }
  },
  {
    path: '/placeholder/contract',
    name: 'contract-management',
    component: ContractManagementView,
    meta: {
      title: '合同管理',
      tabKey: 'contract-management',
      menuGroup: 'business-suite',
      requiresAuth: true
    }
  },
  {
    path: '/workflow/forms',
    name: 'workflow-form-designer',
    component: WorkflowFormDesignerView,
    meta: {
      title: '设计表单',
      tabKey: 'workflow-form-designer',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/workflow/processes',
    name: 'workflow-process-designer',
    component: WorkflowProcessDesignerView,
    meta: {
      title: '设计流程',
      tabKey: 'workflow-process-designer',
      menuGroup: 'workflow',
      requiresAuth: true
    }
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: UserManagementView,
    meta: {
      title: '用户管理',
      tabKey: 'admin-users',
      menuGroup: 'system',
      requiresAuth: true,
      requiresAdmin: true
    }
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
    meta: {
      title: '个人资料',
      tabKey: 'profile',
      requiresAuth: true
    }
  },
  {
    path: '/placeholder/:pathMatch(.*)*',
    name: 'placeholder',
    component: PlaceholderView,
    meta: {
      title: '待建设模块',
      tabKey: 'placeholder',
      requiresAuth: true
    }
  },
  {
    path: '/new-work',
    redirect: '/case/new'
  },
  {
    path: '/workbench',
    redirect: '/my-work'
  },
  {
    path: '/cases',
    redirect: '/work-query'
  }
];

export function createAppRouter(pinia: Pinia): Router {
  const router = createRouter({
    history: createWebHistory(),
    routes
  });

  router.beforeEach((to) => {
    const authStore = useAuthStore(pinia);
    const isAuthenticated = authStore.isAuthenticated;

    if (to.path === '/login' && isAuthenticated) {
      return {
        path: '/my-work'
      };
    }

    if (to.meta.requiresAuth && !isAuthenticated) {
      return {
        path: '/login',
        query: to.fullPath === '/my-work' ? undefined : { redirect: to.fullPath }
      };
    }

    if (to.meta.requiresAdmin && !authStore.isAdmin) {
      return {
        path: '/my-work'
      };
    }

    return true;
  });

  return router;
}
