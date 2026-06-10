# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目重构。

## 📊 当前项目状态摘要 (2026-06-10)
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

---
*注：Codex 在接手任务后，请根据本文件了解 Gemini CLI 已完成的部分，避免重复劳动。*
