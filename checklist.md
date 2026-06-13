# 司法鉴定系统 checklist

更新时间：2026-06-13

本文件是根目录快速总览。详细执行项见：

- `docs/judicial-appraisal-master-checklist.md`
- `docs/judicial-appraisal-flow-verification-matrix.md`
- `docs/browser-validation-record-2026-06-13.md`

状态口径：

- `[x]`：已按使用手册与流程图完成运行级测试；涉及页面时已有浏览器验证或不需要前端页面作为完成条件。
- `[~]`：配置、入口或部分链路已具备，但还缺完整运行级分支测试、真实页面验证或手册要求的关键能力。
- `[ ]`：尚未开始或仍需从流程定义、运行测试、页面验证补齐。

## 总览表

| 序号 | 手册章节 | 流程/能力 | 当前状态 | 证据与下一步 |
|---:|---|---|---|---|
| 1 | 公共功能 | 多角色登录与非 admin 真实页面验收 | [x] | 已用 `case_acceptor`、`dept_leader`、`project_leader`、`finance`、`archivist` 验证，记录见 `docs/browser-validation-record-2026-06-13.md` |
| 2 | 公共功能 | 新建工作按当前角色展示 | [x] | 角色筛选已去掉；不同业务账号看到不同流程列表 |
| 3 | 公共功能 | 我的工作待办/办结可打开 | [x] | 待办进入 `mode=handle`；办结进入 `mode=readonly`；均携带 `taskId` |
| 4 | 公共功能 | 工作查询详情与草稿删除 | [x] | 浏览器验证“查看详情”和“删除草稿”成功 |
| 5 | 公共功能 | 数据报表 | [ ] | 手册要求的报表展示、导出方式、分页条数仍待实现/验收 |
| 6 | 公共功能 | 知识库页面与批量下载 | [~] | 后端归档与知识库基础存在；页面、批量下载、打开/下载细节仍需验收 |
| 7 | 9.1 | 收到委托书 | [x] | `ReceivedEntrustBranchVerificationTest`、`ManualAcceptanceWalkthroughVerificationTest`、浏览器主流程片段 |
| 8 | 9.2 | 初步勘验 | [x] | `PreliminarySurveyBranchVerificationTest` |
| 9 | 9.3 | 发交费通知书及相关函件 | [x] | `PaymentNoticeBranchVerificationTest` |
| 10 | 9.4 | 编制内部质量控制文件 | [x] | `QualityControlBranchVerificationTest` |
| 11 | 9.5 | 现场勘验 | [x] | `FieldSurveyBranchVerificationTest` |
| 12 | 9.6 | 材料接收与返还 | [x] | `MaterialReceiveReturnBranchVerificationTest` |
| 13 | 9.7 | 鉴定意见书征求意见稿送审编制 | [x] | `DraftOpinionReviewBranchVerificationTest` |
| 14 | 9.8 | 鉴定意见书送审稿编制 | [x] | `FinalOpinionReviewBranchVerificationTest`，已对照专项流程图 |
| 15 | 9.9 | 出具鉴定意见书 | [x] | `IssueOpinionBranchVerificationTest` |
| 16 | 9.10 | 出具征求意见稿 | [x] | `IssueDraftOpinionBranchVerificationTest` |
| 17 | 9.11 | 收到法院其他函件（含异议函） | [x] | `CourtLetterBranchVerificationTest` |
| 18 | 9.12 | 收到出庭通知 | [x] | `CourtAppearanceBranchVerificationTest` |
| 19 | 9.13 | 不予受理 | [x] | `RejectAcceptanceBranchVerificationTest` |
| 20 | 9.14 | 收到撤案函 | [x] | `WithdrawCaseBranchVerificationTest` |
| 21 | 9.15 | 退费流程 | [~] | 上游触发 `refund` 已验证；仍需完整跑合同变更、收入确认、退费申请、财务打款、进入终止鉴定 |
| 22 | 9.16 | 财务报销 | [ ] | `finance` 账号新建工作可见入口已验证；独立流程运行级测试与页面验收待补 |
| 23 | 9.17 | 终止鉴定 | [~] | 多个上游流程可触发 `terminate-appraisal`；内部完整节点待补 |
| 24 | 9.18 | 归档 | [~] | 归档基础和多个入口已存在；邮寄入库、中心审核、档案室存放待补 |
| 25 | 9.19 | 用章流程/用章申请表 | [~] | 多个上游流程已触发用章子流程；独立发起、父流程等待、盖章扫描件回传专项验收待补 |

## 下一步顺序

| 优先级 | 任务 | 状态 |
|---:|---|---|
| 1 | 补 9.15 退费流程完整运行级测试 | [ ] |
| 2 | 补 9.17 终止鉴定完整运行级测试 | [ ] |
| 3 | 补 9.18 归档完整运行级测试和知识库页面验收 | [ ] |
| 4 | 补 9.19 用章申请表独立发起与父流程等待专项验收 | [ ] |
| 5 | 补 9.16 财务报销独立流程 | [ ] |
| 6 | 补数据报表、批量下载、流程图视图、经办人只读通知模型 | [ ] |

## 提交前必跑

```bash
cd judicial-appraisal-backend
mvn test

cd ../judicial-appraisal-frontend
npm install
npm run build
```
