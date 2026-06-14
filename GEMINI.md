# Gemini CLI 协作与进度对齐

本文档由 Gemini CLI 维护，用于记录其工作进度、关键决策以及为其它协作 AI（如 Codex）提供的上下文信息。

## 🤖 角色定位
Gemini CLI 当前作为**辅助协作**身份运行，负责协助 Codex 推进项目。

## 📊 当前项目状态摘要 (2026-06-14)
- **核心目标**：严格按照《司法鉴定系统使用手册.docx》完美实现司法鉴定管理系统全部功能。
- **阶段**：第四阶段（司法鉴定手册级验收）是当前唯一主线。第五、第六阶段（通用 OA 模块）已标记为非必要历史规划。
- **验收基准**：手册 3.1-3.18 流程章节 + 3.15 财务报销，合计 **19 个验收流程**。
- **流程图基准**：`docs/judicial-appraisal-flowchart-baseline.md` 已登记 1 张主流程图和 2 张细化流程图，后续运行级测试主要按这些图逐项跑通节点、分支、退回、输出文件和父子流程关系。
- **2026-06-14 Codex 反查校准**：此前多个完成标记只能证明后端运行时测试、流程配置或子流程触发，不能证明《司法鉴定系统使用手册》级完成。手册级完成必须有非 admin 业务账号真实页面验收，覆盖待办办理、办结只读、工作查询详情、附件、办理意见、流程日志、退回和必要分支。`checklist.md`、`docs/judicial-appraisal-master-checklist.md`、`docs/judicial-appraisal-flow-verification-matrix.md` 已按此口径降级。
- **2026-06-14 Codex 主干试跑**：真实实例 `2758` 使用 `case_acceptor` 页面发起 `收到委托书`，并由 `dept_leader`、`project_leader`、`project_assistant`、`tech_leader`、`archivist`、`center_archivist` 等真实业务账号 API 辅助办理至 `COMPLETED`，覆盖附件、办理意见、流程日志、退回/重提、用章、归档、工作查询和办结只读。已修复业务案号未同步到 `case_info.case_no`、详情页未合并历史 `formData` 两个手册范围问题；因浏览器输入工具无法稳定在节点页面填写提交，状态仍为 `[~]`。
- **2026-06-14 Codex 02.5 公共功能补证**：已补真实页面待办进入、表单填写、附件上传、办理意见、提交、退回/重提、工作查询详情、办结只读、知识库归档可见、预览、单下载、批量下载，以及 `business_staff` 对 2758 案件和知识归档的权限隔离。只能据此将知识库批量下载等有证据项升级；数据报表、保存不触发流转、流程图入口、会签意见、独立经办人只读通知模型、页面主干全链路后半段仍无手册级证据，继续保持 `[ ]` 或 `[~]`。
- **核心成果**：
    - **底座部分就绪**：RBAC、动态表单/流程设计器、文件平台、知识库、审计与自动归档有后端基础；知识库页面与批量下载已有 02.5 页面证据，手动上传、文件夹点击细项、流程归档可视化和公共页面闭环仍需手册级验收。
    - **项目瘦身 (2026-06-12)**：已完成文档清理，删除了 8 个历史/非必要文档（含《完整OA系统重构需求规格说明书》、旧设计方案等），整合了验收矩阵，当前仓库仅保留与使用手册相关的核心依据。
    - **高保真配置**：已基于旧版 20 流程矩阵完成配置导入，当前需按 19 个手册流程进行精准对齐。
    - **E2E 验证**：已通过主链 E2E 测试，验证了表单持久化、并行/汇聚、子流程挂起与父流程回写唤醒等核心能力。
    - **3.1 收到委托书后端运行级测试完成（非手册级）**：`ReceivedEntrustBranchVerificationTest` 已按运行时真实任务流覆盖不受理、受理+初勘、受理+不初勘、受理+初勘+材料接收、受理+交费+材料接收 5 条分支；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.1 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest` 通过 5/5，`mvn test -Dtest=ReceivedEntrustBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 8/8。
    - **9.2 初步勘验后端运行级测试完成（非手册级）**：`PreliminarySurveyBranchVerificationTest` 已按运行时真实任务流覆盖具备鉴定条件进入发交费通知、不具备鉴定条件进入终止鉴定、退回项目辅助人补充方案与设备记录 3 条路径；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.2 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=PreliminarySurveyBranchVerificationTest` 通过 3/3；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 11/11。
    - **9.3 发交费通知书及相关函件后端运行级测试完成（非手册级）**：`PaymentNoticeBranchVerificationTest` 已按运行时真实任务流覆盖需要用章、无需用章、已缴费进入编制内部质量控制文件、未缴费进入终止鉴定、项目负责人审核退回、缴费确认退回档案管理员 6 条路径；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.3 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=PaymentNoticeBranchVerificationTest` 通过 6/6；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 17/17。
    - **9.4 编制内部质量控制文件后端运行级测试完成（非手册级）**：`QualityControlBranchVerificationTest` 已按运行时真实任务流覆盖非 F 类进用章、F 类进部门负责人审核、部门负责人审核通过/退回、项目负责人审核退回、盖章件回传退回、现场勘验/材料接收与返还/征求意见稿送审稿/鉴定意见书送审稿/退费/终止鉴定 6 类后续流向，共 12 条路径；`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.4 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=QualityControlBranchVerificationTest` 通过 12/12，后端全量 `mvn test` 通过 105/105；前端 `npm run build` 通过。
    - **9.5 现场勘验后端运行级测试完成（非手册级）**：`FieldSurveyBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件流转进入现场勘验、15 万以下直接转设备记录、15 万以上项目负责人/技术负责人/部门负责人逐级审核、项目负责人/技术负责人/部门负责人退回项目辅助人、项目负责人转交设备记录、项目辅助人上传设备出入库/使用/归还记录、项目负责人材料审核通过/退回，以及材料接收与返还、征求意见稿送审稿、鉴定意见书送审稿、退费、终止鉴定 5 类后续流向，共 13 条路径；同时校准 `field-survey` 流程配置，使技术负责人和部门负责人退回均回到项目辅助人，并补齐“项目负责人转交设备记录 -> 项目辅助人填写仪器设备相关表 -> 项目负责人审核上传材料”节点。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.5 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=FieldSurveyBranchVerificationTest,JudicialConfigImportServiceTests` 通过 33/33；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 42/42；后端全量 `mvn test -q` 通过 118/118；前端 `npm run build` 通过。
    - **9.6 材料接收与返还后端运行级测试完成（非手册级）**：`MaterialReceiveReturnBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件和现场勘验进入材料接收与返还、委托方直接提供并指定上传主办人、项目负责人确认材料与退回补充、需要补充材料进入发交费通知书及相关函件、项目辅助人登记介质和存放地址、需要返还与无需返还、返还/保管后并行进入归档和项目负责人后续判断，以及征求意见稿送审稿、鉴定意见书送审稿、退费、终止鉴定 4 类后续流向，共 11 条路径；同时校准 `material-receive-return` 流程配置，补齐 `MATERIAL_UPLOAD`、`PROJECT_MATERIAL_CONFIRM`、`ASSISTANT_RETURN`、`PARALLEL_GATEWAY_SPLIT`、`PAYMENT_NOTICE` 等节点，并修复 `WorkflowRuntimeService` 在最后活跃任务位于子流程时未将主案件置为完成的终态回写问题。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.6 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=MaterialReceiveReturnBranchVerificationTest,JudicialConfigImportServiceTests` 通过 31/31；`mvn test -Dtest=MaterialReceiveReturnBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 14/14；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 53/53；后端全量 `mvn test -q` 通过 129/129；前端 `npm run build` 通过。
    - **9.7 鉴定意见书征求意见稿送审稿编制后端运行级测试完成（非手册级）**：`DraftOpinionReviewBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件、现场勘验、材料接收与返还进入征求意见稿送审稿编制，项目负责人首节点转交项目辅助人，项目辅助人上传初稿，项目负责人、技术负责人、部门负责人三级串行审核，项目负责人/技术负责人/部门负责人审核退回，项目负责人定稿退回，以及项目负责人上传最终版本后进入出具征求意见稿，共 8 条路径；同时校准 `draft-opinion-review` 流程配置，补齐 `PROJECT_ASSIGN` 首节点，并将阶段性上传/审核字段从全表单必填改为节点流转测试保障，避免首节点被后续定稿字段阻断。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.7 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=DraftOpinionReviewBranchVerificationTest,JudicialConfigImportServiceTests,ReceivedEntrustToArchiveE2ETest` 通过 31/31；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,DraftOpinionReviewBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 61/61；后端全量 `mvn test -q` 通过 137/137；前端 `npm run build` 通过。
    - **9.8 鉴定意见书送审稿编制后端运行级测试完成（非手册级）**：已额外对照 `docs/flowcharts/final-opinion-review-detailed-flow.png` 专项细化流程图；`FinalOpinionReviewBranchVerificationTest` 已按运行时真实任务流覆盖从编制内部质量控制文件、现场勘验、材料接收与返还进入鉴定意见书送审稿编制，项目负责人首节点确认并转交项目辅助人，项目辅助人上传初稿，项目负责人、技术负责人、部门负责人三级串行审核，项目负责人/技术负责人/部门负责人审核退回，项目负责人定稿退回，以及项目负责人上传最终送审稿后进入出具鉴定意见书，共 8 条路径；同时校准 `final-opinion-review` 流程配置，补齐 `PROJECT_ASSIGN` 首节点，将 `opinionDraftUploaded`、`versionAUploaded`、`versionABUploaded`、`versionABCUploaded`、`finalDraftUploaded` 等阶段性字段从全表单必填改为节点流转测试保障，避免首节点被后续版本字段阻断。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.8 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=FinalOpinionReviewBranchVerificationTest,JudicialConfigImportServiceTests,ReceivedEntrustToArchiveE2ETest` 通过 31/31；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,DraftOpinionReviewBranchVerificationTest,FinalOpinionReviewBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 69/69；后端全量 `mvn test -q` 通过 145/145；前端 `npm run build` 通过。
    - **9.9 出具鉴定意见书后端运行级测试完成（非手册级）**：`IssueOpinionBranchVerificationTest` 已按运行时真实任务流覆盖项目负责人首节点修改鉴定意见书、项目负责人转交项目辅助人、项目辅助人上传鉴定人承诺书和司法鉴定复核意见、项目负责人审核及退回、项目负责人审核通过后转档案管理员申请盖章和开票、档案管理员确认盖章申请并上传中电投系统编号登记表扫描件、用章申请表与申请开票并行、开票分支完成但用章未完成时不得提前汇聚、免开票、用章完成后项目辅助人上传盖章扫描件、盖章件退回，以及送达后进入归档，共 5 条路径；同时校准 `issue-opinion` 流程配置，将原 `PROJECT_SUPPLEMENT` 单节点拆为 `PROJECT_MODIFY -> ASSISTANT_UPLOAD -> PROJECT_REVIEW -> ARCHIVIST_CONFIRM -> PARALLEL_GATEWAY_SPLIT`，并将 `opinionModified`、`commitmentDrafted`、`reviewOpinionDrafted`、`projectReviewPassed`、`systemRegistrationUploaded`、`sealedOpinionUploaded`、`invoiceIssued`、`archiveConfirmed` 等阶段性字段从全表单必填改为节点流转测试保障。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.9 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=IssueOpinionBranchVerificationTest,JudicialConfigImportServiceTests,ReceivedEntrustToArchiveE2ETest,FinalOpinionReviewBranchVerificationTest` 通过 36/36；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,DraftOpinionReviewBranchVerificationTest,FinalOpinionReviewBranchVerificationTest,IssueOpinionBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 74/74；后端全量 `mvn test -q` 通过 150/150；前端 `npm run build` 通过。
    - **9.10 出具征求意见稿后端运行级测试完成（非手册级）**：`IssueDraftOpinionBranchVerificationTest` 已按运行时真实任务流覆盖项目辅助人首节点编制/上传鉴定说明函、项目负责人审核及退回、档案管理员确认盖章并上传征求意见稿、用章申请、项目辅助人上传盖章扫描件、归档与材料寄出并行、材料寄出退回、等待反馈、收到异议进入收到法院其他函件、无异议或未反馈进入鉴定意见书送审稿编制，共 6 条路径；同时校准 `issue-draft-opinion` 流程配置，将说明函编制、项目负责人审核、档案管理员确认上传、用章、盖章扫描件上传、归档/材料寄出并行、反馈分支拆为可运行节点，并将 `explainLetterDrafted`、`projectReviewPassed`、`draftOpinionUploaded`、`sealedDraftOpinionUploaded`、`archiveConfirmed`、`feedbackDecision` 等阶段性字段从全表单必填改为节点流转测试保障。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.10 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=IssueDraftOpinionBranchVerificationTest,JudicialConfigImportServiceTests,DraftOpinionReviewBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 37/37；组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,DraftOpinionReviewBranchVerificationTest,FinalOpinionReviewBranchVerificationTest,IssueOpinionBranchVerificationTest,IssueDraftOpinionBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 80/80；后端全量 `mvn test -q` 通过 156/156；前端 `npm run build` 通过。
    - **9.11 收到法院其他函件（含异议函）后端运行级测试完成（非手册级）**：`CourtLetterBranchVerificationTest` 已按运行时真实任务流覆盖手动新建并关联原流程、从出具征求意见稿收到异议自然流入、上传函件并选择项目负责人、项目负责人判断异议/非异议、异议回复、非异议需回复、非异议无需回复直接归档、项目负责人退回、档案管理员退回、档案管理员同意盖章并触发用章申请、项目辅助人上传盖章回复函、归档与发相关函件并行、后续进入鉴定意见书送审稿编制或出具鉴定意见书，共 7 条路径；同时校准 `court-letter` 流程配置，新增 `LETTER_UPLOAD` 首节点，替换原部门负责人审核为档案管理员盖章确认，将后置字段从全表单必填改为节点流转测试保障，并避免运行时不支持的组合条件表达式。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.11 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=CourtLetterBranchVerificationTest,JudicialConfigImportServiceTests,IssueDraftOpinionBranchVerificationTest` 通过 33/33。
    - **9.12 收到出庭通知后端运行级测试完成（非手册级）**：`CourtAppearanceBranchVerificationTest` 已按运行时真实任务流覆盖手动新建并关联原流程、项目负责人确认出庭通知和项目编号、出庭费通知复用发交费通知书及相关函件子流程、档案管理员上传档案借阅登记表、中心档案室线下调档留痕、非中心档案调档、项目负责人等待并上传出庭准备文件、进入出庭状态、出庭后整理材料、整理完成后归档、后续进入出具鉴定意见书、调档退回、出庭未完成退回，共 6 条路径；同时校准 `court-appearance` 流程配置，将 `FINANCE_NOTICE` 单节点替换为 `PAYMENT_NOTICE` 子流程调用，新增 `CENTER_ARCHIVE_RETRIEVAL` 留痕节点，并将出庭准备、出庭完成、出庭后整理等阶段性字段从全表单必填改为节点流转测试保障。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.12 历史曾标记 `[x]`，现已降级为 `[~]`。已执行 `mvn test -Dtest=CourtAppearanceBranchVerificationTest,JudicialConfigImportServiceTests` 通过 26/26；9.1-9.12 组合回归 `mvn test -Dtest=ReceivedEntrustBranchVerificationTest,PreliminarySurveyBranchVerificationTest,PaymentNoticeBranchVerificationTest,QualityControlBranchVerificationTest,FieldSurveyBranchVerificationTest,MaterialReceiveReturnBranchVerificationTest,DraftOpinionReviewBranchVerificationTest,FinalOpinionReviewBranchVerificationTest,IssueOpinionBranchVerificationTest,IssueDraftOpinionBranchVerificationTest,CourtLetterBranchVerificationTest,CourtAppearanceBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 通过 93/93；后端全量 `mvn test -q` 通过 169/169；前端 `npm run build` 通过。
    - **9.13 不予受理后端运行级测试完成（非手册级）**：`RejectAcceptanceBranchVerificationTest` 已按运行时真实任务流覆盖从收到委托书中不满足受理条件分支自动进入、项目辅助人编制不予受理通知书、项目负责人审核及退回、档案管理员盖章确认（包含用章申请子流程及无需用章分支）、项目辅助人上传盖章扫描版、进入归档等路径；同时校准 `reject-acceptance` 及 `seal-application` 流程表单配置，将 `rejectionReason`、`noticeDraftCompleted`、`projectReviewPassed`、`sealRequired`、`sealedNoticeUploaded`、`applicationReason`、`sealMode`、`applicationFilesPrepared` 等阶段性必填字段从全表单必填改为由流转测试保障，避免阻断首节点流转。`docs/judicial-appraisal-flow-verification-matrix.md` 对应 9.13 历史曾标记 `[x]`，现已降级为 `[~]`。已执行专项测试 `mvn test -Dtest=RejectAcceptanceBranchVerificationTest` 通过 2/2。
    - **9.14 收到撤案函及 9.15 退费流程后端证据存在，但手册级未完成**：`WithdrawCaseBranchVerificationTest` 已覆盖撤案登记、项目负责人判断、按需触发 `refund` 或 `terminate-appraisal`；`RefundBranchVerificationTest` 覆盖了退费部分后端链路。但测试使用管理员/Service 直调并存在手工构造任务记录，缺非 admin 业务账号真实页面、附件、办理意见、流程日志、办结只读和工作查询详情验收。9.14、9.15 当前应为 `[~]`。
    - **9.16 财务报销后端运行级测试完成（非手册级）**：`FinanceReimbursementBranchVerificationTest` 已验证 `expense-reimbursement` 独立流程，覆盖发起人提交报销材料、财务处理确认以及退回补充路径；并修改 `JudicialConfigImportService.java` 放宽了 `expenseSummary`、`expenseAmount` 等字段的全局必填以放行首节点。前端页面闭环和真实角色流转尚未完成。已执行 `mvn test -Dtest=FinanceReimbursementBranchVerificationTest` 专项通过，及 `mvn test -Dtest=JudicialConfigImportServiceTests,ReceivedEntrustBranchVerificationTest,PaymentNoticeBranchVerificationTest,FinanceReimbursementBranchVerificationTest,SealApplicationBranchVerificationTest,ArchiveBranchVerificationTest,ReceivedEntrustToArchiveE2ETest` 等组合回归全部通过。
    - **9.17 终止鉴定后端证据存在，但手册级未完成**：`TerminateAppraisalBranchVerificationTest` 覆盖部分上游触发、终止文书、审核、用章等待/唤醒和进入归档；但同样缺真实页面和非 admin 全链路验收，且当前配置初始节点与手册描述存在差异。当前应为 `[~]`。
    - **9.18 归档后端证据存在，但手册级未完成**：`ArchiveBranchVerificationTest` 覆盖部分邮寄/中心审核/正式入库路径；但知识库/档案库页面、归档附件、归档记录、归档后只读、工作查询和批量下载未完整验收。当前应为 `[~]`。
    - **9.19 用章流程/用章申请表后端证据存在，但手册级未完成**：`SealApplicationBranchVerificationTest` 覆盖后端独立发起、父流程等待与退回；但真实页面的独立发起、父流程触发、扫描件上传/回传、办结查看和工作查询详情未完整验收。当前应为 `[~]`。

## 📝 Gemini CLI 历史完成的工作 (当前目标相关)
1. **RBAC 实施**：重构了系统的鉴权体系，支持行级数据权限。
2. **菜单动态化**：实现了由后端驱动的动态菜单体系。
3. **动态平台底座**：实现了流程/表单设计器的版本化管理及运行时引擎。
4. **高保真配置**：完成了司法鉴定流程的首轮高保真表单与流程配置。

## 🎯 下一步任务 (严格对齐手册)

### A. 页面与功能验证 (依据 docs/manual-scope-acceptance-template.md)
- **业务账号登录已 curl 复验**：2026-06-14 `case_acceptor`、`project_leader`、`project_assistant`、`dept_leader`、`tech_leader`、`director_review`、`archivist`、`center_archivist`、`business_staff`、`finance` 均返回 `code=0`；下一步仍需补真实页面菜单和流程权限矩阵。
- **穷尽式流转**：按照提示词要求，对 19 个流程进行全分支、全角色、全路径验证。
- **页面闭环优先**：每个流程必须覆盖待办点开办理、附件上传/下载/预览、办理意见、流程日志、退回、办结只读、工作查询详情。
- **数据一致性**：重点核查附件去重、知识库归档、审计日志以及查询报表的准确性；02.5 已补一条 2758 知识库样例，查询报表仍无手册级证据。
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

## 📝 协作约定 (2026-06-14 更新)
- **最高依据**：`docs/司法鉴定系统使用手册.docx`。
- **流程图依据**：`docs/judicial-appraisal-flowchart-baseline.md` 及 `docs/flowcharts/` 中的主流程图、细化流程图。
- **口径统一**：不再强调“完整 OA”或“不要缩小成小系统”。
- **验收标准**：完美符合使用手册，不追求超出手册的通用性；不得把配置存在、子流程触发、测试类存在、Service 层测试通过或 API/MockMvc 通过直接等同于手册级完成。
