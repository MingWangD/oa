# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目重构。

## 📊 当前项目状态摘要 (2026-06-11)
- **阶段**：第二阶段（权限、动态表单与动态流程平台）进行中。
- **核心成果**：
    - 已完成全案调研，梳理了 20 个流程和 19 个表单的元数据定义（详见 `PlatformCatalogService.java`）。
    - 数据库 Schema 已就绪，新增了 `sys_menu` 和 `sys_role_menu` 表以支持动态路由和菜单。
    - 前端 `App.vue` 已对接后端提供的动态菜单接口 `PermissionService`，并支持图标动态解析。
    - 后端 `UserController` 已接入 `@PreAuthorize` 注解以实现 API 级别控制。
    - 引入了 MyBatis-Plus 的 `DataPermissionInterceptor` 与 `@DataScope` 接口进行数据权限控制的底层搭设。

## 📝 Gemini CLI 最新完成的工作
1. **RBAC 实施**：重构了系统的鉴权体系，在 `JwtTokenService` 注入角色与权限，由 `CustomDataPermissionHandler` 提供行级数据权限底层支撑。
2. **菜单动态化**：将前端硬编码的菜单抽取到后端通过 `sys_menu` 管理，并提供树状数据结构供 Vue Router 和 Element Plus 渲染。
3. **安全更新**：移除了手动的 `requireAdmin`，代之以更标准的 Spring Security 注解鉴权。

## 🎯 下一步对齐焦点 (供 Codex 参考)
- **动态表单与流程**：第二阶段仍需实现表单设计器和流程引擎（`wf_definition`、`wf_node_def` 等模型已具备）。建议接下来从流程定义的增删改查及发布入手。
- **完整权限落地**：当前数据权限提供了基础的 handler，需要后续业务功能（如业务台账）查询时关联对应别名。

## Codex 对齐记录（2026-06-11）

### 已核对的文档结论

- `AGENT.md`、根目录 `README.md` 和 `docs/complete-oa-reconstruction-plan.md` 与当前完整 OA 重构目标一致。
- `docs/judicial-appraisal-workflow-design.md`、`docs/judicial-appraisal-state-machine.md`、`docs/judicial-appraisal-database-sql.md`、`docs/judicial-appraisal-frontend-integration.md` 主要描述司法鉴定一期简化方案。
- 旧文档中“系统不是通用 OA”“不做通用流程设计器/表单设计器”等描述已经过期，只能作为现有模型、接口和状态机实现的参考，不能缩小当前项目范围。
- `docs/judicial-appraisal-backend-api-current.md` 用于描述当前已实现接口，不代表完整需求已经实现。

### 当前共同进度判断

- 第一阶段公共平台与可运行骨架已完成。
- 第二阶段正在进行：RBAC、动态菜单、API 权限和数据权限底层已经搭建；案件查询已接入本人、本部门、本部门及下级、全量数据范围。
- 动态表单设计器、动态流程设计器、流程发布版本、并行/汇聚、子流程和完整退回规则仍未完成。
- 文件、知识归档、审计闭环和司法鉴定 20 个流程、19 个表单的逐节点实现仍未完成。
- 本地环境已经跑通：Java 17、MySQL 3307、Redis 6379、MinIO 9000、后端 8080、前端 5173。
- 最新验证结果：后端 `mvn test` 通过，前端 `npm run build` 通过，`admin / Admin123` 登录成功。

### 协作约定

- Gemini 已提交的 RBAC、动态菜单和数据权限代码已由 Codex 延续加固，后续不回退该实现方向。
- 下一项公共平台开发应优先落地动态表单与动态流程定义管理，并继续补齐自定义组织范围和其他业务查询的数据权限接入。

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

---
*注：Codex 在接手任务后，请根据本文件了解 Gemini CLI 已完成的部分，避免重复劳动。*
