# docs 目录索引

本目录已按“手册范围验收”瘦身整理。日常开发、验收和结项判断只应从本索引列出的活跃文件进入；旧 OA 样本、历史归档、临时验收截图和下载物已清理。

## 活跃入口

| 文件/目录 | 用途 |
| -- | -- |
| `司法鉴定系统使用手册.docx` | 最高业务依据。所有页面、流程、角色、附件、归档、查询和报表要求以此为准。 |
| `manual-scope-acceptance-template.md` | 唯一总体验收模板。覆盖全部手册范围流程、公共功能、失败即修复规则和结项判断。 |
| `judicial-appraisal-manual-based-requirements-v2.md` | 手册内容的结构化需求说明，用于快速定位页面、流程和公共功能要求。 |
| `judicial-appraisal-master-checklist.md` | 当前详细执行清单和完成度状态。 |
| `judicial-appraisal-flow-verification-matrix.md` | 19 个流程和公共功能的验收矩阵。 |
| `judicial-appraisal-flowchart-baseline.md` | 流程图基准索引，说明三张流程图的使用顺序。 |
| `flowcharts/` | 主流程图、细化流程总图、鉴定意见书送审稿专项流程图。 |

## 状态口径

- `[x]`：必须有非 admin 业务账号真实页面验收，覆盖待办、办理、附件、意见、日志、退回、办结只读、工作查询和必要分支。
- `[~]`：只有配置、入口、后端运行时测试或部分页面证据，仍缺手册级闭环。
- `[ ]`：未实现、未验收或没有有效运行级证据。

## 执行规则

1. 新增验收必须优先填写或更新 `manual-scope-acceptance-template.md`。
2. 验收不通过且属于手册范围时，必须当场修复并复验，不能只放入“后续优化”。
3. 标记 `[x]` 前必须同步更新 `checklist.md`、`judicial-appraisal-master-checklist.md`、`judicial-appraisal-flow-verification-matrix.md` 和 `GEMINI.md`。
4. 临时验收截图、下载物和旧系统样本不再入库；必要证据优先沉淀为可复验步骤、测试或脱敏文本记录。
