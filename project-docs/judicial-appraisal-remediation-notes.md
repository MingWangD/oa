# 司法鉴定 OA 流程与表单整改说明

更新时间：2026-06-16

## 1. 本轮整改目标

本轮整改优先解决真实页面链路中的三类问题：表单保存和回显、流程待办流转、权限边界。核心验收对象是“收到委托书”主流程：用户从新建工作进入表单，填写后可以先保存不流转，也可以提交流转；再次从待办、办结或工作查询进入时，表单数据应完整回显。

关键口径：

- 保存表单只更新案件表单数据，不创建任务、不推进流程。
- 提交流转继续使用 `/api/cases/{caseId}/actions`，并把 `formData` 合并到 `case_info.form_data`。
- 草稿创建人、当前主办人、候选任务用户或候选角色用户可以保存；无关用户不能越权保存。
- “我的工作”以当前登录用户为准，不信任前端传入的任意 `assigneeId`。

## 2. 已落地实现

- 新增 `PUT /api/cases/{caseId}/form-data`，用于保存案件表单草稿。
- 前端案件办理页新增“保存表单”按钮，保存成功后提示“流程未流转”并刷新表单数据。
- 保存时同步案件摘要字段：`caseNo`/`projectNo`、`flowName`/`caseTitle`/`projectName`、`entrustOrgName`/`clientName`。
- 增加 `CaseInfoServiceTests`，覆盖草稿创建人保存、候选任务用户保存、无关用户禁止保存。

## 3. 主流程复现与验收清单

| 类别 | 操作 | 预期结果 | 核查点 |
|---|---|---|---|
| 表单保存 | 新建“收到委托书”草稿，填写后点击“保存表单” | 页面提示保存成功，流程不流转 | `case_info.form_data` 有数据，任务不推进 |
| 表单提交 | 点击“提交流转” | 当前节点完成或流程启动，下一待办生成 | 请求体包含 `formData`、`actionCode`、`taskId` |
| 表单回显 | 从待办、办结、工作查询打开同一案件 | 字段展示一致 | 案件详情接口返回 `formData` |
| 权限隔离 | 无关账号尝试保存或办理 | 被后端拒绝或页面不可操作 | 返回 403 或提示无权 |
| 我的工作 | 切换下一节点角色账号 | 待办出现于当前用户列表 | `case_task` 与 `case_task_candidate` 匹配 |

## 4. 收到委托书主链路

| 顺序 | 节点 | 角色 | 页面动作 | 核心数据 |
|---:|---|---|---|---|
| 1 | 新建草稿 | 可发起用户 | 新建“收到委托书” | `case_info` 草稿 |
| 2 | 发起者填写委托信息 | 发起者或候选发起角色 | 填表、保存或提交 | `case_info.form_data` |
| 3 | 收案员登记 | `case_acceptor` | 登记材料和收案信息 | `case_task`、节点表单快照 |
| 4 | 部门负责人审核 | `dept_leader` | 判断是否受理 | 条件字段如 `accepted` |
| 5 | 项目负责人确认 | `project_leader` | 判断初勘、材料、后续流程 | 条件字段如 `preliminarySurveyRequired` |
| 6 | 后续子流程 | 对应业务角色 | 缴费、质控、勘验、文书、归档 | `case_subflow_instance`、归档记录 |

## 5. 数据库表用途

### 案件主数据

| 表名 | 用途 |
|---|---|
| `case_info` | 案件主表，保存案件编号、标题、状态、当前节点、当前处理人和汇总表单数据。 |
| `case_party` | 案件当事人。 |
| `case_material` | 案件材料台账。 |
| `case_evidence` | 案件鉴材台账。 |
| `case_appraisal_item` | 案件鉴定事项。 |
| `case_document` / `case_document_version` | 文书台账和版本。 |
| `case_attachment` | 案件附件关系。 |

### 流程运行

| 表名 | 用途 |
|---|---|
| `case_wf_instance` | 主流程实例。 |
| `case_subflow_instance` | 子流程实例。 |
| `case_node_instance` | 节点实例和节点级表单快照。 |
| `case_task` | 待办和已办任务，“我的工作”的核心来源。 |
| `case_task_candidate` | 候选人、候选角色、候选部门和候选岗位范围。 |
| `case_task_opinion` | 办理意见。 |
| `case_transfer_log` | 流转日志。 |
| `case_status_log` / `case_action_log` | 状态和操作日志。 |
| `case_reopen_log` / `case_withdraw_log` | 重开和撤回记录。 |

### 流程与表单配置

| 表名 | 用途 |
|---|---|
| `wf_definition` | 流程定义。 |
| `wf_node_def` | 节点定义和处理角色规则。 |
| `wf_transition_def` | 节点流转、条件表达式和子流程触发配置。 |
| `form_definition` | 表单定义。 |
| `form_version` | 表单字段、校验、附件和布局版本。 |
| `doc_template` | 文书模板。 |

### 权限组织

| 表名 | 用途 |
|---|---|
| `sys_user` / `sys_role` / `sys_user_role` | 用户、角色和用户角色绑定。 |
| `sys_dept` / `sys_post` | 部门和岗位。 |
| `sys_menu` / `sys_role_menu` | 菜单和角色菜单权限。 |
| `sys_role_data_scope_dept` | 自定义数据权限部门范围。 |
| `sys_dict_type` / `sys_dict_item` | 字典类型和字典项。 |

### 附件与归档

| 表名 | 用途 |
|---|---|
| `sys_file` / `file_version` | 上传文件和文件版本。 |
| `case_archive_record` | 案件自动归档记录。 |
| `knowledge_directory` / `knowledge_document` | 知识库目录和文档。 |
| `knowledge_document_version` / `knowledge_permission` | 知识库文档版本和权限。 |

### 业务辅助

| 表名 | 用途 |
|---|---|
| `audit_event` | 审计事件。 |
| `ledger_transfer_log` | 台账流转日志。 |
| `contract_info` / `contract_version` / `contract_attachment` | 合同主数据、版本和附件。 |

## 6. 初始化与测试建议

1. 重建 `judicial_appraisal` 数据库。
2. 导入 `judicial-appraisal-backend/src/main/resources/db/judicial_appraisal_full_dump.sql`。
3. 使用默认账号登录，优先测试 `case_acceptor`、`dept_leader`、`project_leader`、`project_assistant`、`archivist`、`finance`。
4. 先跑“收到委托书”主链路，再补初步勘验、缴费通知、质量控制、现场勘验、材料接收、送审稿、出具意见书、用章和归档。
5. 自动化测试中，优先使用隔离数据库或串行执行涉及 `sys_role`、`wf_definition`、`form_version` 写入的集成测试，避免共享库死锁。
