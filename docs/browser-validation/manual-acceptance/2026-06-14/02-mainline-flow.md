# 02 主干流程手册级试跑记录

| 字段 | 内容 |
| -- | -- |
| 验收日期 | 2026-06-14 |
| 前端地址 | `http://127.0.0.1:5173` |
| 后端地址 | `http://127.0.0.1:8080` |
| 试跑流程 | 收到委托书 -> 发交费通知书及相关函件 -> 编制内部质量控制文件 -> 用章申请表 -> 鉴定意见书送审稿编制 -> 出具鉴定意见书 -> 用章申请表 -> 归档 -> 工作查询详情 |
| 真实案件 ID | `2758` |
| 业务案件编号 | `OA-MAIN-20260614-001` |
| 试跑结论 | `[~]` 主干引擎闭环通过；页面发起、工作查询、办结只读已复验；节点办理因浏览器输入工具限制采用真实业务账号 API 辅助提交，尚不能标为手册级 `[x]` |

## 数据来源

优先使用 `docs/original-oa-samples/exports/` 下原 OA 导出的工作查询 Excel 样例，并用同名 CSV/JSON/metadata 对照字段与流程名称：

- `docs/original-oa-samples/exports/old-oa-work-query-flow-104-收到委托书.xls`
- `docs/original-oa-samples/csv-preview/old-oa-work-query-flow-104-收到委托书.csv`
- `docs/original-oa-samples/json/old-oa-work-query-flow-104-收到委托书.json`
- `docs/original-oa-samples/metadata/old-oa-work-query-flow-104-收到委托书.metadata.json`
- `docs/original-oa-samples/exports/old-oa-work-query-flow-106-发交费通知书及相关函件.xls`
- `docs/original-oa-samples/exports/old-oa-work-query-flow-112-编制内部质量控制文件.xls`
- `docs/original-oa-samples/exports/old-oa-work-query-flow-115-鉴定意见书送审稿编制.xls`
- `docs/original-oa-samples/exports/old-oa-work-query-flow-119-出具鉴定意见书.xls`
- `docs/original-oa-samples/exports/old-oa-work-query-flow-108-归档.xls`
- `docs/original-oa-samples/exports/old-oa-work-query-flow-109-用章申请表.xls`

选取的对照样例为原 OA `收到委托书` CSV 中流程号 `749` 的记录，字段包括 `快递单号=1213213`、委托人 `内蒙法院`、案件号 `123456`、鉴定类别 `造价鉴定`、状态 `已结束`、附件 `test1.docx*`。本次为避免污染原样例语义，按样例结构创建最小新测试案：`OA-MAIN-20260614-001`，委托人 `脱敏测试法院`，快递单号 `123213`，鉴定类别 `工程造价`。

## 账号与节点

本次未使用 `admin` 办理业务节点。真实业务账号与节点如下：

| 账号 | 角色 | 本次办理节点 |
| -- | -- | -- |
| `case_acceptor` | 收案员/收件人/申请人 | 页面发起 `收到委托书` 草稿、发起者填写、收案员登记、工作查询/只读详情复验 |
| `dept_leader` | 部门负责人 | 部门负责人审阅、鉴定意见书送审稿部门负责人审核 |
| `project_leader` | 项目负责人/申请人 | 项目负责人决策、函件审核退回/通过、缴费确认、质控审核、后续流程判断、送审稿审核、出具意见书修改/审核、用章申请 |
| `project_assistant` | 项目辅助人 | 主流程通知、缴费函件编制/重新提交、质控文件编制、送审稿初稿、承诺书与复核意见、盖章扫描件上传 |
| `tech_leader` | 技术负责人 | 鉴定意见书送审稿技术负责人审核 |
| `archivist` | 档案管理员/盖章经办人/邮寄人员 | 缴费函件回传、质控用章、质控盖章件回传、出具意见书盖章确认/用章、送达归档、归档整理 |
| `center_archivist` | 中心档案管理员 | 中心档案审核并入库 |
| `business_staff` | 综合业务部 | 权限边界复验 |
| `finance` | 财务 | 登录可用；本次主链选择 `invoiceRequired=false`，未进入开票办理 |

## 流程实例与节点结果

| 项 | 值 |
| -- | -- |
| 案件 ID | `2758` |
| 案件编号 | `OA-MAIN-20260614-001` |
| 主流程实例 | `2726` |
| 发起时间 | 2026-06-14 13:47 左右 |
| 办结时间 | 2026-06-14 13:49:05 |
| 终态 | `COMPLETED` |

数据库复核结果：`case_task` 共 44 条，已完成 44 条，退回 1 条，44 条均保存办理意见；`file_version` 共 37 条；`knowledge_document` 共 44 条。

关键节点覆盖：

| 节点范围 | 办理账号 | 附件 | 意见 | 日志 | 结果 |
| -- | -- | -- | -- | -- | -- |
| 收到委托书：发起者填写、收案员登记、部门审阅、项目负责人决策 | `case_acceptor`、`dept_leader`、`project_leader` | 已上传并归档 | 已保存 | 已记录 | 通过 |
| 发交费通知书及相关函件 | `project_assistant`、`project_leader`、`archivist` | 已上传并归档 | 已保存 | 已记录 | 通过，含退回和重新提交 |
| 编制内部质量控制文件及用章 | `project_assistant`、`project_leader`、`archivist` | 已上传并归档 | 已保存 | 已记录 | 通过 |
| 鉴定意见书送审稿编制 | `project_leader`、`project_assistant`、`tech_leader`、`dept_leader` | 已上传并归档 | 已保存 | 已记录 | 通过 |
| 出具鉴定意见书及用章 | `project_leader`、`project_assistant`、`archivist` | 已上传并归档 | 已保存 | 已记录 | 通过 |
| 归档 | `archivist`、`center_archivist` | 已上传并归档 | 已保存 | 已记录 | 通过 |

退回验证：`PROJECT_REVIEW` 节点由 `project_leader` 退回缴费函件，意见为“项目负责人退回：请补充交费通知书摘要。”；随后 `project_assistant` 在 `ASSISTANT_DRAFT` 节点重新提交，`project_leader` 再次审核通过。

## 页面与接口复验

- 页面发起：`case_acceptor` 从 `/case/new` 点击 `收到委托书` 创建真实草稿，生成案件 `2758`。
- 工作查询：`/work-query?keyword=OA-MAIN-20260614-001` 可检索到案件并进入详情。
- 办结只读：`/case/2758?mode=readonly&from=/work-query&fromLabel=工作查询` 显示 `只读查看`，无提交按钮，保留节点附件和流程图与日志。
- 附件下载：`archivist` 请求 `/api/files/62/download` 返回 200。
- 附件预览：`archivist` 请求 `/api/files/62/preview` 返回 200，预览内容带水印。
- 知识库归档：`case_acceptor` 请求 `/api/knowledge/documents?caseId=2758` 返回 44 条；文档 `37657` 下载和预览均返回 200。
- 未登录边界：未登录访问 `/api/cases/2758` 和 `/api/files/62/download` 均返回 401。
- 登录后数据范围：`business_staff` 可读取办结案件和知识文档，当前按工作查询/知识库开放策略记录为待补数据权限矩阵项。

截图证据：

- `docs/browser-validation/manual-acceptance/2026-06-14/02-mainline-work-query.png`
- `docs/browser-validation/manual-acceptance/2026-06-14/02-mainline-readonly-detail.png`

## 发现并修复的问题

| 编号 | 问题 | 范围 | 修复 | 复验 |
| -- | -- | -- | -- | -- |
| P02 | 动态表单 `caseNo` 未同步到 `case_info.case_no`，导致工作查询按业务案号追溯失败 | 手册范围 | 后端提交表单时同步 `caseNo`/`projectNo` 与委托人摘要字段；补回归断言；当前实例同步修正为 `OA-MAIN-20260614-001` | 工作查询按业务案号命中；后端专项测试通过 |
| P03 | 详情页动态表单默认值未合并已保存 `formData`，办结/查询详情存在字段丢失风险 | 手册范围 | 前端 `CaseDetail` 类型补 `formData`，详情页构建默认表单时合并历史表单数据 | 前端构建通过；办结只读详情显示业务案号和节点信息 |
| P04 | 浏览器自动化输入因本机 Browser Use 虚拟剪贴板缺失无法稳定 `fill/type` | 工具限制 | 本次节点办理改用真实业务账号 token 调用真实接口，页面保留发起、查询、只读复验；记录为剩余卡点 | 主链办结，但不能标为手册级 `[x]` |
| P05 | 知识库页面复验时页签处于未授权状态，页面显示 0 条 | 工具/会话限制 | 用有效业务账号接口复核知识文档、下载、预览；保留页面复验待补 | 接口返回 44 条；知识库页面仍需可登录页签复验 |

## 回归命令

- `cd judicial-appraisal-frontend && npm run build`
- `cd judicial-appraisal-backend && mvn test -Dtest=ManualAcceptanceWalkthroughVerificationTest`

两项均通过。

## 结论

主干业务引擎、附件、办理意见、流程日志、退回/重提、自动归档、工作查询和办结只读已完成一条真实实例闭环验证；但由于节点办理没有做到每一步都从真实页面待办表单输入提交，且知识库页面还需要可登录页签复验，本次状态为 `[~]`，暂不建议直接进入 19 个流程逐项手册级验收的 `[x]` 判定阶段。

## 02.5 补证

已执行 `02_5` 补证任务，详见 `docs/browser-validation/manual-acceptance/2026-06-14/02_5-supplemental-evidence.md`。

补证后：

- 页面输入问题定位为 Browser Use/in-app Browser 虚拟剪贴板限制，已改用系统 Chrome + Playwright 完成真实页面办理。
- 新建页面复验实例 `2775 / OA-PAGE-FINAL-20260614064446 / 2737`，覆盖真实页面发起、待办进入、表单填写、附件上传、办理意见、提交到下一节点，并推进至 `ASSISTANT_NOTICE`。
- 沿用页面实例 `2766` 补齐退回、退回后重新提交、重新审核通过页面证据。
- 沿用办结主实例 `2758 / OA-MAIN-20260614-001 / 2726` 补齐办结只读、工作查询详情、流程日志、节点附件和知识库归档文档页面复验。
- 修复 `form_data` 详情映射丢失、工作查询/详情数据权限过宽、案件归档知识文档越权、知识库批量下载入口缺失。
- `business_staff` 已不能读取无权案件 `2758`，也不能通过知识库读取该案件归档文档；`archivist` 仍可查看、预览、下载、批量下载归档文档。

补证后主干状态仍为 `[~]`，但已从“接口辅助办理为主”推进为“关键节点真实页面办理已验证”。可以进入 `03-all-19-flows.md`，进入后应继续使用 Playwright/Chrome 方式逐流程补齐全链路页面证据。
