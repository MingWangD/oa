# 司法鉴定后端接口现状说明

更新时间：2026-05-21

本文只描述当前后端代码已经实现的接口行为，作为 `docs/judicial-appraisal-workflow-design.md` 的实现对齐补充。设计文档里更完整的流程设想、筛选项和聚合返回，不代表当前代码都已落地。

## 1. 通用说明

### 1.1 返回包裹结构

当前接口统一返回 `ApiResponse<T>`：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | `0` 表示成功；业务异常通常返回 `400`；未处理异常返回 `500`。 |
| `message` | string | 成功时固定为 `success`；失败时为异常消息。 |
| `data` | object/array/null | 业务数据。 |

### 1.2 时间字段

代码中使用 `LocalDateTime`，序列化时按 ISO-8601 输出，例如：

```json
"2026-05-21T09:30:00"
```

### 1.3 当前实现成熟度

| 接口 | 当前状态 | 说明 |
| --- | --- | --- |
| `GET /api/cases` | 基础实现 | 仅支持标题关键字、状态、受理部门、当前处理人四类筛选。 |
| `POST /api/cases/{caseId}/submit` | 最小实现 | 固定进入主流程第一个节点，首个任务直接指派给提交人。 |
| `POST /api/cases/{caseId}/actions` | 最小实现 | 只有部分 `actionCode` 真正驱动流转，其他动作当前只会把当前任务办结。 |
| `GET /api/tasks/todo` | 基础实现 | 仅按 `assigneeId` 可选过滤，不分页。 |
| `GET /api/tasks/done` | 基础实现 | 仅按 `assigneeId` 可选过滤，不分页。 |
| `GET /api/tasks/{taskId}` | 基础实现 | 返回任务明细并补齐案件标题、案件编号、流程名称。 |
| `GET /api/tasks` | 基础实现 | 通过 `caseId + nodeCode` 取最新一条任务记录。 |
| `GET /api/workbench/summary` | 最小实现 | `processingCount` 当前与 `todoCount` 使用同一套计数条件。 |
| `GET /api/workbench/todo` | 基础实现 | 返回待办任务列表，按截止时间升序。 |
| `GET /api/workbench/done` | 最小实现 | 只返回最近 20 条已办任务，不分页。 |

## 2. 案件接口

### 2.1 GET `/api/cases`

用途：分页查询案件列表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | query | string | 否 | 仅对 `case_title` 做模糊匹配。 |
| `caseStatus` | query | string | 否 | 案件状态，按枚举值精确匹配。 |
| `acceptDeptId` | query | long | 否 | 受理部门 ID，精确匹配。 |
| `currentHandlerId` | query | long | 否 | 当前处理人 ID，精确匹配。 |
| `pageNo` | query | integer | 否 | 页码，默认 `1`。小于 `1` 时也会回退到 `1`。 |
| `pageSize` | query | integer | 否 | 每页条数，默认 `10`。小于 `1` 时也会回退到 `10`。 |

示例请求：

```http
GET /api/cases?keyword=伤情&caseStatus=TO_ACCEPT&acceptDeptId=10&currentHandlerId=2001&pageNo=1&pageSize=10
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "records": [
      {
        "id": 101,
        "caseNo": "JA-101",
        "caseTitle": "张三伤情鉴定",
        "caseType": "INJURY",
        "caseStatus": "TO_ACCEPT",
        "caseStatusName": "待受理",
        "currentNodeCode": "ACCEPT_REVIEW",
        "currentNodeName": "受理审查",
        "currentHandlerId": 2001,
        "currentHandlerName": "王受理",
        "acceptDeptId": 10,
        "acceptDeptName": "法医鉴定室",
        "entrustOrgName": "XX公安分局",
        "deadlineTime": "2026-05-30T18:00:00",
        "urgentFlag": 0,
        "submittedTime": "2026-05-21T09:30:00",
        "completedTime": null,
        "createdTime": "2026-05-21T09:00:00"
      }
    ],
    "total": 1,
    "pageNo": 1,
    "pageSize": 10
  }
}
```

响应 `data` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `records` | array | 当前页案件列表。 |
| `total` | long | 总记录数。 |
| `pageNo` | long | 当前页码。 |
| `pageSize` | long | 当前页大小。 |

`records[]` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 案件 ID。 |
| `caseNo` | string/null | 案件编号。草稿在提交前可能为空。 |
| `caseTitle` | string | 案件标题。 |
| `caseType` | string/null | 案件类型。 |
| `caseStatus` | string/null | 案件状态枚举值。 |
| `caseStatusName` | string | 状态显示名称，由后端硬编码映射生成。 |
| `currentNodeCode` | string/null | 当前节点编码。 |
| `currentNodeName` | string/null | 当前节点名称。 |
| `currentHandlerId` | long/null | 当前处理人 ID。 |
| `currentHandlerName` | string/null | 当前处理人姓名。 |
| `acceptDeptId` | long/null | 受理部门 ID。 |
| `acceptDeptName` | string/null | 受理部门名称。 |
| `entrustOrgName` | string/null | 委托机构名称。 |
| `deadlineTime` | string/null | 截止时间。 |
| `urgentFlag` | integer/null | 紧急标记。 |
| `submittedTime` | string/null | 提交时间。 |
| `completedTime` | string/null | 办结时间。 |
| `createdTime` | string/null | 创建时间。 |

当前实现说明：

- 按 `id desc` 排序。
- `keyword` 只匹配 `caseTitle`，不会匹配 `caseNo`、委托单位等其他字段。
- 这是基础列表实现，仍未覆盖设计文档中的更多筛选条件和聚合字段。

### 2.2 POST `/api/cases/{caseId}/submit`

用途：提交草稿案件并启动主流程。

路径参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `caseId` | path | long | 是 | 案件 ID。 |

请求体字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `operatorId` | long | 是 | 提交人 ID。当前实现也把它作为首个任务处理人。 |
| `operatorName` | string | 否 | 提交人姓名。为空时后端会补成 `管理员`。 |
| `opinion` | string | 否 | 提交意见。 |

示例请求：

```http
POST /api/cases/101/submit
Content-Type: application/json
```

```json
{
  "operatorId": 2001,
  "operatorName": "王受理",
  "opinion": "提交受理"
}
```

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "caseId": 101,
    "taskId": 5001,
    "actionCode": "SUBMIT",
    "success": true,
    "message": "提交成功"
  }
}
```

响应 `data` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `caseId` | long | 案件 ID。 |
| `taskId` | long | 新创建的首个任务 ID。 |
| `actionCode` | string | 固定返回 `SUBMIT`。 |
| `success` | boolean | 当前实现成功时为 `true`。 |
| `message` | string | 业务结果消息。 |

当前实现说明：

- 只允许 `caseStatus = DRAFT` 的案件提交，否则返回业务异常。
- 会创建或复用一条运行中的主流程实例，主流程编码固定为 `JUDICIAL_MAIN`。
- 提交后固定进入 `ACCEPT_REVIEW` 节点，并创建一条待办任务。
- 如果案件 `caseNo` 为空，提交时会自动补成 `JA-{caseId}`。
- 这是最小实现：首节点、节点名称、流转顺序均写死在代码里，没有根据流程配置动态决定。

### 2.3 POST `/api/cases/{caseId}/actions`

用途：办理案件流程动作。

路径参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `caseId` | path | long | 是 | 案件 ID。 |

请求体字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `taskId` | long | 条件必填 | 除 `actionCode = SUBMIT` 外，其他动作都要求传入当前任务 ID。 |
| `actionCode` | string | 是 | 动作枚举值，必须是大写。 |
| `opinion` | string | 否 | 办理意见，会写入任务和节点实例的 `resultOpinion`。 |
| `reason` | string | 否 | 当前代码未使用，仅保留在请求模型中。 |
| `assigneeId` | long | 否 | 当前动作的操作人 ID；若产生下一任务，也会作为下一处理人 ID。 |
| `assigneeName` | string | 否 | 当前动作的操作人姓名；若产生下一任务，也会作为下一处理人姓名。为空时会回退为当前任务处理人，仍为空则补成 `管理员`。 |

示例请求：

```http
POST /api/cases/101/actions
Content-Type: application/json
```

```json
{
  "taskId": 5001,
  "actionCode": "APPROVE",
  "opinion": "同意进入鉴定办理",
  "assigneeId": 3001,
  "assigneeName": "李办理"
}
```

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "caseId": 101,
    "taskId": 5001,
    "actionCode": "APPROVE",
    "success": true,
    "message": "受理通过"
  }
}
```

响应 `data` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `caseId` | long | 案件 ID。 |
| `taskId` | long | 当前被办理的任务 ID。注意：不是下一条新任务的 ID。 |
| `actionCode` | string | 本次办理动作。 |
| `success` | boolean | 当前实现成功时为 `true`。 |
| `message` | string | 业务结果消息。 |

当前支持的动作与行为：

| `actionCode` | 当前行为 | 是否最小实现 |
| --- | --- | --- |
| `SUBMIT` | 直接走“提交案件”逻辑，忽略 `taskId`，使用 `assigneeId/assigneeName` 作为提交人。 | 是 |
| `APPROVE` | 真正驱动节点前进。 | 否 |
| `COMPLETE` | 与 `APPROVE` 完全同逻辑。 | 是 |
| `RETURN` | 当前任务办结后，新建 `PROCESSING` 节点任务，案件状态改为 `CORRECTION_PENDING`。 | 是 |
| `TERMINATE` | 当前任务办结后，案件状态改为 `TERMINATED`，结束运行中流程实例。 | 否 |
| `REOPEN` | 当前任务办结后，新建 `PROCESSING` 节点任务，案件状态改为 `PROCESSING`。 | 是 |
| `ASSIGN` / `CLAIM` / `WITHDRAW` / `START_CORRECTION` / `START_MATERIAL_SUPPLEMENT` / `START_DOC_ISSUE` / `START_REWORK` / `TRANSFER` / `ADD_SIGN` | 当前只会把现有任务和节点标记为完成，并记录 `resultAction` / `resultOpinion`，不会创建下一节点任务，也不会更新案件状态。 | 是 |

`APPROVE` / `COMPLETE` 的当前流转路径：

| 当前节点 | 下一案件状态 | 下一节点 |
| --- | --- | --- |
| `ACCEPT_REVIEW` | `PROCESSING` | `PROCESSING` |
| `PROCESSING` | `REVIEWING` | `REVIEW` |
| `REVIEW` | `DOC_ISSUING` | `DOC_ISSUE` |
| `DOC_ISSUE` | `ARCHIVED` | `ARCHIVE` |
| `ARCHIVE` | `COMPLETED` | 无，直接办结案件和流程实例 |

当前实现说明：

- 除 `SUBMIT` 外，后端会先把当前任务状态改为 `completed`，再根据动作决定是否创建下一节点和下一任务。
- 如果当前任务已经是 `completed`，接口会直接返回业务异常。
- 这是最小实现最集中的接口：大量动作码只是枚举已定义，但业务语义尚未真正落地。

## 3. 任务接口

### 3.1 GET `/api/tasks/todo`

用途：查询待办任务列表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `assigneeId` | query | long | 否 | 处理人 ID。不传时查询全部待办。 |

示例请求：

```http
GET /api/tasks/todo?assigneeId=3001
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 6001,
      "caseId": 101,
      "taskTitle": "张三伤情鉴定 - 鉴定办理",
      "nodeCode": "PROCESSING",
      "nodeName": "鉴定办理",
      "status": "pending",
      "assigneeId": 3001,
      "assigneeName": "李办理",
      "deadlineTime": "2026-05-30T18:00:00"
    }
  ]
}
```

`data[]` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 任务 ID。 |
| `caseId` | long | 案件 ID。 |
| `taskTitle` | string | 任务标题。 |
| `nodeCode` | string | 节点编码。 |
| `nodeName` | string | 节点名称。 |
| `status` | string | 任务状态。当前接口返回 `pending`、`claimed`、`processing` 三类。 |
| `assigneeId` | long/null | 处理人 ID。 |
| `assigneeName` | string/null | 处理人姓名。 |
| `deadlineTime` | string/null | 截止时间。 |

当前实现说明：

- 查询条件是 `status in ('pending', 'claimed', 'processing')`。
- 按 `id desc` 排序。
- 当前不分页，是基础实现。

### 3.2 GET `/api/tasks/done`

用途：查询已办任务列表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `assigneeId` | query | long | 否 | 处理人 ID。不传时查询全部已办。 |

示例请求：

```http
GET /api/tasks/done?assigneeId=3001
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 5001,
      "caseId": 101,
      "taskTitle": "张三伤情鉴定 - 受理审查",
      "nodeCode": "ACCEPT_REVIEW",
      "nodeName": "受理审查",
      "status": "completed",
      "assigneeId": 2001,
      "assigneeName": "王受理",
      "deadlineTime": "2026-05-30T18:00:00"
    }
  ]
}
```

字段说明同 `GET /api/tasks/todo`。

当前实现说明：

- 查询条件是 `status = 'completed'`。
- 按 `completedTime desc, id desc` 排序。
- 当前不分页，是基础实现。

### 3.3 GET `/api/tasks/{taskId}`

用途：查询单个任务明细。

路径参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `taskId` | path | long | 是 | 任务 ID。 |

示例请求：

```http
GET /api/tasks/6001
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 6001,
    "caseId": 101,
    "caseNo": "JA-101",
    "caseTitle": "张三伤情鉴定",
    "wfName": "司法鉴定主流程",
    "wfInstanceId": 7001,
    "nodeInstanceId": 8001,
    "taskType": "single",
    "taskTitle": "张三伤情鉴定 - 鉴定办理",
    "nodeCode": "PROCESSING",
    "nodeName": "鉴定办理",
    "status": "pending",
    "assigneeId": 3001,
    "assigneeName": "李办理",
    "startedTime": "2026-05-21T10:00:00",
    "completedTime": null,
    "deadlineTime": "2026-05-30T18:00:00",
    "overtimeFlag": 0,
    "resultAction": null,
    "resultOpinion": null
  }
}
```

响应 `data` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 任务 ID。 |
| `caseId` | long | 案件 ID。 |
| `caseNo` | string | 案件编号。案件不存在时返回空字符串。 |
| `caseTitle` | string | 案件标题。案件不存在时返回空字符串。 |
| `wfName` | string | 流程名称。流程实例不存在时返回空字符串。 |
| `wfInstanceId` | long | 流程实例 ID。 |
| `nodeInstanceId` | long | 节点实例 ID。 |
| `taskType` | string | 任务类型，当前创建任务时固定为 `single`。 |
| `taskTitle` | string | 任务标题。 |
| `nodeCode` | string | 节点编码。 |
| `nodeName` | string | 节点名称。 |
| `status` | string | 任务状态。 |
| `assigneeId` | long/null | 处理人 ID。 |
| `assigneeName` | string/null | 处理人姓名。 |
| `startedTime` | string/null | 开始时间。 |
| `completedTime` | string/null | 完成时间。 |
| `deadlineTime` | string/null | 截止时间。 |
| `overtimeFlag` | integer/null | 超时标记。 |
| `resultAction` | string/null | 最终动作码。 |
| `resultOpinion` | string/null | 最终意见。 |

当前实现说明：

- 任务不存在时返回业务异常 `任务不存在`。
- 当前只是“任务表 + 案件表 + 流程实例表”的拼装查询，没有额外聚合信息。

### 3.4 GET `/api/tasks`

用途：按案件和节点编码查询最新一条任务。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `caseId` | query | long | 是 | 案件 ID。 |
| `nodeCode` | query | string | 是 | 节点编码。 |

示例请求：

```http
GET /api/tasks?caseId=101&nodeCode=PROCESSING
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 6001,
    "caseId": 101,
    "caseNo": "JA-101",
    "caseTitle": "张三伤情鉴定",
    "wfName": "司法鉴定主流程",
    "wfInstanceId": 7001,
    "nodeInstanceId": 8001,
    "taskType": "single",
    "taskTitle": "张三伤情鉴定 - 鉴定办理",
    "nodeCode": "PROCESSING",
    "nodeName": "鉴定办理",
    "status": "pending",
    "assigneeId": 3001,
    "assigneeName": "李办理",
    "startedTime": "2026-05-21T10:00:00",
    "completedTime": null,
    "deadlineTime": "2026-05-30T18:00:00",
    "overtimeFlag": 0,
    "resultAction": null,
    "resultOpinion": null
  }
}
```

字段说明同 `GET /api/tasks/{taskId}`。

当前实现说明：

- 先按 `caseId`、`nodeCode` 精确匹配，再按 `id desc` 取最新一条。
- 没查到时返回业务异常 `任务不存在`。
- `caseId` 或 `nodeCode` 缺失时，当前全局异常处理不会给出专门的参数错误结构，通常会落到通用 `500` 响应，这也是当前实现的限制。

## 4. 工作台接口

### 4.1 GET `/api/workbench/summary`

用途：查询工作台摘要统计。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `assigneeId` | query | long | 否 | 处理人 ID。不传时按全部任务统计。 |

示例请求：

```http
GET /api/workbench/summary?assigneeId=3001
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "todoCount": 3,
    "doneCount": 8,
    "processingCount": 3,
    "overdueCount": 1
  }
}
```

响应 `data` 字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `todoCount` | long | 待办任务数，统计 `pending`、`claimed`、`processing`。 |
| `doneCount` | long | 已办任务数，统计 `completed`。 |
| `processingCount` | long | 当前实现与 `todoCount` 使用同一套查询条件，统计的是任务数，不是去重后的案件数。 |
| `overdueCount` | long | 截止时间早于当前时间的待办任务数。 |

当前实现说明：

- 这是最小实现。`processingCount` 虽然名称像“办理中案件数”，但当前代码实际上统计的是待办状态任务数。
- `overdueCount` 只看待办状态任务，并与当前系统时间比较。

### 4.2 GET `/api/workbench/todo`

用途：查询工作台待办列表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `assigneeId` | query | long | 否 | 处理人 ID。不传时查询全部待办。 |

示例请求：

```http
GET /api/workbench/todo?assigneeId=3001
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 6001,
      "caseId": 101,
      "taskTitle": "张三伤情鉴定 - 鉴定办理",
      "nodeCode": "PROCESSING",
      "nodeName": "鉴定办理",
      "status": "pending",
      "assigneeId": 3001,
      "assigneeName": "李办理",
      "deadlineTime": "2026-05-30T18:00:00"
    }
  ]
}
```

字段说明同 `GET /api/tasks/todo`。

当前实现说明：

- 查询条件与 `GET /api/tasks/todo` 相同，都是 `status in ('pending', 'claimed', 'processing')`。
- 排序不同：这里按 `deadlineTime asc, id desc` 返回，更适合工作台展示。

### 4.3 GET `/api/workbench/done`

用途：查询工作台已办列表。

请求参数：

| 参数 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `assigneeId` | query | long | 否 | 处理人 ID。不传时查询全部已办。 |

示例请求：

```http
GET /api/workbench/done?assigneeId=3001
```

请求体：无。

示例响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": 5001,
      "caseId": 101,
      "taskTitle": "张三伤情鉴定 - 受理审查",
      "nodeCode": "ACCEPT_REVIEW",
      "nodeName": "受理审查",
      "status": "completed",
      "assigneeId": 2001,
      "assigneeName": "王受理",
      "deadlineTime": "2026-05-30T18:00:00"
    }
  ]
}
```

字段说明同 `GET /api/tasks/done`。

当前实现说明：

- 查询条件是 `status = 'completed'`。
- 按 `completedTime desc` 排序。
- 当前固定 `limit 20`，没有分页，这是最小实现。

## 5. 状态与动作取值

### 5.1 案件状态

当前代码中已定义的案件状态枚举：

- `DRAFT`
- `TO_ACCEPT`
- `ACCEPT_REVIEWING`
- `REJECTED_ACCEPTANCE`
- `CORRECTION_PENDING`
- `PROCESSING`
- `REVIEWING`
- `DOC_ISSUING`
- `COMPLETED`
- `ARCHIVED`
- `TERMINATED`

说明：并不是所有状态都已在当前流转代码中实际走到，例如 `ACCEPT_REVIEWING`、`REJECTED_ACCEPTANCE` 当前未出现在上述接口的真实流转分支中。

### 5.2 任务状态

当前代码中已定义的任务状态枚举：

- `PENDING`
- `CLAIMED`
- `PROCESSING`
- `COMPLETED`
- `CANCELLED`

说明：数据库查询与接口返回使用的是小写字符串，例如 `pending`、`completed`。

### 5.3 动作枚举

当前代码中已定义的动作枚举：

- `SUBMIT`
- `APPROVE`
- `RETURN`
- `TERMINATE`
- `REOPEN`
- `ASSIGN`
- `CLAIM`
- `WITHDRAW`
- `START_CORRECTION`
- `START_MATERIAL_SUPPLEMENT`
- `START_DOC_ISSUE`
- `START_REWORK`
- `TRANSFER`
- `ADD_SIGN`
- `COMPLETE`

说明：动作枚举定义得比实际流转多；真实已落地的行为以本文 2.3 节为准。

## 6. 常见错误响应

### 6.1 业务异常

示例：

```json
{
  "code": 400,
  "message": "任务不存在",
  "data": null
}
```

常见触发场景：

- 按 `taskId` 或 `caseId + nodeCode` 查不到任务。
- 提交的案件不是 `DRAFT`。
- 办理动作时未传 `taskId`。
- 办理已经完成的任务。

### 6.2 参数校验异常

示例：

```json
{
  "code": 400,
  "message": "operatorId must not be null",
  "data": null
}
```

说明：

- `POST /api/cases/{caseId}/submit` 的 `operatorId` 使用了 `@NotNull`。
- `POST /api/cases/{caseId}/actions` 的 `actionCode` 使用了 `@NotNull`。
- 当前全局处理器只返回第一个字段错误。

### 6.3 未处理异常

示例：

```json
{
  "code": 500,
  "message": "系统异常",
  "data": null
}
```

说明：缺少必填 query 参数等未单独处理的异常，当前也可能落到这个响应。
