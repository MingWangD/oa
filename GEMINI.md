# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目重构。

## 📊 当前项目状态摘要 (2026-06-10)
- **阶段**：第一阶段（公共平台骨架）已基本收尾。
- **核心成果**：
    - 已完成全案调研，梳理了 20 个流程和 19 个表单的元数据定义（详见 `PlatformCatalogService.java`）。
    - 后端技术栈（Spring Boot 3 + MyBatis-Plus）及前端技术栈（Vue 3 + TS）已对齐。
    - 数据库 Schema 已就绪，涵盖了 RBAC、流程实例、材料台账等核心模型。

## 📝 Gemini CLI 最新完成的工作
1. **项目调研**：完成了对现有后端 `workflow`、`platform` 模块和前端 `views`、`api` 的深度阅读。
2. **规范对齐**：确认了以 `AGENT.md` 为核心的需求优先级体系。
3. **协作设置**：创建了本对齐文档，并更新了 `AGENT.md` 引导 Codex 进行跨 Agent 通讯。

## 🎯 下一步对齐焦点 (供 Codex 参考)
- **RBAC 强化**：目前系统权限较薄弱，需从硬编码的角色判断转向动态的菜单/按钮权限。
- **流程动态化**：`WorkflowRuntimeService` 中存在大量硬编码的分支判断（如 `handleApprove`），建议后续通过 `wf_transition_def` 进行动态驱动。
- **数据权限**：需在 Mapper 层或 Service 层引入基于部门树的数据过滤机制。

---
*注：Codex 在接手任务后，请根据本文件了解 Gemini CLI 已完成的部分，避免重复劳动。*
