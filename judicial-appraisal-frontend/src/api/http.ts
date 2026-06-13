export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export type QueryParams = Record<string, unknown>;

export const tokenStorageKey = 'token';
export const authUserStorageKey = 'authUser';

const apiBase = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '');

function buildUrl(path: string, params?: QueryParams): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const requestPath =
    normalizedPath.startsWith(`${apiBase}/`) || normalizedPath === apiBase
      ? normalizedPath
      : `${apiBase}${normalizedPath}`;
  const searchParams = new URLSearchParams();

  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (
        (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') &&
        value !== ''
      ) {
        searchParams.set(key, String(value));
      }
    });
  }

  const query = searchParams.toString();
  return query ? `${requestPath}?${query}` : requestPath;
}

function isBrowser(): boolean {
  return typeof window !== 'undefined';
}

async function parseApiResponse<T>(response: Response): Promise<ApiResponse<T> | null> {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as ApiResponse<T>;
  } catch {
    return null;
  }
}

function redirectToLogin(): void {
  if (!isBrowser() || window.location.pathname === '/login') {
    return;
  }

  const redirectPath = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  const target =
    redirectPath && redirectPath !== '/'
      ? `/login?redirect=${encodeURIComponent(redirectPath)}`
      : '/login';
  window.location.replace(target);
}

export function getAccessToken(): string {
  if (!isBrowser()) {
    return '';
  }

  return window.localStorage.getItem(tokenStorageKey)?.trim() || '';
}

export function setAccessToken(token: string): void {
  if (!isBrowser()) {
    return;
  }

  const value = token.trim();
  if (value) {
    window.localStorage.setItem(tokenStorageKey, value);
    return;
  }

  window.localStorage.removeItem(tokenStorageKey);
}

export function clearAccessToken(): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.removeItem(tokenStorageKey);
}

export function clearAuthStorage(): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.removeItem(tokenStorageKey);
  window.localStorage.removeItem(authUserStorageKey);
  window.localStorage.removeItem('currentUserName');
}

async function request<T>(path: string, init?: RequestInit & { params?: QueryParams }): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(buildUrl(path, init?.params), {
    ...init,
    headers
  });
  const result = await parseApiResponse<T>(response);

  if (response.status === 401) {
    clearAuthStorage();
    redirectToLogin();
    throw new Error(result?.message || '未登录或登录状态已失效，请重新登录');
  }

  if (!response.ok) {
    throw new Error(result?.message || `请求失败：${response.status}`);
  }

  if (!result) {
    throw new Error('接口返回格式异常');
  }

  if (result.code !== 0) {
    throw new Error(result.message || '接口返回异常');
  }

  return result.data;
}

export function get<T>(path: string, params?: QueryParams): Promise<T> {
  return request<T>(path, { method: 'GET', params });
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: body ? JSON.stringify(body) : undefined
  });
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json'
    },
    body: body ? JSON.stringify(body) : undefined
  });
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' });
}

export function postForm<T>(path: string, body: FormData): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    body
  });
}

export async function getBlob(path: string, params?: QueryParams): Promise<{ blob: Blob; filename: string }> {
  const token = getAccessToken();
  const headers = new Headers();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  const response = await fetch(buildUrl(path, params), { method: 'GET', headers });
  if (response.status === 401) {
    clearAuthStorage();
    redirectToLogin();
    throw new Error('未登录或登录状态已失效，请重新登录');
  }
  if (!response.ok) {
    throw new Error(`请求失败：${response.status}`);
  }
  const disposition = response.headers.get('content-disposition') || '';
  const filenameMatch = disposition.match(/filename\*=UTF-8''([^;]+)|filename="([^"]+)"/i);
  const filename = decodeURIComponent(filenameMatch?.[1] || filenameMatch?.[2] || 'download');
  return { blob: await response.blob(), filename };
}
