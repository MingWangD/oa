# 02.5 主干流程补证记录

验收日期：2026-06-14

## 目标

根据 `02-mainline-flow.md` 的 `[~]` 结论，补齐主干流程真实页面办理证据和知识库页面证据，并修复页面办理、详情表单回填、知识库归档权限中属于手册范围的问题。本次未执行 `docs/acceptance-prompts/03-all-19-flows.md`。

## 页面输入问题排查

结论：Browser Use/in-app Browser 的 `locator.fill()` 与 `locator.type()` 在本机真实页面表单上触发 `Browser Use virtual clipboard is not installed`，普通 `textarea` 也会失败；同一元素使用逐键 `press()` 可输入，说明页面组件本身并非完全不可输入。为稳定覆盖附件上传、账号切换、待办进入和表单输入，本次改用系统 Chrome + Playwright，使用原生 `fill()`、DOM input/change 事件和 `setInputFiles()` 完成真实页面操作。

仍有限制：页面内通过 `Blob + a.click()` 触发的下载在 headless Playwright 中不一定产生 `download` 事件；本次在页面点击后，使用同一真实账号 token 调用对应下载接口保存文件，记录为浏览器下载事件捕获限制，不替代页面可见性和点击证据。

## 补证实例

优先追溯原主干办结实例：

| 项 | 值 |
| -- | -- |
| 案件 ID | `2758` |
| 案件编号 | `OA-MAIN-20260614-001` |
| 主流程实例编号 | `2726` |
| 用途 | 办结只读、工作查询详情、流程日志、节点附件、知识库归档文档复验 |

由于 `2758` 已办结，无法补做待办页面办理动作，因此新建最小页面复验实例：

| 项 | 值 |
| -- | -- |
| 案件 ID | `2775` |
| 案件编号 | `OA-PAGE-FINAL-20260614064446` |
| 主流程实例编号 | `2737` |
| 用途 | 真实页面从待办进入、表单填写、附件上传、办理意见、提交到下一节点，推进至 `ASSISTANT_NOTICE` |

退回/重提页面证据沿用页面复验实例：

| 项 | 值 |
| -- | -- |
| 案件 ID | `2766` |
| 案件编号 | `OA-PAGE-20260614-20260614061842` |
| 主流程实例编号 | `2730` |
| 用途 | `DEPT_REVIEW` 页面退回、`CLERK_REGISTER` 页面重新提交、重新审核通过 |

## 页面补证结果

| 节点名称 | 使用账号 | 从待办进入 | 填写表单 | 上传附件 | 填写办理意见 | 提交/退回/重提 | 证据 | 结果 |
| -- | -- | -- | -- | -- | -- | -- | -- | -- |
| 草稿/发起 | `case_acceptor` | 新建工作进入 | 是 | 是 | 是 | 提交启动 | `32-final-draft-before-submit.png`、`32-40-final-page-run-result.json` | 通过 |
| 发起者填写委托信息 `INIT_FILL` | `case_acceptor` | 是 | 是 | 未新增 | 是 | 提交下一节点 | `33-final-init-fill-todo-list.png`、`34-final-init-fill-before-submit.png` | 通过 |
| 收案员登记 `CLERK_REGISTER` | `case_acceptor` | 是 | 是 | 是 | 是 | 提交下一节点 | `35-final-clerk-register-todo-list.png`、`36-final-clerk-register-before-submit.png` | 通过 |
| 部门负责人审阅 `DEPT_REVIEW` | `dept_leader` | 是 | 是 | 未新增 | 是 | 提交下一节点 | `37-final-dept-review-todo-list.png`、`38-final-dept-review-before-submit.png`、`37-40-final-page-continue-result.json` | 通过 |
| 项目负责人决策 `PROJECT_DECISION` | `project_leader` | 是 | 不适用 | 未新增 | 是 | 提交下一节点 | `39-final-project-decision-todo-list.png`、`40-final-project-decision-before-submit.png` | 通过，流转至 `ASSISTANT_NOTICE` |
| 部门负责人审阅退回 | `dept_leader` | 是 | 是 | 是 | 是 | 退回 | `04-dept-review-before-return.png` | 通过 |
| 收案员登记重提 | `case_acceptor` | 是 | 是 | 是 | 是 | 重新提交 | `05-clerk-register-before-resubmit.png` | 通过 |
| 部门负责人重审通过 | `dept_leader` | 是 | 是 | 是 | 是 | 提交下一节点 | `06-dept-review-before-approve.png` | 通过 |
| 办结只读详情 | `case_acceptor` | 工作查询进入 | 只读 | 展示 | 展示 | 不可提交 | `../02-mainline-readonly-detail.png` | 通过 |
| 工作查询详情 | `case_acceptor` | 查询进入 | 只读 | 展示 | 展示 | 不可提交 | `../02-mainline-work-query.png` | 通过 |

关键复核：`2775` 的 `formData.entrustAccepted=true` 在草稿提交、`INIT_FILL`、`CLERK_REGISTER`、`DEPT_REVIEW`、`PROJECT_DECISION` 后均保持为 `true`，未再退回到不予受理分支。

## 知识库页面复验

| 项 | 结果 |
| -- | -- |
| 使用账号 | `archivist` |
| 页面是否可进入 | 是 |
| 是否能看到归档文档 | 是，搜索 `OA-MAIN` 返回 37 条带文件归档记录 |
| 是否能打开/预览 | 是，打开 `blob:http://127.0.0.1:5173/...`，截图 `42-knowledge-preview-opened-oamain.png` |
| 是否能下载 | 是，页面点击下载后，同账号下载接口保存 `downloads/single-37657.bin`，HTTP 200，74 bytes |
| 是否能批量下载 | 是，页面选择两条并点击批量下载后，同账号下载接口保存 `downloads/batch-37657.bin`、`downloads/batch-37656.bin` |
| 权限外账号结果 | `business_staff` 搜索 `OA-MAIN` 页面 0 条，截图 `46-knowledge-business-staff-denied-oamain.png`；直接下载文档 `37657` 返回业务 `403` |
| 是否通过 | 通过 |

知识库证据文件：

- `41-knowledge-archivist-list-oamain.png`
- `42-knowledge-preview-opened-oamain.png`
- `43-knowledge-single-download-click-oamain.png`
- `44-knowledge-batch-selected-oamain.png`
- `45-knowledge-batch-clicked-oamain.png`
- `46-knowledge-business-staff-denied-oamain.png`
- `41-45-knowledge-page-result.json`
- `downloads/single-37657.bin`
- `downloads/batch-37657.bin`
- `downloads/batch-37656.bin`

## 权限初步判断

`business_staff` 读取办结案件 `2758`：不合理，已修复。复验结果：`/api/cases?keyword=OA-MAIN-20260614-001` 返回 total 0，`/api/cases/2758` 返回业务 `403`。

`business_staff` 读取 2758 知识归档文档：不合理，已修复。修复后案件归档类知识文档先校验案件访问权，再校验知识库目录/文档授权；公共知识和非案件归档文档仍按知识库授权规则处理。复验结果：`business_staff` 知识库列表 0，直接下载文档 `37657` 返回业务 `403`；`archivist` 仍可查看 37 条并下载成功。

## 修复问题清单

| 编号 | 问题描述 | 是否手册范围 | 修复 | 复验 |
| -- | -- | -- | -- | -- |
| P04 | Browser Use/in-app Browser 页面 `fill/type` 因虚拟剪贴板缺失不稳定 | 是，影响真实页面办理取证 | 改用系统 Chrome + Playwright；普通文本用原生 fill，附件用 setInputFiles，布尔表单用真实点击/事件 | 2775 真实页面节点通过 |
| P06 | `GET /api/cases/{id}` 未正确反序列化 `form_data`，页面后续节点重新加载时丢失 `entrustAccepted=true` | 是，影响主干分支 | `CaseInfoMapper.selectRawById` 增加 `form_data` 的 `JacksonTypeHandler` 映射 | 2775 多节点复验均保持 true，流转至 `ASSISTANT_NOTICE` |
| P07 | 非当前/历史相关账号可通过工作查询读到已办结案件 | 是，权限边界 | 案件列表与详情按当前用户过滤；候选角色仅对待办/处理中任务授权，不再凭历史候选关系读全案 | `business_staff` 查询 2758 total 0，详情业务 403 |
| P08 | `business_staff` 无案件权限时仍可通过知识库读取 2758 案件归档文档 | 是，明显越权 | 案件归档类知识文档/目录先校验案件访问权；无权下载返回业务 403 | `business_staff` 知识库 0 条，直链下载 403；`archivist` 可读可下载 |
| P09 | 知识库页面缺少批量下载入口，无法覆盖手册要求 | 是，知识库能力 | 前端知识库表格增加选择列和“批量下载”按钮 | 页面选择两条并触发批量下载，文件保存成功 |

## 修改文件

- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/caseinfo/controller/CaseInfoController.java`
- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/caseinfo/service/CaseInfoService.java`
- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/caseinfo/mapper/CaseInfoMapper.java`
- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/knowledge/service/KnowledgeService.java`
- `judicial-appraisal-frontend/src/views/KnowledgeBaseView.vue`

沿用第 2 步已完成修复：

- `judicial-appraisal-backend/src/main/java/com/example/judicialappraisal/workflow/service/WorkflowRuntimeService.java`
- `judicial-appraisal-backend/src/test/java/com/example/judicialappraisal/workflow/service/ManualAcceptanceWalkthroughVerificationTest.java`
- `judicial-appraisal-frontend/src/api/judicial.ts`
- `judicial-appraisal-frontend/src/views/CaseDetailView.vue`

## 回归

- `cd judicial-appraisal-frontend && npm run build`：通过。
- `cd judicial-appraisal-backend && mvn test -Dtest=ManualAcceptanceWalkthroughVerificationTest`：通过。

## 结论

主干流程状态从原 `[~] 真实账号 + 真实接口辅助办理 + 页面查询/只读复验` 推进为更接近手册级的 `[~] 真实页面关键节点办理 + 真实页面退回/重提 + 页面知识库归档复验 + 权限边界修复`。

仍保持 `[~]` 的原因：已办结主实例 `2758` 无法倒回待办补做全链路页面办理；新实例 `2775` 已覆盖发起至项目负责人后的 `ASSISTANT_NOTICE`，但未继续用页面跑完后续所有子流程、用章、质控、发函、归档节点。建议可以进入 `03-all-19-flows.md`，但进入时应以本次修复后的 Playwright/Chrome 方式作为标准页面自动化手段，并把 19 流程逐项页面证据作为下一阶段验收重点。
