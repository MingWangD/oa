# 手册级验收前置清点记录

验收日期：2026-06-14

## 依据

- `docs/司法鉴定系统使用手册.docx`
- `docs/manual-scope-acceptance-template.md`
- `docs/judicial-appraisal-master-checklist.md`
- `docs/judicial-appraisal-flow-verification-matrix.md`
- `docs/judicial-appraisal-manual-based-requirements-v2.md`
- `docs/original-oa-samples/`
- `checklist.md`
- `GEMINI.md`
- `AGENT.md`

有效入口为上述文件。`docs/archive/` 只作为历史参考，不作为当前手册范围验收口径。

## 环境

- 后端：已有服务监听 `http://127.0.0.1:8080`，配置来自 `judicial-appraisal-backend/src/main/resources/application.yml`。
- 前端：已有 Vite 服务监听 `http://127.0.0.1:5173`，代理目标默认为 `http://127.0.0.1:8080`。
- MySQL：`127.0.0.1:3307/judicial_appraisal`。
- Redis：`127.0.0.1:6379`。
- MinIO：`http://127.0.0.1:9000`。

## 账号检查

接口复验 10 个业务账号均可使用密码 `123456` 登录，且均可访问流程中心、我的工作、工作查询、数据报表、知识库菜单。

| 账号 | 角色 | 登录 | 可发起流程数 |
|---|---|---:|---:|
| `case_acceptor` | 收案员、收件人、申请人、发起人 | 通过 | 5 |
| `project_leader` | 项目负责人、申请人、发起人 | 通过 | 17 |
| `project_assistant` | 项目辅助人 | 通过 | 13 |
| `dept_leader` | 部门负责人 | 通过 | 5 |
| `tech_leader` | 技术负责人 | 通过 | 3 |
| `director_review` | 审阅所长 | 通过 | 0 |
| `archivist` | 档案管理员、盖章经办人、邮寄人员 | 通过 | 12 |
| `center_archivist` | 中心档案管理员 | 通过 | 12 |
| `business_staff` | 综合业务部、申请人、发起人 | 通过 | 1 |
| `finance` | 财务、申请人、发起人 | 通过 | 3 |

`director_review` 暂无可手动发起流程，按当前角色定位理解为待办处理角色，后续主干/分流程验收仍需验证其真实待办菜单与办理页面。

## 流程配置检查

`/api/platform/judicial-catalog/verification` 返回 `expected=19 checked=19 passed=19 failed=0`。19 个流程均已发布、有关联表单、有开始/结束/可办理节点、退回路径和结束路径。当前状态仍按总口径记为 `[~]`，原因是尚未完成全流程真实页面闭环验收。

## 公共能力检查

已通过 `case_acceptor` 接口或浏览器检查：

- `/api/auth/me`、菜单、可发起流程、工作台摘要、待办、办结、工作查询、知识库目录、知识库文档均返回成功。
- 附件上传、下载、预览接口成功；本次临时上传文件 `fileId=25`，下载 24 字节，预览返回带水印内容 102 字节。
- 数据报表入口发现并修复前端映射问题，复验 `/placeholder/workflow/report` 页面显示“报表中心”，无“暂不支持的业务台账模块”错误。

截图证据：

- `docs/browser-validation/manual-acceptance/2026-06-14/01-precheck-report-center.png`

## 原 OA 导出数据

优先确认 `docs/original-oa-samples/exports/` 下 Excel 导出文件。CSV/JSON/metadata/raw 可用于字段、流程名称、状态、查询口径对照。

低样本流程：退费 1 条、财务报销 1 条、收到出庭通知 1 条、收到撤案函 2 条、收到法院其他函件 3 条。可用于对照，但后续全分支页面试跑需要在新系统内补充最小测试数据。

## 修复与复验

| 编号 | 问题 | 范围 | 修复 | 复验 |
|---|---|---|---|---|
| P01 | 数据报表菜单 `/placeholder/workflow/report` 未映射到可用台账模块，可能请求不支持模块 | 手册范围，公共功能 2.4 | 在 `PlaceholderView.vue` 增加数据报表 section 和 `report-center` 映射 | `npm run build` 通过；浏览器打开页面显示“报表中心”，无错误 |

## 结论

系统已具备进入“主干流程试跑”的基础条件：服务可访问、10 个业务账号可登录、19 个流程基础配置通过、导出样本可用。不能进入结项判断；后续仍需按手册逐流程补齐真实页面闭环、附件/意见/日志/退回/办结只读/查询/归档/权限隔离验收。
