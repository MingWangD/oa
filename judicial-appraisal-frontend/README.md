# judicial-appraisal-frontend

基于 Vue 3 + TypeScript + Vite 的最小前端骨架，用于对接司法鉴定后端接口。

## 环境要求

- Node.js 18+
- npm 9+
- 后端服务已启动，默认地址 `http://127.0.0.1:8080`

## 安装依赖

```bash
npm install
```

## 本地联调前准备

当前后端业务接口默认都需要 `Authorization: Bearer <token>`。

推荐做法：

1. 先调用后端登录接口拿到 token
2. 选择下面两种方式之一注入前端

### 方式一：环境变量

新建 `.env.local`：

```env
VITE_API_BASE=/api
VITE_DEV_TOKEN=你的登录token
VITE_DEV_ASSIGNEE_ID=1
```

### 方式二：浏览器本地存储

在浏览器控制台执行：

```js
localStorage.setItem('token', '你的登录token')
```

其中 `VITE_DEV_ASSIGNEE_ID` 用于工作台按指定办理人拉取 summary/todo。

## 本地启动

```bash
npm run dev
```

默认开发地址为 `http://127.0.0.1:5173`。

Vite 已配置代理，前端统一请求 `/api`，再转发到本地后端。

当前已接接口：

- `/api/workbench/summary`
- `/api/workbench/todo`
- `/api/cases`

## 生产构建

```bash
npm run build
```

构建产物输出到 `dist/`。

## 页面说明

- `工作台`：展示汇总指标和待办任务列表
- `案件列表`：展示案件分页列表，并支持关键字与状态筛选
