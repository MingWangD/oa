# 手册级验收五步提示词

本目录保存司法鉴定管理系统结项验收的五步提示词。五份提示词按顺序执行，用于从验收前置清点推进到最终结项审计。

## 使用顺序

1. `01-precheck.md`：验收前置清点
2. `02-mainline-flow.md`：主干流程试跑
3. `03-all-19-flows.md`：19 个流程逐项验收
4. `04-common-functions-permissions.md`：公共功能和权限专项验收
5. `05-final-closure-audit.md`：最终结项审计

## 数据要求

项目中已有原 OA 导出数据，应作为验收测试和等价性对照的优先数据源：

- `docs/original-oa-samples/exports/`：Excel 导出文件。
- `docs/original-oa-samples/csv-preview/`：Excel 的 CSV 预览。
- `docs/original-oa-samples/json/`：按流程整理后的 JSON 数据。
- `docs/original-oa-samples/metadata/`：导出清单、字段头和汇总信息。
- `docs/original-oa-samples/raw/`：原始接口响应样本。

执行提示词时，不应凭空造测试数据；应优先从上述导出数据中选取案件、流程名称、字段、状态和查询口径作为测试依据。只有导出数据不足以覆盖某个场景时，才补充最小必要测试数据，并在输出结果中说明原因。
