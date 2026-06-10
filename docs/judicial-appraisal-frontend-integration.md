# 司法鉴定前端骨架对接说明

更新时间：2026-05-21

本文只说明前端骨架如何对接当前 Spring Boot 后端，默认目标是先跑通 `工作台` 和 `案件列表` 两个页面。不涉及后端改造。

## 1. 建议的页面骨架

### 1.1 工作台 `/workbench`

建议先做成一屏可用的业务首页：

- 顶部：当前用户信息、退出入口
- 统计卡片：`todoCount`、`doneCount`、`processingCount`、`overdueCount`
- 左侧：我的待办列表
- 右侧：我最近已办列表

数据映射：

- 统计卡片 -> `GET /api/workbench/summary?assigneeId={userId}`
- 待办列表 -> `GET /api/workbench/todo?assigneeId={userId}`
- 已办列表 -> `GET /api/workbench/done?assigneeId={userId}`

待办/已办列表首版字段够用即可：

- `taskTitle`
- `nodeName`
- `status`
- `deadlineTime`

点击一行后，首版建议跳到案件详情页或先弹出任务详情抽屉，详情接口可用：

- `GET /api/tasks/{taskId}`

### 1.2 案件列表 `/cases`

建议结构：

- 顶部筛选栏：关键词、案件状态、受理部门、当前处理人
- 中部表格
- 底部分页

筛选项来源：

- 受理部门 -> `GET /api/admin/depts`
- 当前处理人 -> `GET /api/admin/users`

表格主列建议直接对应后端返回字段：

- `caseNo`
- `caseTitle`
- `caseStatusName`
- `currentNodeName`
- `currentHandlerName`
- `acceptDeptName`
- `entrustOrgName`
- `deadlineTime`
- `createdTime`

列表接口：

- `GET /api/cases`

常用查询参数：

- `keyword`
- `caseStatus`
- `acceptDeptId`
- `currentHandlerId`
- `pageNo`
- `pageSize`

案件详情接口：

- `GET /api/cases/{caseId}`

如果列表页首版要放“提交”“办理”按钮，可继续接：

- 提交草稿：`POST /api/cases/{caseId}/submit`
- 办理动作：`POST /api/cases/{caseId}/actions`

## 2. 请求约定

### 2.1 基础地址

开发环境建议前端统一请求 `/api`，再由本地 dev server 代理到：

```text
http://localhost:8080
```

### 2.2 统一响应

后端统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

前端约定：

- `code === 0` 视为成功
- `code !== 0` 统一提示 `message`
- 401 时跳回登录页或清空本地 token

### 2.3 鉴权

除了 `/api/auth/**` 之外，其它接口都需要：

```http
Authorization: Bearer <token>
```

登录接口：

- `POST /api/auth/login`
- body: `{ "username": "...", "password": "..." }`

登录成功后会返回：

- `token`
- `user.id`
- `user.username`
- `user.realName`

当前用户接口：

- `GET /api/auth/me`

### 2.4 时间字段

时间字段是 ISO-8601 字符串，例如：

```text
2026-05-21T09:30:00
```

前端需要自行格式化展示。

## 3. 建议的对接顺序

1. 先接登录、token 持久化、请求拦截器
2. 再接工作台 summary/todo/done
3. 再接案件列表、部门/人员筛选
4. 最后再补案件详情、任务详情、流程动作

如果当前骨架还没做真实登录页，可以先保留一个开发兜底：

- `VITE_DEV_TOKEN`
- `VITE_DEV_ASSIGNEE_ID`

即本地开发先手工登录拿 token，再把 token/userId 注入前端调试。

## 4. 建议的启动方式

### 4.1 后端

在 `judicial-appraisal-backend` 下启动：

```bash
mvn spring-boot:run
```

默认端口见 `src/main/resources/application.yml`，当前是 `8080`。

### 4.2 前端

建议前端骨架使用 Vite，并通过代理解决跨域。示例：

```ts
// vite.config.ts
server: {
  port: 5173,
  proxy: {
    "/api": {
      target: "http://localhost:8080",
      changeOrigin: true
    }
  }
}
```

建议环境变量：

```env
VITE_API_BASE=/api
```

这样前端开发时只请求 `/api/...`，不要把 `localhost:8080` 写死在业务代码里。

## 5. 当前限制

- 当前前端骨架应按“最小实现”理解：先保证可请求、可展示、可跳转，样式不必重。
- 后端虽然已有 `login/me/logout`，但如果前端还没做完整登录态联调，建议先用手工 token 方式跑通主流程。
- 后端未见单独的 CORS 配置，前端本地联调应优先走 dev proxy。
- 数据库脚本里没有现成演示用户；真实登录依赖 `sys_user` 表存在账号，且 `password_hash` 需要是 BCrypt 值。
- `GET /api/workbench/summary` 里的 `processingCount` 当前与 `todoCount` 使用同一套统计条件，更接近“待办任务数”而不是真正“办理中案件数”。
- `GET /api/workbench/done` 当前固定只返回最近 20 条，未分页。
- `GET /api/tasks/todo`、`GET /api/tasks/done` 当前未分页。
- `GET /api/cases` 的 `keyword` 当前只匹配 `caseTitle`，不匹配 `caseNo`。
- 流程动作里只有一部分动作真正驱动流转；如果前端要做动作按钮，先优先支持 `SUBMIT`、`APPROVE`、`COMPLETE`、`RETURN`、`TERMINATE`、`REOPEN`。

## 6. 首版最小可交付

如果只做一版能联调的前端骨架，建议以这两个页面作为完成标准：

- 工作台：能登录后看到我的统计、我的待办、我的已办
- 案件列表：能筛选、分页、查看案件基础信息

这样已经可以覆盖当前后端最稳定的一批接口。
