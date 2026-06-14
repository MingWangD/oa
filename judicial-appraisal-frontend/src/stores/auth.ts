import { defineStore } from 'pinia';

import {
  authUserStorageKey,
  clearAuthStorage,
  getAccessToken,
  setAccessToken,
  tokenStorageKey
} from '../api/http';
import {
  changePassword,
  fetchCurrentUser,
  fetchPlatformMenus,
  login,
  logout,
  type ChangePasswordPayload,
  type MenuDto,
  type UserInfo
} from '../api/judicial';

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

function readStoredUser(): UserInfo | null {
  if (!isBrowser()) {
    return null;
  }

  const raw = window.localStorage.getItem(authUserStorageKey);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as UserInfo;
  } catch {
    window.localStorage.removeItem(authUserStorageKey);
    return null;
  }
}

function storeUser(user: UserInfo | null): void {
  if (!isBrowser()) {
    return;
  }

  if (user) {
    window.localStorage.setItem(authUserStorageKey, JSON.stringify(user));
    window.localStorage.setItem('currentUserName', user.realName || user.username);
    return;
  }

  window.localStorage.removeItem(authUserStorageKey);
  window.localStorage.removeItem('currentUserName');
}

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getAccessToken(),
    user: readStoredUser(),
    menus: [] as MenuDto[],
    restoring: false
  }),

  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    displayName: (state) => state.user?.realName || state.user?.username || '管理员',
    roleNames: (state) => state.user?.roles?.map((role) => role.name).filter(Boolean) ?? [],
    isAdmin: (state) =>
      Boolean(
        state.user?.roles?.some((role) =>
          ['ADMIN', 'ROLE_ADMIN'].includes(role.code?.toUpperCase() ?? '')
        )
      ),
    statusLabel: (state) => {
      if (!state.user?.status) {
        return '未登录';
      }
      return state.user.status === 'enabled' ? '启用' : state.user.status;
    },
    permissions: (state) => state.user?.permissions ?? []
  },

  actions: {
    async restoreSession(): Promise<void> {
      this.token = getAccessToken();
      this.user = readStoredUser();
      if (!this.token) {
        return;
      }

      if (!this.user) {
        this.restoring = true;
        try {
          this.user = await fetchCurrentUser();
          storeUser(this.user);
        } catch {
          this.clearSession();
          return;
        } finally {
          this.restoring = false;
        }
      }

      if (this.isAuthenticated) {
        void this.fetchMenus();
      }
    },

    async signIn(username: string, password: string): Promise<UserInfo> {
      const response = await login(username, password);
      this.token = response.token;
      this.user = response.user;
      setAccessToken(response.token);
      storeUser(response.user);
      void this.fetchMenus();
      return response.user;
    },

    async fetchCurrentUser(): Promise<UserInfo> {
      const user = await fetchCurrentUser();
      this.user = user;
      storeUser(user);
      return user;
    },

    async fetchMenus(): Promise<void> {
      try {
        this.menus = await fetchPlatformMenus();
      } catch (error) {
        console.error('Failed to fetch menus', error);
      }
    },

    async signOut(): Promise<void> {
      try {
        if (this.token || window.localStorage.getItem(tokenStorageKey)) {
          await logout();
        }
      } finally {
        this.clearSession();
      }
    },

    async changePassword(payload: ChangePasswordPayload): Promise<void> {
      await changePassword(payload);
    },

    clearSession(): void {
      this.token = '';
      this.user = null;
      this.menus = [];
      clearAuthStorage();
    }
  }
});
