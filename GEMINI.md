# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目。

## 📊 当前项目状态摘要 (2026-06-13)
- **核心目标**：严格按照《司法鉴定系统使用手册.docx》完美实现司法鉴定管理系统全部功能。
- **阶段**：第四阶段（司法鉴定手册级验收）是当前唯一主线。第五、第六阶段（通用 OA 模块）已标记为非必要历史规划。
- **验收基准**：手册 3.1-3.18 流程章节 + 3.15 财务报销，合计 **19 个验收流程**。
- **流程图基准**：`docs/judicial-appraisal-flowchart-baseline.md` 已登记 1 张主流程图和 2 张细化流程图，后续运行级测试主要按这些图逐项跑通节点、分支、退回、输出文件和父子流程关系。
- **核心成果**：
    - **底座就绪**：RBAC、动态表单/流程设计器、文件平台、知识库、审计与自动归档已全部就绪。
    - **项目瘦身 (2026-06-12)**：已完成文档清理，删除了 8 个历史/非必要文档（含《完整OA系统重构需求规格说明书》、旧设计方案等），整合了验收矩阵，当前仓库仅保留与使用手册相关的核心依据。
    - **高保真配置**：已基于旧版 20 流程矩阵完成配置导入，当前需按 19 个手册流程进行精准对齐。
    - **E2E 验证**：已通过主链 E2E 测试，验证了表单持久化、并行/汇聚、子流程挂起与父流程回写唤醒等核心能力。
    - **3.1 收到委托书验收完成**：`ReceivedEntrustBranchVerificationTest` 已按运行时真实任务流覆盖不受理、受理+初勘、受理+不初勘、受理+初勘+材料接收、受理+交费+材料接收 5 条分支；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.1 已标记 `[x]`。已执行 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest` 通过 5/5，`mvn test -Dtest=ReceivedEntrustBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 8/8。
    - **9.2 初步勘验验收完成**：`PreliminarySurveyBranchVerificationTest` 已按运行时真实任务流覆盖具备鉴定条件进入发交费通知、不具备鉴定条件进入终止鉴定、退回项目辅助人补充方案与设备记录 3 条路径；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.2 已标记 `[x]`。已执行 `mvn test -Dtest=PreliminarySurveyBranchVerificationTest` 通过 3/3；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 11/11。
    - **9.3 发交费通知书及相关函件验收完成**：`PaymentNoticeBranchVerificationTest` 已按运行时真实任务流覆盖需要用章、无需用章、已缴费进入编制内部质量控制文件、未缴费进入终止鉴定、项目负责人审核退回、缴费确认退回档案管理员 6 条路径；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.3 已标记 `[x]`。已执行 `mvn test -Dtest=PaymentNoticeBranchVerificationTest` 通过 6/6；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 17/17。
    - **9.4 编制内部质量控制文件验收完成**：`QualityControlBranchVerificationTest` 已按运行时真实任务流覆盖非 F 类进用章、F 类进部门负责人审核、部门负责人审核通过/退回、项目负责人审核退回、盖章件回传退回、现场勘验/材料接收与返还/征求意见稿送审稿/鉴定意见书送审稿/退费/终止鉴定 6 类后续流向，共 12 条路径；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.4 已标记 `[x]`。已执行 `mvn test -Dtest=QualityControlBranchVerificationTest` 通过 12/12，后端全量 `mvn test` 通过 105/105；前端 `npm run build` 通过。
    - **9.5 现场勘验验收完成**：`FieldSurveyBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件流转进入现场勘验、15 万以下直接转设备记录、15 万以上项目负责人/技术负责人/部门负责人逐级审核、项目负责人/技术负责人/部门负责人退回项目辅助人、项目负责人转交设备记录、项目辅助人上传设备出入库/使用/归还记录、项目负责人材料审核通过/退回，以及材料接收与返还、征求意见稿送审稿、鉴定意见书送审稿、退费、终止鉴定 5 类后续流向，共 13 条路径；同时校准 `field-survey` 流程配置，使技术负责人和部门负责人退回均回到项目辅助人，并补齐“项目负责人转交设备记录 -> 项目辅助人填写仪器设备相关表 -> 项目负责人审核上传材料”节点。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.5 已标记 `[x]`。已执行 `mvn test -Dtest=FieldSurveyBranchVerificationTest,JudicialConfigImportServiceTests` 通过 33/33；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 42/42；后端全量 `mvn test -q` 通过 118/118；前端 `npm run build` 通过。
    - **9.6 材料接收与返还验收完成**：`MaterialReceiveReturnBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件和现场勘验进入材料接收与返还、委托方直接提供并指定上传主办人、项目负责人确认材料与退回补充、需要补充材料进入发交费通知书及相关函件、项目辅助人登记介质和存放地址、需要返还与无需返还、返还/保管后并行进入归档和项目负责人后续判断，以及征求意见稿送审稿、鉴定意见书送审稿、退费、终止鉴定 4 类后续流向，共 11 条路径；同时校准 `material-receive-return` 流程配置，补齐 `MATERIAL_UPLOAD`、`PROJECT_MATERIAL_CONFIRM`、`ASSISTANT_RETURN`、`PARALLEL_GATEWAY_SPLIT`、`PAYMENT_NOTICE` 等节点，并修复 `WorkflowRuntimeService` 在最后活跃任务位于子流程时未将主案件置为完成的终态回写问题。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.6 已标记 `[x]`。已执行 `mvn test -Dtest=MaterialReceiveReturnBranchVerificationTest,JudicialConfigImportServiceTests` 通过 31/31；`mvn test -Dtest=MaterialReceiveReturnBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 14/14；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 53/53；后端全量 `mvn test -q` 通过 129/129；前端 `npm run build` 通过。
    - **9.7 鉴定意见书征求意见稿送审稿编制验收完成**：`DraftOpinionReviewBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件、现场勘验、材料接收与返还进入征求意见稿送审稿编制，项目负责人首节点转交项目辅助人，项目辅助人上传初稿，项目负责人、技术负责人、部门负责人三级串行审核，项目负责人/技术负责人/部门负责人审核退回，项目负责人定稿退回，以及项目负责人上传最终版本后进入出具征求意见稿，共 8 条路径；同时校准 `draft-opinion-review` 流程配置，补齐 `PROJECT_ASSIGN` 首节点，并将阶段性上传/审核字段从全表单必填改为节点流转测试保障，避免首节点被后续定稿字段阻断。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.7 已标记 `[x]`。已执行 `mvn test -Dtest=DraftOpinionReviewBranchVerificationTest,JudicialConfigImportServiceTests,ReceivedEntrustToArchiveE2ETest` 通过 31/31；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,DraftOpinionReviewBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 61/61；后端全量 `mvn test -q` 通过 137/137；前端 `npm run build` 通过。

## 📝 Gemini CLI 历史完成的工作 (当前目标相关)
1. **RBAC 实施**：重构了系统的鉴权体系，支持行级数据权限。
2. **菜单动态化**：实现了由后端驱动的动态菜单体系。
3. **动态平台底座**：实现了流程/表单设计器的版本化管理及运行时引擎。
4. **高保真配置**：完成了司法鉴定流程的首轮高保真表单与流程配置。

## 🎯 下一步任务 (严格对齐手册)

### A. 页面与功能验证 (依据 docs/judicial-appraisal-verification-prompt.md)
- **穷尽式流转**：按照提示词要求，对 19 个流程进行全分支、全角色、全路径验证。
- **数据一致性**：重点核查附件去重、知识库归档、审计日志以及查询报表的准确性。
- **真实样板对比**：使用原 OA 案件数据进行等价性校验，确保操作路径与业务结果一致。

### B. 19 个流程运行验收 (3.1 - 3.18 + 财务报销)
- **3.1 收到委托书**：验证发起、收案、审阅、决策及分支流转。
- **3.2 不予受理**：验证不予受理通知书编制、审核、用章、送达。
- **3.3 初步勘验**：验证勘验记录、设备出入库、鉴定条件判断。
- **3.4 发交费通知书**：验证函件编制、审核、用章、缴费确认。
- **3.5 编制内部质量控制文件**：验证质控文件、F类判断、多级审核、用章。
- **3.6 现场勘验**：验证勘验记录、设备记录、金额阈值审核。
- **3.7 材料接收与返还**：验证材料登记、补材通知、返还处理。
- **3.8 送审稿编制**：验证初稿、三级审核不可合并、定稿流向。
- **3.9 出具鉴定意见书**：验证承诺书、并行盖章/开票、送达归档。
- **3.10 收到法院函件**：验证函件登记、异议判断、回复函、用章。
- **3.11 收到出庭通知**：验证出庭费、调档、准备、登记、整理。
- **3.12 收到撤案函**：验证撤案登记、退费/终止判断。
- **3.13 终止鉴定**：验证终止函编制、审核、用章、归档。
- **3.14 归档**：验证邮寄入库、中心审核、正式入库。
- **3.15 财务报销**：验证报销登记、财务处理、支付确认。
- **3.16 收到征求意见稿**：验证说明函、用章、送达、反馈接收。
- **3.17 退费流程**：验证合同变更、申请、打款确认。
- **3.18 用章流程**：验证申请、审核、盖章扫描件回传。
- **财务报销 (独立流程)**：验证手册 3.15 提到的报销环节。

## 📊 历史已做但当前非必要范围
- **合同管理 MVP**：已实现 `contract_info` 等三张表，支持创建、版本、附件、审批流转、归档与审计。当前作为技术样板保留，不作为司法鉴定系统验收的必要指标。
- **CRM/项目管理/人事等**：当前仅有结构化看板/示例台账，已暂停纵深开发。

## 📝 协作约定 (2026-06-12 更新)
- **最高依据**：`docs/司法鉴定系统使用手册.docx`。
- **流程图依据**：`docs/judicial-appraisal-flowchart-baseline.md` 及 `docs/flowcharts/` 中的主流程图、细化流程图。
- **口径统一**：不再强调“完整 OA”或“不要缩小成小系统”。
- **验收标准**：完美符合使用手册，不追求超出手册的通用性。
