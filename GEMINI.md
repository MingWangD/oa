# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目重构。

## 📊 当前项目状态摘要 (2026-06-12)
- **阶段**：第四阶段已完成 20 流程 / 19 表单首轮高保真配置、自动结构验收、主链 happy path E2E 和关键运行时风险修复；第五阶段可以启动合同等业务域纵深开发，但不能视为已收官。
- **核心成果**：
    - **第四阶段进展**：
        - 已完成 20 个司法鉴定流程 / 19 个表单的高保真配置校准。
        - 自动验收接口 `GET /api/platform/judicial-catalog/verification` 返回 20/20 全部 Passed。
        - 后端运行时已通过 `ReceivedEntrustToArchiveE2ETest` 跑通主链 happy path E2E，验证了表单持久化、并行/汇聚网关、子流程挂起与父流程回写唤醒。
        - 已修复并行汇聚等待 `subflow_running`、后端认证上下文办理权限、下一任务候选角色规则、完成后 current node/current handler 清空、前端 `readOnly` 字段口径等关键运行时风险。
    - **平台底座**：RBAC、数据权限、动态表单/流程设计器、文件平台（MinIO）、知识库、审计与自动归档已全部就绪。
    - **第五阶段现状**：各业务域已有结构化看板与示例/派生台账，具备进入纵深开发（建表、真审批、真业务逻辑）的条件；合同管理已作为第一个最小闭环样板启动，但第五阶段不能视为完成。
- **合同管理 MVP 最新进展**：
    - 已新增真实合同表 `contract_info`、合同版本表 `contract_version`、合同附件关联表 `contract_attachment`，迁移脚本为 `judicial-appraisal-backend/src/main/resources/db/migration_v7_contract_mvp.sql`。
    - 后端已新增 `/api/contracts`，覆盖合同查询、详情、创建、修改、提交审批、部门审核通过、驳回；审批通过后会归档到知识库“合同档案”目录并记录审计日志。
    - 前端 `/placeholder/contract` 已切换为真实合同管理页，支持合同列表/详情、新建/编辑、上传或绑定附件、提交审批、审核通过/驳回。
    - 本轮验证：后端 `mvn test` 通过，`Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`；前端 `npm run build` 通过。
- **给 Gemini 的直接接手入口**：
    - 当前 in-app browser 已打开：`http://localhost:5173/placeholder/contract`，页面可正常渲染合同管理 MVP。
    - 本机后端 `8080`、前端 `5173` 正在运行；本地 MySQL 使用 `localhost:3307 / judicial_appraisal / root / 123456`。
    - Codex 已在本地执行 `migration_v7_contract_mvp.sql`，当前库已具备合同三张表；其它机器或重建库时仍需手动执行该迁移。
    - 交接时不要回退 `contract` 包、`KnowledgeService.archiveBusinessDocument`、`ContractManagementView.vue`、`judicial.ts` 合同 API、`router/index.ts` 合同路由。
    - 合同 MVP 当前使用轻量状态机，不是完整合同工作流；后续若接入动态流程引擎，应在现有 MVP 上增量演进，不要把已可运行闭环拆回占位页。
- `crm`、`contract`、`project` 已支持关键词查询、状态筛选和详情抽屉；后续可继续补联系人、合同金额、项目里程碑和详情页。
- `contract`、`project` 台账行现已带案件详情跳转路径，模块页可以直接回到真实案件办理页。
- `crm` 客户聚合行也已接入最近案件跳转，`CRM / 合同 / 项目` 三条主台账现在都支持从总览回到真实办理对象。
- `crm`、`contract`、`project` 的详情事实已进一步补厚：客户分级/建议跟进时间、合同编号草案/签约窗口、项目里程碑/下一检查点已可直接展示。
- 从模块页进入案件详情后，现已支持返回来源模块；`工作查询` 的筛选条件与页码也已写入路由查询参数，方便往返保留上下文。
- `CRM / 合同 / 项目` 台账行已新增“查看相关清单”动作，可直接按当前客户/合同/项目线索钻到工作查询列表。
- 案件详情已新增“查看同单位案件 / 查看同状态案件”，`工作查询` 也支持返回来源页面，关联浏览链已进一步打通。
- `archive / system-permission / system-log` 已补到第二层：支持状态筛选，并分别打通知识库、用户管理、案件详情的真实跳转。
- 第五阶段相关入口已全部接入统一结构化页面体系：快捷菜单、个人事务、应用中心、业务管理、行政办公、督查/门户/报表、人资/考勤/公文/档案、集成平台、系统管理。
    - `system-datasource` 已升级为实时运行态看板，可直接查看 MySQL / Redis / MinIO 的配置摘要与可用状态。
    - 最新完整验证：后端 `mvn test` 通过，前端 `npm run build` 通过。
    - 最新本地联调补充：当前仓库代码已在 `8081` 后端实例上验证登录、台账、案件创建/提交/查询/详情、工作台和子流程查询；若旧库缺少 `case_subflow_instance` 补充列，需要先执行 `migration_v5_subflow_relation.sql`。
    - 前端 `vite.config.ts` 已支持 `VITE_API_PROXY_TARGET`，可将本地前端临时指向指定后端实例进行联调。

## 📝 Gemini CLI 历史完成的工作
1. **RBAC 实施**：重构了系统的鉴权体系，在 `JwtTokenService` 注入角色与权限，由 `CustomDataPermissionHandler` 提供行级数据权限底层支撑。
2. **菜单动态化**：将前端硬编码的菜单抽取到后端通过 `sys_menu` 管理，并提供树状数据结构供 Vue Router 和 Element Plus 渲染。
3. **安全更新**：移除了手动的 `requireAdmin`，代之以更标准的 Spring Security 注解鉴权。
4. **高保真校准**：完成了 20 个司法鉴定流程的首轮高保真表单与流程配置，并补充了测试。

## 📊 第四阶段 E2E 验收证据清单 (🚨 核心收口)

**结论**：后端运行时已跑通司法鉴定核心主链路 happy path E2E，并补齐关键运行时风险；第四阶段仍需继续做 20 流程逐条运行级验收、异常路径、角色权限边界和前端旧 OA 等价体验。

### 1. 测试基本信息
- **测试类完整路径**：`com.example.judicialappraisal.workflow.service.ReceivedEntrustToArchiveE2ETest`
- **测试方法名**：`receivedEntrustToArchive_shouldCompleteFullBusinessChain()`
- **实际运行命令**：`mvn test -Dtest=ReceivedEntrustToArchiveE2ETest`
- **全量测试结果摘要**：最新 `mvn test` 为 `Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`；其中司法鉴定主链 E2E 持续通过。

### 2. 业务链路与断言覆盖
测试完整模拟了一个高仿真司法鉴定案件的生命周期：
- **完整链路**：`received-entrust` (主流程) -> `payment-notice` (子) -> `quality-control` (子) -> `material-receive-return` (子) -> `draft-opinion-review` (子) -> `issue-draft-opinion` (子) -> `final-opinion-review` (子) -> `issue-opinion` (**含并行盖章/开票**) -> `archive` (子) -> **COMPLETED** (结案)。

**关键断言代码位置 (Evidence)**：
- **无残留 active task / running subflow**：
  ```java
  long activeTasks = caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
          .eq(CaseTask::getCaseId, caseId)
          .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running"));
  assertThat(activeTasks).isEqualTo(0L);

  long activeSubflows = caseSubflowInstanceMapper.selectCount(new LambdaQueryWrapper<CaseSubflowInstance>()
          .eq(CaseSubflowInstance::getCaseId, caseId)
          .eq(CaseSubflowInstance::getStatus, "running"));
  assertThat(activeSubflows).isEqualTo(0L);
  ```
- **并行任务分发与 Inclusive 汇聚 (`issue-opinion`)**：
  ```java
  // Parallel gateway split -> should have SEALED_UPLOAD and FINANCE_INVOICE active
  completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedOpinionUploaded", true));
  completeTask(caseId, "FINANCE_INVOICE", Map.of("invoiceIssued", true));

  // Inclusive Join: Both parallel tasks must be completed before DELIVERY_ARCHIVE appears
  completeTask(caseId, "DELIVERY_ARCHIVE", Map.of("archiveConfirmed", true));
  ```
- **最终状态断言**：
  ```java
  assertThat(caseInfo.getCaseStatus()).isEqualTo(CaseStatus.COMPLETED.name());
  assertThat(mainWf.getStatus()).isEqualTo("completed");
  ```

### 3. 为了跑通并加固 E2E 修复的真实问题
- **生产代码修复**：
    - 修正了 `WorkflowRuntimeService` 中的必填字段校验逻辑：改为基于 `case_info` 中已持久化的合并表单数据进行校验，解决了“历史节点数据在后续节点验证不通过”的缺陷。
    - 修正了 `completeCurrentTaskAndNode` 中的 `CaseInfo` 同步问题：确保每次任务完成都能实时触发 `formData` 的增量合并与持久化。
    - 修正了父任务 `subflow_running -> completed` 的转换：当子流程到达 `END` 节点时，自动触发父任务及其关联节点实例的 `completed` 状态变更。
    - 修正了并行汇聚等待逻辑：汇聚节点会把 `subflow_running` 和同作用域 running 子流程视为未完成分支，避免用章子流程未结束时提前进入送达/归档。
    - 修正了办理权限身份来源：当前操作者只从 Spring Security / JWT 认证上下文获得，请求体中的 `assigneeId` 不再代表当前操作者。
    - 修正了下一任务分派逻辑：目标节点存在 `handlerRoleRule` 时优先创建候选任务，`nextAssigneeId` 表示人工指定下一办理人且必须属于候选角色。
    - 修正了结案后 `CaseInfo` / `CaseWfInstance` current node/current handler 可能因 MyBatis-Plus null 更新策略残留的问题。
- **流程配置修复**：
    - 前端动态表单兼容 `readOnly` / `readonly`，长期口径对齐为 `readOnly`。
    - 修正了 `issue-opinion` 的配置，重新激活了 `gateway` 并行语义以支持 E2E 验证。

### 4. 下一步对齐焦点 (供 Gemini/Codex 接手) (🚨 战略修正)
- **结论**：第五阶段可以启动业务域纵深开发，但第四阶段不能标记为最终验收完成；合同管理开发应与第四阶段逐条运行级验收并行推进。

---

## 🎯 Codex 接手：第五阶段纵深攻坚（可并行推进）

**战略目标**：在不提前宣布第四阶段完整收官的前提下，选择 **合同管理 (Contract Management)** 做业务模块纵深闭环。第四阶段仍需继续补 20 流程逐条运行级验收、异常路径、角色权限边界和前端旧 OA 等价体验。

### 1. 合同管理模块纵深闭环任务清单
- [x] **数据库建模（MVP）**：已新增 `contract_info`、`contract_version`、`contract_attachment`，合同不再仅依赖 `case_info.formData`。
- [x] **业务逻辑实现（MVP）**：
    - 已实现合同编号生成、创建、修改、查询、版本快照和附件绑定。
    - 已支持关联单个案件 `related_case_id`；客户/项目多对多关系、收付款计划仍未纳入本轮 MVP。
- [x] **审批与归档闭环（MVP）**：
    - 已实现轻量合同状态机：草稿/驳回可提交，部门审核可通过或驳回，通过后自动归档知识库并写审计。
    - 尚未接入独立动态工作流任务队列，金额分级审批、签署、生效、履行、收付款仍待后续迭代。
- [x] **前端闭环（MVP）**：
    - `/placeholder/contract` 已接入真实接口，实现带权限的数据查询和详情。
    - 已支持合同附件查看、上传/绑定、提交和部门审核动作；付款进度与完整审批历史暂未纳入本轮 MVP。

### 2. 技术约束 (Codex 必须遵守)
- **数据一致性**：合同 MVP 当前在 Service 层按“本人或本部门 / ADMIN 全量”做数据范围控制；后续应把 `contract_info` 纳入统一 `CustomDataPermissionHandler` 行级拦截。
- **归档集成**：合同审批通过后的 PDF 盖章件必须自动归档至知识库的“合同档案”目录，并记录审计事件。
- **引擎适配**：合同审批过程中若涉及金额判断（如 > 50万 需总经理审批），应利用表单计算字段驱动流程分支，保持引擎配置的简洁性。

### 3. 下一步开工指引
1. 执行 `migration_v6_form_data.sql`（如果尚未执行）以确保最新的数据结构支持。
2. 下一轮可在 `com.example.judicialappraisal.contract` 上继续补合同付款计划、签署状态、审批历史查询和金额分级审批。
3. 参考 `ReceivedEntrustToArchiveE2ETest` 编写合同真实数据库集成测试，覆盖“创建 -> 提交 -> 部门审核 -> 知识库归档 -> 审计查询”。

---

## 📝 Gemini CLI 历史完成的工作 (归档)
1. **RBAC 与权限管理**：完成了标准 RBAC 体系、API 权限拦截及五类行级数据权限。
2. **动态平台底座**：实现了流程/表单设计器的版本化管理、导入导出及运行时引擎。
3. **文件与知识库**：完成了基于 MinIO 的文件管理及自动归档审计链路。
4. **第四阶段高保真**：完成了司法鉴定 20 流程首轮配置、主链 happy path E2E 和关键运行时风险修复；逐条运行级验收仍需继续。



## Codex 最新对齐补充（2026-06-12）

- 最新交接给 Gemini 的优先级：司法鉴定主链 E2E 可靠性修复已完成并通过全量测试；第五阶段合同管理 MVP 已启动并完成第一条最小闭环，后续仍需与第四阶段逐条运行级验收并行推进。
- 本次 Codex 已完成 P0/P1 修复：
  - `WorkflowRuntimeService` 并行汇聚等待已把 `subflow_running` 纳入未完成分支，并补充同作用域 running 子流程检查；`issue-opinion` 的用章子流程未完成前，汇聚不会提前进入 `DELIVERY_ARCHIVE`。
  - 工作流办理权限已改为以后端 Spring Security / JWT 认证上下文为准，`WorkflowActionRequest.assigneeId` 不再表示当前操作者；旧字段仅作为 `nextAssigneeId` 的兼容输入。
  - 下一任务分派不再被 `assigneeId` 无条件绕过：目标节点配置 `handlerRoleRule` 时优先创建候选任务；人工指定下一办理人使用 `nextAssigneeId`，且必须属于目标节点候选角色。
  - 普通前端办理不再默认提交当前用户作为下一办理人，避免“系统账号一路推完流程”的假阳性。
  - `CaseInfo` / `CaseWfInstance` current node/current handler 字段已配置 MyBatis-Plus `FieldStrategy.ALWAYS`，结案后可真实清空 null。
  - 前端动态表单已兼容 `readOnly` 和 `readonly`，必填过滤与控件禁用统一走归一后的 `readonly`。
- 本次新增/加强测试：
  - `ReceivedEntrustToArchiveE2ETest.issueOpinionShouldWaitForSealSubflowWhenInvoiceCompletesFirst`
  - `ReceivedEntrustToArchiveE2ETest.issueOpinionShouldWaitForSealSubflowWhenInvoiceNotRequired`
  - `WorkflowRuntimeServiceTests.completingTaskCreatesCandidateTaskWhenTargetNodeHasRoleRuleEvenIfRequestHasAssignee`
  - `WorkflowRuntimeServiceTests.manualNextAssigneeMustMatchTargetNodeCandidateRole`
  - `WorkflowRuntimeServiceTests.currentUserCannotCompleteAssignedTaskWhenRequestHasNoAssigneeId`
  - `WorkflowRuntimeServiceTests.forgedAssigneeIdCannotBypassCurrentUserPermission`
  - `WorkflowRuntimeServiceTests.currentUserCannotCompleteCandidateTaskOutsideCandidateScope`
  - `WorkflowRuntimeServiceTests.assignedCurrentUserCanCompleteTaskWithoutOperatorInRequest`
  - `WorkflowRuntimeServiceTests.candidateUserCanClaimAndCompleteCandidateTask`
  - 主链全量 E2E 结案断言已补充：`caseStatus=COMPLETED`、`CaseInfo.currentNode/currentHandler` 清空、主流程实例 current node 清空、无 active task、无 running subflow。
- 本次验证结果：
  - 后端：`cd judicial-appraisal-backend && mvn test` 通过，`Tests run: 73, Failures: 0, Errors: 0, Skipped: 0`。
  - 前端：`cd judicial-appraisal-frontend && npm install` 成功；`npm run build` 通过。
  - 注意：`npm install` 报告 2 个 moderate severity audit 警告，未执行 `npm audit fix --force`，避免引入破坏性依赖升级。
- 当前阶段判断：
  - 第四阶段不能写成最终验收完成或正式收官。准确口径是：20 流程 / 19 表单首轮高保真配置、自动结构验收、主链 happy path E2E 和关键运行时风险修复已完成；仍需补 20 流程逐条运行级验收、异常路径、角色权限边界、前端旧 OA 等价体验。
  - 第五阶段可以启动合同管理等业务域纵深开发，但当前多数模块仍是结构化看板/派生台账，不能视为完整业务闭环完成。
- 仍需 Gemini/Codex 后续关注：
  - `WorkflowActionRequest.assigneeId` 已废弃为下一办理人兼容输入；后续新开发应只使用 `nextAssigneeId / nextAssigneeName`，不得新增前端提交 `operatorId` 的接口。
  - 继续补 `issue-opinion` 的免章、免开票、退回/终止、非候选办理人等运行级路径。
  - 继续按 `docs/judicial-appraisal-flow-verification-matrix.md` 推进 20 流程逐条运行级验收，再并行启动合同管理纵深闭环。
- 两份最高优先级 Word 已复制到 `docs/`：`docs/完整OA系统重构需求规格说明书.docx`、`docs/司法鉴定系统使用手册.docx`。后续第四阶段、第五阶段和完整 OA 重构均必须严格以这两份文件为最高依据。
- 用户偏好已再次确认：进入阶段性收官或清单式任务时，减少每一步过程汇报，尽量连续完成多项任务后统一汇总。
- 本地默认数据库配置仍为 `localhost:3307 / judicial_appraisal / root / 123456`；这是本机开发配置，不代表团队公共密码。Windows 或其它同事机器应按本机 MySQL 实际密码调整 `application.yml`。
- README 已补充 Windows 10/11 运行方式，覆盖 MySQL 初始化、迁移脚本、Redis、MinIO、后端、前端和 `VITE_API_PROXY_TARGET`。
- 运行时入口已纠偏：新建案件提交时不再优先走旧 `JUDICIAL_MAIN / ACCEPT_REVIEW`，而是绑定 `received-entrust` 最新发布版本，并从首个可办理节点 `INIT_FILL / 发起者填写委托信息` 启动。
- 流程设计器草稿刷新已修复：保存/恢复草稿时对子节点和连线做物理清理，避免逻辑删除记录与 `(wf_id,node_code)` 唯一键冲突，保证 `forceNewVersion=true` 可刷新司法鉴定 20 流程配置。
- 已在本地真实接口跑通 `received-entrust` 高保真主链前半段：`INIT_FILL -> CLERK_REGISTER -> DEPT_REVIEW -> PROJECT_DECISION`，并成功触发 `preliminary-survey` 真子流程首个任务 `ASSISTANT_PREPARE`。
- 第五阶段台账接口在当前代码的 `8080` 后端验证为 live 数据，覆盖 `crm / contract / project / archive / system-permission / system-log / system-datasource`。
- 已新增第四阶段自动验收报告接口 `GET /api/platform/judicial-catalog/verification`，检查流程发布版本、关联表单、开始/结束节点、可办理节点、退回路径、结束路径、连线引用和子流程目标发布状态。
- 已修正 `withdraw-case-letter` 缺少退回路径的问题：项目负责人判断节点可退回撤案函登记节点。
- `case-suspension` 是 20 流程 / 19 表单中的无独立表单流程，验收规则已按目录中的 19 个表单判定，不再误报缺少表单。
- 本地 `8080` 真实接口已执行 `forceNewVersion=true` 导入，刷新发布 19 个表单 / 20 个流程；验收报告返回 `expected=20, checked=20, passed=20, failed=0`。
- 第五阶段其它模块已继续补深：`warehouse / risk / notice / meeting / asset / attendance / official-doc / community / open-api / sso` 已具备模块专属结构化看板、状态筛选、业务事实和下一步动作提示。
- 最新抽测接口：`notice?status=draft`、`attendance?status=exception`、`open-api?status=warning`、`warehouse?status=borrowed` 均返回结构化数据。
- 新建工作页已按旧 OA 入口样式改为流程列表，并补入司法鉴定手册角色筛选栏；角色口径包括 `部门负责人 / 项目负责人 / 项目辅助人 / 档案管理员 / 中心档案管理员 / 技术负责人 / 审阅所长 / 综合业务部 / 财务 / 收案员`。
- 首页已接入第四阶段自动验收矩阵，可直接查看 20 个流程的发布版本、节点/连线、子流程目标、验收状态和问题列表。
- 当前任务继续方向：
  - 第四阶段：把自动验收接口接到前端平台总览或流程中心，并继续做真实案件长链路、父流程回写和页面侧子流程展示。
  - 第五阶段：继续把结构化看板推进到真实数据表、审批动作、详情页和权限边界。
- 最新验证基线：后端 `mvn test` 73 个测试通过，前端 `npm run build` 通过。
- Codex 最新推进：
  - `新建工作` 已按使用手册入口规则调整为直接/关联/系统触发三类语义，防止子流程绕过父流程直接新建。
  - `案件办理页` 已接入当前节点任务、表单设计器预览 schema、动态分组表单、节点附件上传、关联子流程列表和任务级流转提交。
  - 办理动作语义已修正：草稿无任务时 `SUBMIT` 启动流程；已有任务时走当前节点办理，页面默认使用 `APPROVE/RETURN/TERMINATE`。
  - 节点提交已携带 `taskId`、`formData`、`fileIds`，后端现有自动归档可记录表单快照和附件。
  - 后端退回/终止原因校验兼容页面办理意见，符合手册“退回必须填写原因”的要求。
  - 后端运行时已按发布表单 schema 校验正常提交/审批/完成动作的必填字段；退回、终止、撤回不阻塞必填字段，但仍要求原因/办理意见。
  - 司法鉴定主链 E2E 可靠性已加固：并行汇聚等待 `subflow_running` / 同作用域 running 子流程，办理权限以后端认证上下文为准，下一任务分派遵守候选角色规则，结案后 current node/current handler 清空，前端 `readOnly` 口径兼容。
  - 第五阶段模块中心已区分 `实时数据 / 结构化看板 / 示例台账`，避免把结构化建设模块误判为纯虚拟数据。
  - 最新验证：后端 `mvn test` 73 个测试全部通过，前端 `npm run build` 通过。

## Gemini 下一步建议任务（2026-06-12 交接）

### A. 先做合同 MVP 修复后审计，不要直接扩完整合同系统
- 审计 `ContractService` 的状态流转边界：`DRAFT / REJECTED -> UNDER_REVIEW -> ARCHIVED`，确认非负责人不能提交、非本部门/ADMIN 不能审批。
- 审计 `KnowledgeService.archiveBusinessDocument`：确认合同归档进入“合同档案”目录、`artifactCode=CONTRACT-{contractId}`、版本号递增、审计日志写入。
- 审计前端 `/placeholder/contract`：确认普通用户只能看到本人/本部门合同，按钮只在可操作状态出现，附件上传和绑定已有文件 ID 均能提交。
- 审计本地 DB：确认 `contract_info / contract_version / contract_attachment` 存在，必要时执行：
  ```bash
  mysql -h127.0.0.1 -P3307 -uroot -p123456 judicial_appraisal < judicial-appraisal-backend/src/main/resources/db/migration_v7_contract_mvp.sql
  ```

### B. 推荐下一批最小增量
- 增加真实数据库级合同集成测试：创建合同 -> 上传/绑定附件 -> 提交 -> 部门审核通过 -> 知识库出现归档文档 -> 审计日志可查。
- 给合同详情补“审批/操作历史”读取接口，优先复用 `audit_event`，不要新建重复日志系统。
- 把 `contract_info` 纳入统一数据权限策略，或至少补测试锁住 Service 层本人/本部门/ADMIN 范围。
- 补合同付款计划的最小表和只读展示，但不要一次性做收款、开票、合同变更、终止全套闭环。
- 若接动态流程引擎，先设计“合同审批”独立流程定义，并保持现有状态机兼容，不要破坏当前 MVP 可运行链路。

### C. 每次交接前必须重新跑
```bash
cd judicial-appraisal-backend
mvn test

cd ../judicial-appraisal-frontend
npm run build
```

### D. 阶段口径必须保持
- 第四阶段：运行时核心风险已收口，但 20 流程逐条运行级验收、异常路径、角色权限边界、前端旧 OA 等价体验仍未最终完成。
- 第五阶段：合同管理 MVP 已完成第一条最小闭环，可以继续纵深；但第五阶段不能写成收官或完整业务闭环完成。

## Codex 对齐记录（2026-06-11）

### 已核对的文档结论

- `AGENT.md`、根目录 `README.md` 和 `docs/complete-oa-reconstruction-plan.md` 与当前完整 OA 重构目标一致。
- `docs/judicial-appraisal-workflow-design.md`、`docs/judicial-appraisal-state-machine.md`、`docs/judicial-appraisal-database-sql.md`、`docs/judicial-appraisal-frontend-integration.md` 主要描述司法鉴定一期简化方案。
- 旧文档中“系统不是通用 OA”“不做通用流程设计器/表单设计器”等描述已经过期，只能作为现有模型、接口和状态机实现的参考，不能缩小当前项目范围。
- `docs/judicial-appraisal-backend-api-current.md` 用于描述当前已实现接口，不代表完整需求已经实现。

### 当前共同进度判断

- 第一阶段公共平台与可运行骨架已完成。
- 第二阶段权限、动态表单与动态流程平台已完成主体能力：RBAC、动态菜单、API 权限、五类数据权限、表单/流程草稿发布版本、动态出边、多出边并行任务基础、候选/认领/撤回等已落地。
- 第三阶段文件、知识库、审计、自动归档与当前计划范围内的查重/水印/病毒扫描/全文检索已完成。
- 第四阶段司法鉴定高保真流程已完成首轮配置：20 个流程 / 19 个表单均可导入并具备高保真配置。
- 尚未完成：20 个流程逐条真实案件运行验证、父流程回写细化、页面侧子流程展示、完整 OA 其它业务域落地。
- 本地环境已经跑通：Java 17、MySQL 3307、Redis 6379、MinIO 9000、后端 8080、前端 5173。
- 最新验证结果：后端 `mvn test` 通过，前端 `npm run build` 通过，`admin / Admin123` 登录成功。

### 协作约定

- Gemini 已提交的 RBAC、动态菜单和数据权限代码已由 Codex 延续加固，后续不回退该实现方向。
- 下一项司法鉴定开发应优先做真实案件路径联调和父流程回写细化；公共平台后续重点转向更成熟的拖拽设计器与完整 OA 其它业务域。

### Codex / Gemini 最新对齐补充（2026-06-11）

- `issue-draft-opinion` 原先使用了运行时不支持的复合条件表达式，现已改为通过 `feedbackDecision` 显式路由字段分支到 `court-letter` 或 `final-opinion-review`。
- `issue-opinion` 已恢复并验证用章/开票并行分支和 Inclusive 汇聚语义；汇聚会等待 `subflow_running` 与同作用域 running 子流程完成后再进入送达归档。
- `court-letter` 已完成首轮高保真配置：关联原流程、异议判断、回复函编制、项目负责人/部门负责人审核、用章、寄送，以及流向 `final-opinion-review` / `issue-opinion` / `archive` 的后续子流程。
- `court-appearance` 已完成首轮高保真配置：关联原流程、出庭费通知、调档、出庭准备、出庭登记、出庭后材料整理，以及流向 `final-opinion-review` / `issue-opinion` / `archive` 的后续子流程。
- `withdraw-case-letter`、`refund`、`terminate-appraisal`、`archive`、`seal-application`、`expense-reimbursement`、`case-suspension` 已完成首轮高保真配置。
- 文件平台已增加重复文件识别、基础病毒扫描、文本预览水印；知识库检索已覆盖标题、归档快照、归档结果和文本内容。

### Codex 最新完成（2026-06-11）

- 修复数据权限全局追加 `created_by` 导致系统表查询可能报错的问题，改为只对显式业务表应用范围。
- 案件查询支持 `self`、`dept`、`dept_sub`、`all`，其中 `dept_sub` 使用 MySQL 8 递归部门树。
- 管理员识别改为按 `role_code=ADMIN`，不再依赖角色 ID 为 1。
- 动态菜单会自动补齐授权菜单的祖先目录，按钮权限不进入侧边栏，前端支持递归展示深层菜单。
- 每次受保护请求重新加载当前角色和权限，禁用用户或撤销权限后旧 JWT 立即失效。
- 新增权限测试，后端 9 个测试通过；非管理员菜单、案件查询、接口拒绝、即时禁用和部门下级范围均完成端到端验证。

### Codex 第二阶段推进（2026-06-11）

- 新增 `migration_v3_dynamic_platform.sql`，扩展流程定义、节点、流转配置字段，并新增 `form_definition`、`form_version`。
- 新增动态表单设计器后端能力：草稿保存、预览、发布、恢复、版本列表，发布版本不可变。
- 新增动态流程设计器后端能力：草稿保存、预览、发布、恢复、版本列表，发布前校验唯一开始节点、至少一个结束节点和连线引用。
- `WorkflowRuntimeService` 创建新实例时改为绑定 `JUDICIAL_MAIN` 最新已发布流程定义，历史实例仍保留原 `wf_id`。
- 前端新增 `/workflow/forms` 和 `/workflow/processes` 两个页面，动态菜单已能打开真实设计器页面。
- 本地已应用 v3 迁移，并用 `admin / Admin123` 验证设计器 API、菜单和页面可用。
- 最新验证：后端 `mvn test` 共 12 个测试通过，前端 `npm run build` 通过。

### Codex 第一/第二阶段收尾（2026-06-11）

- 已结束本机未响应的 VS Code Java 语言服务进程；该进程不是 Spring Boot 后端。
- 第二阶段 custom 数据权限闭环完成：JWT/当前用户/SQL 拦截器支持 `custom`，新增 `sys_role_data_scope_dept`，管理员可通过 `/api/admin/roles/{roleId}/data-scope` 配置角色自定义组织范围。
- 用户管理页新增“角色数据权限”区域，可维护 `all`、`dept_sub`、`custom`、`dept`、`self` 五类范围。
- 流程运行时补齐定义表驱动流转：办理任务时优先读取 `wf_transition_def`，支持动态目标节点、多出边并行任务和动态结束节点。
- 最新验证：后端 `mvn test` 共 14 个测试通过，前端 `npm run build` 通过；浏览器验证用户管理页和角色数据权限区域可见。
- 第一阶段与第二阶段按当前分阶段计划已收官。第三阶段应进入文件、知识、审计与自动归档闭环。

### Codex 第三/四阶段底座推进（2026-06-11）

- 新增 `migration_v4_file_knowledge_audit.sql`，包含通用文件版本、知识目录、知识文档、文档版本、知识权限、审计事件和案件归档记录表。
- 文件服务从占位接口升级为 MinIO 上传、下载、在线预览和通用版本记录；上传/预览/下载均写入审计。
- 新增知识库后端：公共/部门/案件自动归档目录、目录/文档权限、知识文档版本、按案件/目录/关键词检索、知识文档预览下载。
- 流程办理请求已扩展 `formData` 和 `fileIds`；节点完成后自动写入知识文档版本与案件归档记录，为后续真实表单办理页承接节点产物。
- 新增司法鉴定配置导入服务：可将 `PlatformCatalogService` 中 19 个表单和 20 个流程发布到动态表单/动态流程设计器，默认跳过已发布版本。
- 导入后的表单配置包含输入文件、输出文件、版本产物、附件规则和归档规则；流程配置包含角色办理节点、归档节点、通过路径和退回上一节点路径。
- 前端知识库页已接入真实目录/文档 API，支持搜索、预览和下载；平台总览页新增“导入平台配置”按钮。
- 最新验证：后端 `mvn test` 共 16 个测试通过，前端 `npm run build` 通过。
- 下一步建议：先在本地数据库执行 v4 迁移并调用导入接口，再逐条跑司法鉴定 20 个流程，补齐细化流程图中的条件分支、并行/子流程/流程关联和真实节点表单。

### Codex 第四阶段核对基线（2026-06-11）

- 已复核 `docs`、最新需求规格说明书、司法鉴定使用手册和截图，确认当前方向正确：先公共平台底座，再司法鉴定高保真，再完整 OA 业务域。
- 已新增 `docs/judicial-appraisal-flow-verification-matrix.md`，作为后续逐条实现和验收的基线。
- 矩阵覆盖 20 个流程、19 个表单、输入/输出文件、版本产物、当前状态、下一步重点。
- 首条流程“收到委托书”已拆出高保真校准清单：发起者、收案员登记、部门负责人审阅、项目负责人决策、并行通知/材料接收/初步勘验/缴费/不予受理分支。
- 已实现 `received-entrust` 的真实表单字段 schema、真实节点配置和条件表达式运行时判断。
- 下一步应优先补子流程/关联流程最小模型，并用真实案件跑通“初步勘验 / 发交费通知 / 不予受理”三条路径。

### Codex 首条司法鉴定流程校准（2026-06-11）

- `JudicialConfigImportService` 已对 `received-entrust` 做专项配置：字段组、必填校验、附件查重、流程名模板、输入/输出文件、归档规则和角色节点均来自细化流程基线。
- `received-entrust` 流程配置新增发起者填写、收案员登记、部门负责人审阅、项目负责人决策、项目辅助人通知、材料接收、初步勘验、缴费、不予受理和结束节点。
- `WorkflowRuntimeService` 已支持基于 `formData` 的简单条件表达式：`form.xxx == true/false`、字符串、数字、`!=`、`always/never`，办理任务时只推进命中的出边。
- 新增测试覆盖首条流程导入配置和条件流转命中路径；最新验证：后端 `mvn test` 共 18 个测试通过，前端 `npm run build` 通过。

### Codex 子流程最小模型（2026-06-11）

- 已新增 `CaseSubflowInstance` 实体、Mapper 和 `migration_v5_subflow_relation.sql`，补齐父任务、父节点、子流程编码和名称字段。
- `WorkflowRuntimeService` 已支持读取 `wf_transition_def.transition_config_json` 中的 `launchSubflow/subflowCode`，命中时自动创建子流程实例，并把后续节点实例、任务挂到 `subflowInstanceId`。
- `received-entrust` 的三条关键分支已经带上子流程配置：`preliminary-survey`、`payment-notice`、`reject-acceptance`。
- 新增测试覆盖“命中分支时自动创建子流程实例并将任务绑定到子流程”；最新验证：后端 `mvn test` 共 19 个测试通过，前端 `npm run build` 通过。

### Codex 子流程收口与查询（2026-06-11）

- 已修正子流程结束语义：子流程任务流转到 `END` 时，不再直接把整个案件主流程打成完成，而是优先回写到同案件同流程的剩余活动任务。
- 当所有活动任务都已结束时，仅结束对应流程实例；只有老的 `JUDICIAL_MAIN` 主流程收口时才会把案件状态打成 `COMPLETED`。
- 已新增 `/api/cases/{caseId}/subflows` 查询接口，可直接查看案件触发的子流程实例、父任务、父节点、子流程编码/名称、状态和原因。
- 任务摘要、任务详情与前端 `judicial.ts` 已补 `subflowInstanceId`，便于后续页面联调。
- 最新验证：后端 `mvn test` 共 20 个测试通过，前端 `npm run build` 通过。

### Codex 真子流程执行（2026-06-11）

- 已将 `launchSubflow` 从“只创建子流程实例并给主流程任务打标签”升级为“直接从子流程定义中选择首个可办节点创建任务”。
- 当任务携带 `subflowInstanceId` 时，运行时会按子流程自己的 `wf_definition / wf_node_def / wf_transition_def` 继续流转，而不是继续使用主流程定义。
- 这意味着 `received-entrust` 的三条关键分支现在已经具备真实子流程执行基础，而不是仅有关系记录。

### Codex 初步勘验高保真校准（2026-06-11）

- 已重构根目录 `README.md`，使新接手者可快速理解项目目标、阶段状态、已完成功能、未完成范围、运行方式和当前主线。
- `JudicialConfigImportService` 已对 `preliminary-survey` 做专项配置：现场工作方案、设备出入库记录、设备使用记录、是否具备鉴定条件、下一步建议等真实字段已写入表单 schema。
- `preliminary-survey` 流程配置已新增项目辅助人准备、项目负责人审核、转发交费通知、转终止鉴定和退回补充记录等节点/路径。
- 条件分支已落到动态流程配置：`form.appraisalConditionMet == true/false` 分别触发 `payment-notice` 与 `terminate-appraisal` 子流程。
- 最新验证：后端 `mvn test` 共 21 个测试通过，前端 `npm run build` 通过。

### Codex 发交费通知高保真校准（2026-06-11）

- `JudicialConfigImportService` 已对 `payment-notice` 做专项配置：缴费函件草稿、函件类型、是否需要用章、盖章件回传、是否已缴费、下一步建议等真实字段已写入表单 schema。
- `payment-notice` 流程配置已新增项目辅助人编制、项目负责人审核、用章流程、档案管理员回传盖章件、项目负责人确认缴费等节点。
- 条件分支已落到动态流程配置：`form.sealRequired == true/false` 决定是否进入 `seal-application`，`form.paymentReceived == true/false` 分别触发 `quality-control` 与 `terminate-appraisal` 子流程。
- 最新验证：后端 `mvn test` 共 22 个测试通过，前端 `npm run build` 通过。

### Codex 不予受理高保真校准（2026-06-11）

- `JudicialConfigImportService` 已对 `reject-acceptance` 做专项配置：不予受理原因、通知书草稿、项目负责人审核、是否用章、盖章通知书回传、送达方式、送达日期、归档确认等真实字段已写入表单 schema。
- `reject-acceptance` 流程配置已新增项目辅助人编制通知书、项目负责人审核、用章流程、档案管理员回传盖章通知书、送达并归档、进入归档子流程等节点。
- 条件分支已落到动态流程配置：`form.projectReviewPassed == true/false` 控制审核通过或退回修改，`form.archiveConfirmed == true` 触发 `archive` 子流程；审核通过会触发 `seal-application` 子流程。
- 最新专项验证：后端 `mvn test -Dtest=JudicialConfigImportServiceTests` 共 5 个测试通过。
- 下一步建议：用真实案件跑通 `received-entrust -> reject-acceptance` 路径，补更细的父流程回写和页面侧子流程展示；随后优先校准 `quality-control`。

### Codex 内部质量控制高保真校准（2026-06-11）

- `JudicialConfigImportService` 已对 `quality-control` 做专项配置：内部质量控制文件草稿、格式类型、合同金额、F 类项目判断、项目负责人审核流向、部门负责人审核、用章、盖章件回传、下一步建议等真实字段已写入表单 schema。
- 表单校验/计算规则已声明：中心格式且合同金额大于 50 万，或非中心格式且合同金额大于 25 万时判定 F 类项目；F 类项目需进入部门负责人审核。
- `quality-control` 流程配置已新增项目辅助人编制、项目负责人审核并判定 F 类、部门负责人审核 F 类项目、用章流程、档案管理员回传盖章件、项目负责人确认后续流程等节点。
- 条件分支已落到动态流程配置：运行时用 `form.projectReviewRoute` 控制进入部门负责人审核、用章或退回修改，`form.departmentReviewPassed` 控制 F 类审核通过/退回，`form.nextRecommendation` 分别触发 `field-survey`、`material-receive-return`、`draft-opinion-review`、`final-opinion-review`、`refund`、`terminate-appraisal`。
- 最新专项验证：后端 `mvn test -Dtest=JudicialConfigImportServiceTests` 共 6 个测试通过。
- 下一步建议：后续真实案件联调要验证 `payment-notice -> quality-control -> field-survey/material/draft/final/refund/terminate` 的父流程回写。

### Codex 现场勘验高保真校准（2026-06-11）

- `JudicialConfigImportService` 已对 `field-survey` 做专项配置：现场勘验日期/地点、现场工作方案、勘验记录、设备使用记录、设备归还记录、项目金额、15 万阈值、项目负责人审核流向、技术负责人审核、部门负责人审核、下一步建议等真实字段已写入表单 schema。
- 表单校验/计算规则已声明：项目金额大于 15 万时判定需逐级审核；审核通过前必须具备勘验记录和设备使用记录。
- `field-survey` 流程配置已新增项目辅助人完成勘验记录、项目负责人审核、技术负责人审核、部门负责人审核、项目负责人确认后续流程等节点。
- 条件分支已落到动态流程配置：运行时用 `form.projectReviewRoute` 控制进入技术负责人审核、确认后续流程或退回修改，`form.technicalReviewPassed` / `form.departmentReviewPassed` 控制逐级审核通过/退回，`form.nextRecommendation` 分别触发 `material-receive-return`、`draft-opinion-review`、`final-opinion-review`、`refund`、`terminate-appraisal`。
- 最新专项验证：后端 `mvn test -Dtest=JudicialConfigImportServiceTests` 共 7 个测试通过。
- 下一步建议：优先校准 `material-receive-return`，补材料来源、补材通知、介质、存放、返还/保管和并行归档；同时要开始做真实案件路径联调，验证各子流程结束后的父流程回写和页面侧展示。

## Gemini 下一步开工清单（2026-06-11）

### 当前未提交改动

- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/platform/service/JudicialConfigImportService.java`
- `judicial-appraisal-backend/src/test/java/com/example/judicialappraisal/platform/service/JudicialConfigImportServiceTests.java`
- `README.md`
- `GEMINI.md`
- `docs/judicial-appraisal-flow-verification-matrix.md`

这些改动是 Codex 刚完成的司法鉴定第四阶段推进，不要回退。

### 建议立即继续

1. 先阅读 `README.md`、`docs/complete-oa-reconstruction-plan.md`、`docs/judicial-appraisal-flow-verification-matrix.md`。
2. 确认当前目标仍是完整 OA 网站重构，不是单独司法鉴定小系统。
3. 继续在 `JudicialConfigImportService` 中按已形成的模式校准 `material-receive-return`：
   - 表单字段建议覆盖：案件号、项目负责人、项目辅助人、档案管理员、材料来源、是否补充材料、补材通知、材料名称/数量/介质、接收时间、存放地址、是否返还、返还接收人、返还时间、保管状态、下一步建议。
   - 流程节点建议覆盖：项目负责人确认材料需求、项目辅助人登记材料、档案管理员接收/保管/返还、项目负责人确认下一步。
   - 后续流向建议覆盖：`draft-opinion-review`、`final-opinion-review`、`refund`、`terminate-appraisal`、`archive`。
4. 补 `JudicialConfigImportServiceTests`，参考现有 `qualityControlImportUsesHighFidelityFormAndWorkflowConfiguration` 和 `fieldSurveyImportUsesHighFidelityFormAndWorkflowConfiguration`。
5. 更新三份交接文档：
   - `README.md`：面向项目组，只写当前阶段状态。
   - `GEMINI.md`：面向协作 AI，写清实现细节、验证结果、下一步。
   - `docs/judicial-appraisal-flow-verification-matrix.md`：更新流程矩阵状态。
6. 最后运行：
   - `cd judicial-appraisal-backend && mvn test`
   - `cd judicial-appraisal-frontend && npm run build`

### 注意事项

- 运行时条件表达式目前只支持简单的 `form.xxx == value` / `form.xxx != value`，不支持 `&&`、`>`、`<` 等复杂表达式。复杂金额或组合规则应放进表单计算/校验配置，再用单独的路由字段驱动流程分支。
- 旧 Markdown 中“第一期不做通用 OA / 不做通用表单设计器 / 不做通用流程设计器”等描述已过期，只能参考模型和历史实现，不能缩小项目范围。
- `README.md` 是给项目组成员交接的；`GEMINI.md` 是给 Gemini/Codex 这类协作 AI 交接的，二者不要混用。

---
*注：Codex 在接手任务后，请根据本文件了解 Gemini CLI 已完成的部分，避免重复劳动。*
