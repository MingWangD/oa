# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目重构。

## 📊 当前项目状态摘要 (2026-06-11)
- **阶段**：第三阶段已完成，第四阶段已完成首轮高保真流程配置，第五阶段已完成第一轮整批模块落地。
- **核心成果**：
    - 第一阶段、第二阶段按当前分阶段计划已收官。
    - RBAC 菜单/按钮/API 权限、五类数据权限、动态菜单和即时权限失效已落地。
    - 动态表单与动态流程已支持草稿、预览、发布、恢复、版本列表和发布版本不可变。
    - 文件、知识库、审计、自动归档底座已落地，MinIO 上传/预览/下载与知识库检索可用。
    - 已梳理并支持导入司法鉴定 20 个流程、19 个表单。
    - 已完成 20 个司法鉴定流程 / 19 个表单当前计划范围内的首轮高保真校准，其中新增完成 `withdraw-case-letter`、`refund`、`terminate-appraisal`、`archive`、`seal-application`、`expense-reimbursement`、`case-suspension`。
    - `issue-opinion` 已从“配置层并行网关”收敛为当前引擎可稳定执行的顺序流；`issue-draft-opinion` 已改为使用显式路由字段，避免运行时不支持复合条件表达式。
    - 已完成第三阶段增强项当前计划范围：文件查重命中识别、基础病毒扫描拦截、文本预览水印、知识库全文检索。
    - 前端占位页已升级为完整 OA 业务域模块中心，可按业务分域查看当前入口、共享能力和下一步建设重点。
    - 首页“当前阶段”已改为按后端阶段状态动态显示，第五阶段在平台目录中已标记为 `in_progress`。
    - 已新增 `/api/ledger/modules/{moduleCode}`，并完成 `crm`、`contract`、`project` 第一批真实台账页；当前先复用案件数据派生客户聚合、合同清单、项目状态与预警信息。
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

## 📝 Gemini CLI 历史完成的工作
1. **RBAC 实施**：重构了系统的鉴权体系，在 `JwtTokenService` 注入角色与权限，由 `CustomDataPermissionHandler` 提供行级数据权限底层支撑。
2. **菜单动态化**：将前端硬编码的菜单抽取到后端通过 `sys_menu` 管理，并提供树状数据结构供 Vue Router 和 Element Plus 渲染。
3. **安全更新**：移除了手动的 `requireAdmin`，代之以更标准的 Spring Security 注解鉴权。
4. **高保真校准**：完成了 20 个司法鉴定流程的首轮高保真表单与流程配置，并补充了测试。

## 🎯 下一步对齐焦点 (供 Gemini/Codex 接手)
- **优先任务**：从“第五阶段整批模块已落地”切换到“模块细化”和“第四阶段联调验收”双线。
- **本次建议目标**：
  - 业务域侧：在已完成的 `CRM / 合同 / 项目` 第一批台账基础上，继续补更细字段、筛选条件和详情页。
  - 司法鉴定侧：验证 `received-entrust` 主链、关键子流程联动、父流程收口、页面侧子流程展示和旧 OA 对齐行为。
- **业务重点**：让第五阶段从“目录承载”进入“真实模块页面”，同时让第四阶段从“高保真配置已完成”走向“真实流程可验收”。
- **验证要求**：修改后运行后端 `mvn test` 和前端 `npm run build`。

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
- `issue-opinion` 原先使用了配置层并行网关节点，但当前运行时没有 BPMN 网关语义；现已改为“补充材料 -> 用章/免章 -> 开票/免开票 -> 送达归档”的顺序流。
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
