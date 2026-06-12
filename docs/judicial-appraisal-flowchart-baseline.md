# 司法鉴定流程图验收基准

更新时间：2026-06-12

## 1. 定位

本文件登记当前项目纳入仓库的司法鉴定流程图。后续功能实现、运行时分支测试、验收矩阵更新，应优先对照这些流程图逐项跑通。

当流程图、使用手册和结构化需求出现差异时，处理顺序为：

1. `docs/司法鉴定系统使用手册.docx`
2. 本文件登记的主流程图与细化流程图
3. `docs/judicial-appraisal-manual-based-requirements-v2.md`
4. 当前代码与配置

## 2. 已纳入流程图

| 序号 | 文件 | 类型 | 用途 |
|---:|---|---|---|
| 1 | [judicial-appraisal-main-flow.png](flowcharts/judicial-appraisal-main-flow.png) | 主流程图 | 从委托开始到归档、财务报销、委托结束的总链路验收基准 |
| 2 | [judicial-appraisal-detailed-flow-overview.png](flowcharts/judicial-appraisal-detailed-flow-overview.png) | 细化流程总图 | 多个业务子流程的节点、角色、附件、退回与分支验收基准 |
| 3 | [final-opinion-review-detailed-flow.png](flowcharts/final-opinion-review-detailed-flow.png) | 细化流程图 | 鉴定意见书送审稿编制的项目辅助人、项目负责人、技术负责人、部门负责人串行审核验收基准 |

## 3. 后续验收要求

- 逐流程补测试时，必须先核对本文件中对应流程图的节点、角色、判断条件、退回线和输出文件。
- 已有 `ReceivedEntrustBranchVerificationTest`、`PreliminarySurveyBranchVerificationTest`、`PaymentNoticeBranchVerificationTest`、`QualityControlBranchVerificationTest` 可作为测试写法模板。
- 禁止仅凭流程定义已导入或主链 happy path 通过就标记完成；只有覆盖图中主要分支和退回路径的运行时测试通过后，才能在验收矩阵中标记 `[x]`。
- 对于主流程图中涉及但当前 19 流程矩阵未单列的节点，应先映射到最近的手册流程；确实无法映射时，在验收矩阵或执行计划中记录为差异项。

## 4. 当前覆盖状态

- 已按运行时分支测试完成：收到委托书、初步勘验、发交费通知书及相关函件、编制内部质量控制文件。
- 待继续按流程图补齐：现场勘验、材料接收与返还、鉴定意见书征求意见稿送审编制、鉴定意见书送审稿编制、出具鉴定意见书、法院函件、出庭、撤案、退费、终止鉴定、归档、用章、财务报销等。
