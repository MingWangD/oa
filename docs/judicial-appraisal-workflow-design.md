# 司法鉴定流程系统第一期设计稿

> 本文档用于独立开发一个新的司法鉴定流程管理系统。系统不与当前通达 OA 项目直接耦合，但在操作逻辑和页面风格上参考通达 OA。

---

## 1. 项目定位

### 1.1 产品定位

本系统是一个面向内部工作人员的司法鉴定流程管理系统，核心目标是围绕司法鉴定案件，实现从案件登记、受理、办理、复核、文书签发、材料/鉴材/文书管理到归档结束的内部流程闭环。

系统不是通用 OA，也不是通用低代码流程平台。第一期只聚焦司法鉴定业务主流程。

### 1.2 核心原则

- 以案件为中心，而不是以抽象流程为中心。
- 底层保留清晰的流程、任务、台账、审计模型。
- 操作逻辑参考通达 OA，保持列表驱动、高信息密度、办理动作集中。
- 页面风格参考通达 OA，但结构更清晰，业务更聚焦。
- 第一阶段只做内部使用，不做外部委托方入口。
- 所有关键业务动作必须留痕。
- 不支持作废抹除历史。

### 1.3 第一期不做的内容

- 考勤
- 会议
- 门户
- 邮件
- BBS
- 外部委托方门户
- 外部系统集成
- 通用流程设计器
- 通用表单设计器
- 复杂 BI 大屏
- 多机构协同
- 移动端完整版本

---

## 2. 已确认的业务决策

### 2.1 系统形态

- 产品形态：案件中心型系统
- 技术分层：案件域 + 流程域 + 任务域 + 台账域 + 审计域
- 第一阶段流程能力：半配置化
- 主流程：固定司法鉴定主流程
- 子流程：少量受控子流程

### 2.2 流程与任务模式

- 一个案件有一条主流程。
- 一个案件允许挂少量受控子流程。
- 大多数节点单人办理。
- 少量节点支持多人候选、认领办理。
- 个别节点支持多人并行后汇总。

### 2.3 权限模式

- 部门 + 岗位双驱动。
- 局部节点允许人工指定具体承办人。
- 第一阶段不做复杂权限表达式。

### 2.4 材料 / 文书 / 鉴材管理深度

第一期采用结构化台账级管理：

- 材料、文书、鉴材分别建台账。
- 附件只是文件承载，不等同于业务对象。
- 需要记录接收、退回、流转、当前状态。

### 2.5 逆向动作规则

- 撤回：允许。
- 重开：允许。
- 作废：不允许。
- 终止后再发起：重新生成新案件。

### 2.6 外部人员参与

第一期不允许委托方或外部人员登录系统。外部材料由内部人员录入。

### 2.7 文书策略

第一期采用混合型：

- 支持模板生成文书。
- 支持外部上传补充文书。
- 最终统一进入文书台账。

---

## 3. 推荐技术栈

### 3.1 前端

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Element Plus
- Axios
- Day.js
- ECharts
- LogicFlow 或 AntV X6，用于后续流程展示

### 3.2 后端

- Spring Boot
- Spring Security
- MyBatis-Plus
- MySQL
- Redis
- MinIO
- Quartz 或 Spring Scheduler
- MapStruct
- Jackson

### 3.3 流程实现策略

第一期不建议直接上通用 BPM 引擎。推荐自研受控状态机：

- 主流程骨架固定。
- 节点规则可配置。
- 分支规则可配置。
- 角色/岗位/部门规则可配置。
- 子流程类型固定。
- 动作统一通过后端状态机校验。

后续如果流程种类明显增加，再考虑 Flowable、Camunda 或自研 DSL。

---

## 4. 系统模块划分

### 4.1 前端模块

1. 登录与主框架
2. 工作台
3. 案件中心
4. 待办办理
5. 台账中心
6. 系统配置

### 4.2 后端模块

1. auth：登录、会话、权限校验
2. organization：用户、部门、岗位、角色
3. case：案件主数据、当事人、鉴定事项
4. workflow：流程定义、节点规则、状态机、流程实例
5. task：待办、认领、办理、并行任务、已办
6. ledger：材料、鉴材、文书、附件、流转记录
7. template：文书模板、模板渲染、文书生成
8. audit：操作日志、状态日志、流程轨迹
9. common：字典、枚举、异常、响应结构

---

## 5. 第一阶段页面清单

### 5.1 通用页

1. 登录页
2. 主框架页 Layout
3. 无权限页 / 404 页

### 5.2 工作台

4. 工作台首页

### 5.3 案件中心

5. 案件列表页
6. 新建案件页
7. 编辑草稿案件页
8. 案件详情主页

### 5.4 办理中心

9. 我的待办页
10. 我的已办页
11. 任务办理页 / 办理弹窗
12. 我发起 / 参与的案件页

### 5.5 台账中心

13. 材料台账页
14. 鉴材台账页
15. 文书台账页

### 5.6 配置中心

16. 用户管理页
17. 部门岗位管理页
18. 节点规则配置页
19. 文书模板管理页

---

## 6. 页面设计说明

### 6.1 工作台首页

目标：用户登录后快速看到自己要处理什么。

主要区块：

- 我的待办数量
- 待认领数量
- 待补正数量
- 已超时数量
- 我的待办列表
- 最近处理案件
- 预警区
- 快捷新建案件入口

风格：参考通达 OA，信息密度高，操作直达，不做大屏化。

### 6.2 案件列表页

目标：案件管理主入口。

筛选条件：

- 案件编号
- 案件标题
- 委托单位
- 当前状态
- 当前节点
- 承办部门
- 当前承办人
- 创建时间区间

列表字段：

- 案件编号
- 案件标题
- 委托单位
- 当前状态
- 当前节点
- 当前承办人
- 创建时间
- 截止时间
- 超时标识
- 操作

### 6.3 新建案件页

主要区块：

- 基本信息
- 委托信息
- 当事人信息
- 鉴定事项
- 初始材料
- 底部操作栏

操作：

- 保存草稿
- 提交
- 取消

规则：

- 草稿可反复编辑。
- 正式提交后进入受理流程。
- 案件编号建议在提交时生成。

### 6.4 案件详情主页

这是系统核心页面。

顶部摘要区：

- 案件编号
- 案件标题
- 当前状态
- 当前节点
- 当前承办人
- 委托单位
- 创建时间
- 是否加急
- 是否超时

操作栏：

- 办理
- 指派
- 撤回
- 重开
- 终止
- 新增文书
- 上传材料
- 查看轨迹

主 Tab：

1. 案件概况
2. 流程办理
3. 材料台账
4. 鉴材台账
5. 文书台账
6. 流程轨迹

### 6.5 任务办理页 / 办理弹窗

主要区块：

- 案件简要信息
- 当前节点说明
- 办理意见输入
- 附件补充
- 动作按钮区

动作：

- 通过
- 退回
- 补正
- 发起补充材料
- 指派
- 终止
- 保存草稿

规则：

- 动作必须由后端校验。
- 退回、终止、撤回、重开必须填写原因。
- 所有动作必须写日志。

---

## 7. 核心业务状态

### 7.1 案件状态

建议第一期使用以下案件状态：

- DRAFT：草稿
- TO_ACCEPT：待受理
- ACCEPT_REVIEWING：受理审核中
- REJECTED_ACCEPTANCE：不受理
- CORRECTION_PENDING：待补正 / 待补充
- PROCESSING：办理中
- REVIEWING：复核 / 审核中
- DOC_ISSUING：文书签发中
- COMPLETED：已办结
- ARCHIVED：已归档
- TERMINATED：已终止

### 7.2 流程状态

- running
- completed
- terminated
- withdrawn
- reopened

### 7.3 任务状态

- pending
- claimed
- processing
- completed
- cancelled

---

## 8. 流程动作模型

### 8.1 推进类动作

- submit
- approve
- pass
- complete
- issue

### 8.2 控制类动作

- return
- withdraw
- terminate
- reopen

### 8.3 协同类动作

- assign
- claim
- transfer
- add_sign

第一期建议重点实现：

- assign
- claim

transfer 和 add_sign 可后置。

### 8.4 子流程触发动作

- start_correction
- start_material_supplement
- start_doc_issue
- start_rework

---

## 9. 第一阶段数据库表设计初稿

### 9.1 组织权限表

- sys_user
- sys_dept
- sys_post
- sys_role
- sys_user_role

### 9.2 案件核心表

- case_info
- case_party
- case_appraisal_item

### 9.3 流程定义表

- wf_definition
- wf_node_def
- wf_transition_def

### 9.4 流程实例表

- case_wf_instance
- case_subflow_instance
- case_node_instance

### 9.5 任务办理表

- case_task
- case_task_candidate
- case_task_opinion

### 9.6 台账表

- case_material
- case_evidence
- case_document
- doc_template

### 9.7 文件表

- sys_file
- case_attachment

### 9.8 日志审计表

- case_transfer_log
- case_action_log
- case_status_log
- case_withdraw_log
- case_reopen_log

### 9.9 字典表

- sys_dict_type
- sys_dict_item

---

## 10. 第一阶段接口清单：按页面倒推

## 10.1 通用约定

### 统一响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

### 分页响应结构

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [],
    "total": 0,
    "pageNo": 1,
    "pageSize": 20
  }
}
```

### 认证方式

第一期建议使用：

- 登录后返回 token
- 前端通过 Authorization Header 携带 token
- 后端使用 Spring Security 校验

---

## 10.2 登录与当前用户接口

### POST `/api/auth/login`

用途：用户登录。

请求：

```json
{
  "username": "admin",
  "password": "password"
}
```

响应：

```json
{
  "token": "xxx",
  "user": {
    "id": 1,
    "username": "admin",
    "realName": "管理员"
  }
}
```

### POST `/api/auth/logout`

用途：退出登录。

### GET `/api/auth/me`

用途：获取当前用户信息。

### GET `/api/auth/menus`

用途：获取当前用户可见菜单。

---

## 10.3 工作台接口

### GET `/api/workbench/summary`

用途：获取工作台统计数据。

返回：

```json
{
  "todoCount": 12,
  "candidateCount": 3,
  "correctionCount": 2,
  "overtimeCount": 1
}
```

### GET `/api/workbench/todos`

用途：获取工作台最近待办。

参数：

- limit

### GET `/api/workbench/recent-cases`

用途：获取最近处理或查看的案件。

### GET `/api/workbench/warnings`

用途：获取预警列表。

预警类型：

- overtime
- near_overtime
- material_missing
- correction_pending
- doc_pending

---

## 10.4 案件接口

### POST `/api/cases`

用途：新建案件草稿。

请求包含：

- 基本信息
- 委托信息
- 当事人信息
- 鉴定事项
- 初始材料

### PUT `/api/cases/{caseId}`

用途：更新草稿案件。

### POST `/api/cases/{caseId}/submit`

用途：提交案件进入流程。

说明：也可以统一走 `/api/cases/{caseId}/actions`，但新建提交作为高频动作可保留独立接口。

### GET `/api/cases/{caseId}`

用途：获取案件详情主页数据。

建议返回聚合数据：

- 案件基本信息
- 当前状态
- 当前节点
- 当前任务摘要
- 材料统计
- 鉴材统计
- 文书统计
- 可执行动作

### GET `/api/cases`

用途：案件列表查询。

参数：

- caseNo
- caseTitle
- entrustOrgName
- currentStatus
- currentNodeCode
- acceptDeptId
- currentHandlerId
- createdStart
- createdEnd
- pageNo
- pageSize

### GET `/api/cases/{caseId}/overview`

用途：获取案件概况 Tab 数据。

### GET `/api/cases/{caseId}/actions/available`

用途：获取当前用户对该案件可执行动作。

返回示例：

```json
[
  {
    "actionCode": "approve",
    "actionName": "通过",
    "requireReason": false
  },
  {
    "actionCode": "return",
    "actionName": "退回",
    "requireReason": true
  }
]
```

---

## 10.5 统一流程动作接口

### POST `/api/cases/{caseId}/actions`

用途：统一处理案件流程动作。

支持动作：

- submit
- approve
- return
- withdraw
- terminate
- reopen
- assign
- claim
- start_correction
- start_material_supplement
- start_doc_issue
- start_rework

请求：

```json
{
  "taskId": 1001,
  "actionCode": "approve",
  "opinion": "同意",
  "reason": "",
  "payload": {}
}
```

说明：

后端统一负责：

1. 校验当前用户权限
2. 校验任务状态
3. 校验动作是否合法
4. 更新流程实例和节点实例
5. 生成或取消任务
6. 更新案件摘要状态
7. 写入操作日志和状态日志

---

## 10.6 任务接口

### GET `/api/tasks/todo`

用途：获取我的待办。

参数：

- caseNo
- nodeCode
- taskType
- overtimeFlag
- pageNo
- pageSize

### GET `/api/tasks/done`

用途：获取我的已办。

### GET `/api/tasks/candidate`

用途：获取我的候选任务。

### GET `/api/tasks/{taskId}`

用途：获取任务详情。

返回：

- 任务信息
- 案件摘要
- 当前节点说明
- 可执行动作
- 历史意见

### POST `/api/tasks/{taskId}/claim`

用途：认领候选任务。

也可以内部转为统一 Action：`claim`。

### GET `/api/cases/{caseId}/tasks`

用途：获取案件任务记录。

---

## 10.7 流程轨迹接口

### GET `/api/cases/{caseId}/timeline`

用途：获取案件流程时间轴。

返回内容：

- 节点名称
- 操作人
- 操作动作
- 操作时间
- 办理意见
- 子流程标识

### GET `/api/cases/{caseId}/workflow-instances`

用途：获取案件主流程和子流程实例。

### GET `/api/cases/{caseId}/node-instances`

用途：获取案件节点实例记录。

---

## 10.8 材料台账接口

### GET `/api/cases/{caseId}/materials`

用途：获取案件材料列表。

### POST `/api/cases/{caseId}/materials`

用途：新增材料。

### PUT `/api/materials/{materialId}`

用途：更新材料信息。

### POST `/api/materials/{materialId}/attachments`

用途：上传材料附件。

### POST `/api/materials/{materialId}/transfer`

用途：登记材料流转。

### GET `/api/materials`

用途：材料台账全局查询。

### GET `/api/materials/{materialId}/transfer-logs`

用途：获取材料流转记录。

---

## 10.9 鉴材台账接口

### GET `/api/cases/{caseId}/evidences`

用途：获取案件鉴材列表。

### POST `/api/cases/{caseId}/evidences`

用途：新增鉴材。

### PUT `/api/evidences/{evidenceId}`

用途：更新鉴材信息。

### POST `/api/evidences/{evidenceId}/transfer`

用途：登记鉴材流转。

### POST `/api/evidences/{evidenceId}/return`

用途：登记鉴材归还。

### GET `/api/evidences`

用途：鉴材台账全局查询。

### GET `/api/evidences/{evidenceId}/transfer-logs`

用途：获取鉴材流转记录。

---

## 10.10 文书台账接口

### GET `/api/cases/{caseId}/documents`

用途：获取案件文书列表。

### POST `/api/cases/{caseId}/documents/upload`

用途：上传外部文书。

### POST `/api/cases/{caseId}/documents/generate`

用途：根据模板生成文书。

请求：

```json
{
  "templateId": 1,
  "docType": "appraisal_report",
  "variables": {}
}
```

### PUT `/api/documents/{documentId}`

用途：更新文书元信息。

### POST `/api/documents/{documentId}/submit-review`

用途：提交文书签发流程。

### GET `/api/documents`

用途：文书台账全局查询。

### GET `/api/documents/{documentId}`

用途：获取文书详情。

### GET `/api/documents/{documentId}/versions`

用途：获取文书版本记录。

---

## 10.11 文件接口

### POST `/api/files/upload`

用途：上传文件到 MinIO。

返回：

```json
{
  "fileId": 1,
  "originalName": "材料.pdf",
  "fileSize": 123456,
  "storageKey": "xxx"
}
```

### GET `/api/files/{fileId}/download`

用途：下载文件。

### GET `/api/files/{fileId}/preview`

用途：预览文件。

第一期预览可只支持 PDF 和图片。

---

## 10.12 配置接口

### 用户管理

- GET `/api/admin/users`
- POST `/api/admin/users`
- PUT `/api/admin/users/{userId}`
- POST `/api/admin/users/{userId}/disable`
- POST `/api/admin/users/{userId}/enable`

### 部门管理

- GET `/api/admin/depts/tree`
- POST `/api/admin/depts`
- PUT `/api/admin/depts/{deptId}`

### 岗位管理

- GET `/api/admin/posts`
- POST `/api/admin/posts`
- PUT `/api/admin/posts/{postId}`

### 节点规则配置

- GET `/api/admin/workflows/{wfId}/nodes`
- PUT `/api/admin/workflows/{wfId}/nodes/{nodeId}`
- GET `/api/admin/workflows/{wfId}/transitions`
- PUT `/api/admin/workflows/{wfId}/transitions/{transitionId}`

### 文书模板配置

- GET `/api/admin/doc-templates`
- POST `/api/admin/doc-templates`
- PUT `/api/admin/doc-templates/{templateId}`
- POST `/api/admin/doc-templates/{templateId}/enable`
- POST `/api/admin/doc-templates/{templateId}/disable`

---

## 11. 接口设计原则

### 11.1 流程动作必须统一入口

核心流转动作统一走：

```http
POST /api/cases/{caseId}/actions
```

避免每个动作散落成不同接口，导致状态机不可控。

### 11.2 页面查询接口可以适度聚合

案件详情页可以使用聚合接口，避免前端一次打开页面调用过多接口。

### 11.3 台账接口独立

材料、鉴材、文书是业务对象，不要全部归为附件。

### 11.4 所有关键写操作必须写日志

包括：

- 流程动作
- 状态变更
- 材料流转
- 鉴材流转
- 文书签发
- 撤回
- 重开
- 终止

### 11.5 前端只控制展示，后端控制规则

前端可以隐藏不可用按钮，但后端必须再次校验权限、状态和动作合法性。

---

## 12. 分阶段实施建议

### Phase 0：需求固化与流程拆解

周期：1～2 周

产出：

- 节点清单
- 动作清单
- 状态清单
- 角色清单
- 页面清单
- 字段清单
- 文书清单

### Phase 1：最小闭环版

周期：6～10 周

范围：

- 登录
- 用户/部门/岗位
- 案件新建
- 案件列表
- 案件详情主页第一版
- 主流程流转
- 我的待办 / 已办
- 基础材料附件
- 基础流程轨迹

### Phase 2：业务可用版

周期：8～12 周

范围：

- 补正子流程
- 补充材料子流程
- 文书签发子流程
- 退回重办子流程
- 材料台账
- 鉴材台账
- 文书台账
- 撤回
- 重开
- 候选任务
- 局部并行任务

### Phase 3：稳定运营版

周期：4～8 周

范围：

- 超时预警
- 首页预警增强
- 统计查询
- 审计报表
- 归档增强
- 文书模板增强
- 配置后台增强

---

## 13. 后续建议

建议下一步继续产出：

1. 详细数据库字段类型与建表 SQL
2. 接口 OpenAPI 草案
3. 核心状态机伪代码
4. 页面低保真原型
5. 第一阶段开发任务拆分

优先级建议：

1. 状态机伪代码
2. 建表 SQL
3. OpenAPI 草案
4. 页面低保真原型
5. 开发任务拆分
