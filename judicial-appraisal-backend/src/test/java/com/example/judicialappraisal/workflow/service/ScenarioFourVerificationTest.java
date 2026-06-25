package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.caseinfo.service.CaseInfoService;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.platform.service.PlatformCatalogService;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScenarioFourVerificationTest {

    private static final Long OPERATOR_ID = 9L;
    private static final String OPERATOR_NAME = "管理员";

    @Autowired
    private JudicialConfigImportService judicialConfigImportService;

    @Autowired
    private PlatformCatalogService platformCatalogService;

    @Autowired
    private CaseInfoService caseInfoService;

    @Autowired
    private CaseInfoMapper caseInfoMapper;

    @Autowired
    private WorkflowRuntimeService workflowRuntimeService;

    @Autowired
    private CaseTaskMapper caseTaskMapper;

    @Autowired
    private CaseSubflowInstanceMapper caseSubflowInstanceMapper;

    @Autowired
    private CaseWfInstanceMapper caseWfInstanceMapper;

    @Autowired
    private com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper wfDefinitionMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private com.example.judicialappraisal.knowledge.mapper.KnowledgeDirectoryMapper knowledgeDirectoryMapper;

    @Autowired
    private com.example.judicialappraisal.knowledge.mapper.KnowledgeDocumentMapper knowledgeDocumentMapper;

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void runScenarioFour_E2E_Withdraw_Refund_Terminate_MailArchive() {
        System.out.println(">>> 开始运行场景四：撤案、退费、终止、邮寄入库归档 E2E 测试 <<<");

        // 1. 创建并推进主案件到正常办理阶段 (缴费完成，进入质控)
        String mainTitle = "场景4主案件";
        CaseInfo mainCase = caseInfoService.createDraft(new CaseCreateRequest(mainTitle, "工程造价", "深圳市龙岗区人民法院", 1L));
        Long mainCaseId = mainCase.getId();
        System.out.println("1. 成功创建主案件, ID=" + mainCaseId);
 
        CaseSubmitRequest submitRequest = new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起主流程");
        WorkflowActionResult submitResult = caseInfoService.submitCase(mainCaseId, submitRequest, OPERATOR_ID, OPERATOR_NAME);
        assertThat(submitResult.success()).isTrue();
        mainCase = caseInfoMapper.selectById(mainCaseId);
 
        Map<String, Object> initFormData = Map.ofEntries(
                Map.entry("clientName", "深圳市龙岗区人民法院"),
                Map.entry("serialNo", "SC-4-MAIN-123456"),
                Map.entry("initiatorName", "管理员"),
                Map.entry("initiatedDate", "2026-06-23"),
                Map.entry("receivedDate", "2026-06-23"),
                Map.entry("caseNo", "场景4主案件-" + System.currentTimeMillis()),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("projectAmount", 100000), // 10万
                Map.entry("appraisalMatter", "常规工程造价鉴定"),
                Map.entry("entrustAccepted", true),
                Map.entry("preliminarySurveyRequired", false),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L),
                Map.entry("materialReceiveRequired", false),
                Map.entry("filingDate", "2026-06-25"),
                Map.entry("undertakingLegalPerson", "张三"),
                Map.entry("institutionSelectionMethod", "随机抽取"),
                Map.entry("institutionSelectionTime", "2026-06-25"),
                Map.entry("applicantName", "原告张三"),
                Map.entry("respondentName", "被告李四")
        );
        completeTask(mainCaseId, "INIT_FILL", initFormData);
        completeTask(mainCaseId, "CLERK_REGISTER", Map.of());
        completeTask(mainCaseId, "DEPT_REVIEW", Map.of("entrustAccepted", true));
        completeTask(mainCaseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", false));
        completeTask(mainCaseId, "ASSISTANT_NOTICE", Map.of());

        // 推进 payment-notice 缴费通知子流程
        completeTask(mainCaseId, "ASSISTANT_DRAFT", Map.of(
                "letterDraftCompleted", true,
                "letterType", "交费通知书",
                "letterSummary", "10万交费通知书",
                "sealRequired", false,
                "sealedDocumentUploaded", true,
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ));
        completeTask(mainCaseId, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(mainCaseId, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", true));
        completeTask(mainCaseId, "PAYMENT_CONFIRM", Map.of("paymentReceived", true, "nextRecommendation", "编制内部质量控制文件"));
        System.out.println("2. 主案件已成功推进至缴费确认完成并已进入质控");

        // 2. 模拟发起撤案函 (创建新的关联撤案函案件)
        String withdrawTitle = "场景4：收到撤案退费终止归档";
        CaseInfo withdrawCase = caseInfoService.createDraft(new CaseCreateRequest(withdrawTitle, "收到撤案函", "深圳市龙岗区人民法院", 1L));
        Long withdrawCaseId = withdrawCase.getId();
        withdrawCase.setCaseNo("场景4：收到撤案退费终止归档-" + System.currentTimeMillis());
        withdrawCase.setCaseStatus(CaseStatus.PROCESSING.name());
        caseInfoMapper.updateById(withdrawCase);
        System.out.println("3. 成功创建撤案函件关联案件, ID=" + withdrawCaseId + ", 案号=" + withdrawCase.getCaseNo());

        // 启动 withdraw-case-letter 流程
        com.example.judicialappraisal.workflow.entity.WfDefinition definition = wfDefinitionMapper.selectOne(
                new LambdaQueryWrapper<com.example.judicialappraisal.workflow.entity.WfDefinition>()
                        .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getWfCode, "withdraw-case-letter")
                        .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getPublishStatus, "published")
                        .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getDeleted, 0)
                        .orderByDesc(com.example.judicialappraisal.workflow.entity.WfDefinition::getVersionNo)
                        .last("LIMIT 1"));

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setCaseId(withdrawCaseId);
        wfInstance.setWfId(definition.getId());
        wfInstance.setWfCode(definition.getWfCode());
        wfInstance.setWfName(definition.getWfName());
        wfInstance.setStatus("running");
        wfInstance.setCurrentNodeCode("START");
        wfInstance.setCurrentNodeName("开始");
        wfInstance.setStartedBy(OPERATOR_ID);
        wfInstance.setStartedTime(java.time.LocalDateTime.now());
        caseWfInstanceMapper.insert(wfInstance);

        CaseTask task = new CaseTask();
        task.setCaseId(withdrawCaseId);
        task.setWfInstanceId(wfInstance.getId());
        task.setNodeInstanceId(1L);
        task.setTaskType("single");
        task.setTaskTitle("开始任务");
        task.setNodeCode("START");
        task.setNodeName("开始");
        task.setStatus("pending");
        task.setAssigneeId(OPERATOR_ID);
        task.setAssigneeName(OPERATOR_NAME);
        task.setStartedTime(java.time.LocalDateTime.now());
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        // START 节点，填入关联案件的案号和项目负责人
        completeTask(withdrawCaseId, "START", Map.of(
                "linkedWorkflowCode", mainCase.getCaseNo(),
                "projectLeaderId", 3L
        ));
        System.out.println("4. 成功开启撤案流程，并关联主案号: " + mainCase.getCaseNo());

        // 登记撤案函 (LETTER_REGISTER)
        completeTask(withdrawCaseId, "LETTER_REGISTER", Map.of(
                "withdrawLetterReceivedDate", "2026-06-22",
                "withdrawReason", "撤回鉴定申请，退款处理"
        ));

        // 项目负责人判断是否退费 (PROJECT_DECISION)，选择需要退费
        completeTask(withdrawCaseId, "PROJECT_DECISION", Map.of(
                "refundRequired", true,
                "decisionSummary", "撤案退费申请已确认，需要退费"
        ));
        System.out.println("5. 项目负责人决策需要退费，流程自动触发并进入“退费”子流程");

        // 3. 推进退费 (refund) 子流程
        // 项目负责人完成合同变更与收入确认 (PROJECT_PREPARE)
        completeTask(withdrawCaseId, "PROJECT_PREPARE", Map.of(
                "contractChangeCompleted", true,
                "revenueConfirmed", true
        ));

        // 档案管理员登记退费申请 (ARCHIVIST_APPLY)
        completeTask(withdrawCaseId, "ARCHIVIST_APPLY", Map.of(
                "refundApplicationSubmitted", true
        ));

        // 财务人员付款 (FINANCE_PAYMENT)
        completeTask(withdrawCaseId, "FINANCE_PAYMENT", Map.of(
                "paymentCompleted", true,
                "paymentDate", "2026-06-22"
        ));
        System.out.println("6. 财务退费打款已完成，流程自动触发进入“终止鉴定”子流程");

        // 4. 推进终止鉴定 (terminate-appraisal) 子流程
        // 辅助人编制终止文书 (ASSISTANT_DRAFT)
        completeTask(withdrawCaseId, "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "当事人撤回鉴定，退款已结清，终止鉴定",
                "draftCompleted", true
        ));

        // 项目负责人审核终止文书 (PROJECT_REVIEW)并选择需要用章
        completeTask(withdrawCaseId, "PROJECT_REVIEW", Map.of(
                "projectReviewPassed", true,
                "sealRequired", true
        ));
        System.out.println("7. 终止鉴定文书审核通过，流程自动触发进入“用章申请”子流程");

        // 5. 用章申请子流程推进
        completeTask(withdrawCaseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "终止文书用章",
                "sealMode", "物理印章",
                "applicationFilesPrepared", true
        ));
        completeTask(withdrawCaseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(withdrawCaseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(withdrawCaseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
        System.out.println("8. 终止文书用章盖章流程已完成");

        // 回传终止文书盖章件并确认归档材料 (SEALED_UPLOAD)
        completeTask(withdrawCaseId, "SEALED_UPLOAD", Map.of(
                "sealedTerminationUploaded", true,
                "archiveConfirmed", true
        ));
        System.out.println("9. 终止盖章文书已回传，流程自动触发进入“归档”子流程");

        // 6. 推进归档 (archive) 子流程 - 邮寄入库分支
        // 档案管理员整理项目档案并选择“邮寄入库”
        completeTask(withdrawCaseId, "ARCHIVIST_PREPARE", Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "electronicArchiveLocation", "http://minio/archive/JA-SC4-WITHDRAW",
                "deliveryRoute", "邮寄入库"
        ));

        // 邮寄人员移交档案 (MAIL_TRANSFER)
        completeTask(withdrawCaseId, "MAIL_TRANSFER", Map.of(
                "mailTrackingNo", "SF16888899999"
        ));

        // 中心档案管理员审核并入库 (CENTRAL_REVIEW)
        completeTask(withdrawCaseId, "CENTRAL_REVIEW", Map.of(
                "centralArchiveApproved", true,
                "archiveRoomLocation", "南山档案库-C区-4排"
        ));
        System.out.println("10. 档案整理、邮寄移交及中心入库审核已全部完成");

        // Clean up the main case directory and documents so they do not show up in the knowledge base
        knowledgeDocumentMapper.delete(new LambdaQueryWrapper<com.example.judicialappraisal.knowledge.entity.KnowledgeDocument>()
                .eq(com.example.judicialappraisal.knowledge.entity.KnowledgeDocument::getCaseId, mainCaseId));
        knowledgeDirectoryMapper.delete(new LambdaQueryWrapper<com.example.judicialappraisal.knowledge.entity.KnowledgeDirectory>()
                .eq(com.example.judicialappraisal.knowledge.entity.KnowledgeDirectory::getCaseId, mainCaseId));

        // 7. 校验最终案件状态为已归档结案
        CaseInfo finalCase = caseInfoMapper.selectById(withdrawCaseId);
        assertThat(finalCase.getCaseStatus()).isEqualTo(CaseStatus.COMPLETED.name());
        System.out.println(">>> 自动化测试完美结束！撤案退费、终止用章、邮寄入库全链路已顺利流转至 COMPLETED (结案) <<<");

        // 将主案件状态也置为 TERMINATED 并取消 pending 任务，避免遗留脏数据在“我的工作”中显示
        CaseInfo mainCaseFinal = new CaseInfo();
        mainCaseFinal.setId(mainCaseId);
        mainCaseFinal.setCaseStatus(CaseStatus.TERMINATED.name());
        caseInfoMapper.updateById(mainCaseFinal);
        caseTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<CaseTask>()
                .set(CaseTask::getStatus, "cancelled")
                .eq(CaseTask::getCaseId, mainCaseId)
                .in(CaseTask::getStatus, "pending", "processing"));
        System.out.println(">>> 主案件 (ID=" + mainCaseId + ") 已被置为 TERMINATED (终止)，测试数据清理完成 <<<");
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        completeTask(caseId, nodeCode, ActionCode.APPROVE, formData, null);
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("未找到或未激活的任务节点: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "集成自动化测试流转", reason, null, null,
                null, null, formData, null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
    }

    private synchronized void ensureOperatorHasAllJudicialRoles() {
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员", "财务", "质量控制人", "中心档案管理员"));
        for (String roleName : roles) {
            SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, roleName));
            if (role == null) {
                try {
                    role = new SysRole();
                    role.setRoleName(roleName);
                    role.setRoleCode(roleName);
                    role.setStatus("enabled");
                    role.setDataScope("all");
                    role.setDeleted(0);
                    sysRoleMapper.insert(role);
                } catch (Exception e) {
                    role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getRoleCode, roleName));
                }
            }
            if (role != null) {
                SysUserRole userRole = sysUserRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, OPERATOR_ID)
                        .eq(SysUserRole::getRoleId, role.getId()));
                if (userRole == null) {
                    try {
                        userRole = new SysUserRole();
                        userRole.setUserId(OPERATOR_ID);
                        userRole.setRoleId(role.getId());
                        sysUserRoleMapper.insert(userRole);
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
    }
}
