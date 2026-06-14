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
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.entity.CaseNodeInstance;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.mapper.CaseNodeInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ReceivedEntrustToArchiveE2ETest {

    @Autowired
    private JudicialConfigImportService judicialConfigImportService;

    @Autowired
    private CaseInfoService caseInfoService;

    @Autowired
    private CaseInfoMapper caseInfoMapper;

    @Autowired
    private WorkflowRuntimeService workflowRuntimeService;

    @Autowired
    private CaseNodeInstanceMapper caseNodeInstanceMapper;

    @Autowired
    private CaseTaskMapper caseTaskMapper;

    @Autowired
    private CaseWfInstanceMapper caseWfInstanceMapper;

    @Autowired
    private WfDefinitionMapper wfDefinitionMapper;

    @Autowired
    private CaseSubflowInstanceMapper caseSubflowInstanceMapper;

    @Autowired
    private com.example.judicialappraisal.organization.mapper.SysRoleMapper sysRoleMapper;

    @Autowired
    private com.example.judicialappraisal.organization.mapper.SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private com.example.judicialappraisal.platform.service.PlatformCatalogService platformCatalogService;

    @BeforeEach
    void setUp() {
        // Ensure roles exist in DB
        List<String> roles = new java.util.ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员"));
        for (String roleName : roles) {
            com.example.judicialappraisal.organization.entity.SysRole existing = sysRoleMapper.selectOne(
                    new LambdaQueryWrapper<com.example.judicialappraisal.organization.entity.SysRole>()
                            .eq(com.example.judicialappraisal.organization.entity.SysRole::getRoleCode, roleName));
            if (existing == null) {
                com.example.judicialappraisal.organization.entity.SysRole role = new com.example.judicialappraisal.organization.entity.SysRole();
                role.setRoleName(roleName);
                role.setRoleCode(roleName);
                role.setStatus("enabled");
                role.setDataScope("all");
                role.setDeleted(0);
                sysRoleMapper.insert(role);
            }
        }

        List<com.example.judicialappraisal.organization.entity.SysRole> enabledRoles = sysRoleMapper.selectList(
                new LambdaQueryWrapper<com.example.judicialappraisal.organization.entity.SysRole>()
                        .eq(com.example.judicialappraisal.organization.entity.SysRole::getStatus, "enabled")
                        .eq(com.example.judicialappraisal.organization.entity.SysRole::getDeleted, 0));
        for (com.example.judicialappraisal.organization.entity.SysRole role : enabledRoles) {
            com.example.judicialappraisal.organization.entity.SysUserRole existingUserRole = sysUserRoleMapper.selectOne(
                    new LambdaQueryWrapper<com.example.judicialappraisal.organization.entity.SysUserRole>()
                            .eq(com.example.judicialappraisal.organization.entity.SysUserRole::getUserId, 9L)
                            .eq(com.example.judicialappraisal.organization.entity.SysUserRole::getRoleId, role.getId()));
            if (existingUserRole == null) {
                com.example.judicialappraisal.organization.entity.SysUserRole userRole = new com.example.judicialappraisal.organization.entity.SysUserRole();
                userRole.setUserId(9L);
                userRole.setRoleId(role.getId());
                sysUserRoleMapper.insert(userRole);
            }
        }

        // Ensure configs are imported and force refresh
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void receivedEntrustToArchive_shouldCompleteFullBusinessChain() {
        // 1. Create Draft
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest("E2E真实长链路测试案件", "法医临床鉴定", "测试法院", 1L));
        Long caseId = caseInfo.getId();

        // 2. Submit Case
        CaseSubmitRequest submitRequest = new CaseSubmitRequest(9L, "管理员", "发起流程");
        WorkflowActionResult submitResult = caseInfoService.submitCase(caseId, submitRequest, 9L, "管理员");
        assertThat(submitResult.success()).isTrue();

        // Check main wf instance
        CaseWfInstance mainWf = caseWfInstanceMapper.selectOne(new LambdaQueryWrapper<CaseWfInstance>()
                .eq(CaseWfInstance::getCaseId, caseId)
                .eq(CaseWfInstance::getWfCode, "received-entrust")
                .eq(CaseWfInstance::getStatus, "running"));
        assertThat(mainWf).isNotNull();

        // received-entrust chain: INIT_FILL -> CLERK_REGISTER -> DEPT_REVIEW -> PROJECT_DECISION
        Map<String, Object> initFormData = Map.ofEntries(
                Map.entry("clientName", "E2E委托人"),
                Map.entry("serialNo", "2026-001"),
                Map.entry("initiatorName", "测试发起人"),
                Map.entry("initiatedDate", "2026-06-12"),
                Map.entry("receivedDate", "2026-06-12"),
                Map.entry("caseNo", "JA-11"),
                Map.entry("appraisalCategory", "法医临床鉴定"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "损伤程度鉴定"),
                Map.entry("entrustAccepted", true),
                Map.entry("preliminarySurveyRequired", false),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L),
                Map.entry("materialReceiveRequired", false)
        );
        completeTask(caseId, "INIT_FILL", initFormData);
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", true));
        
        // PROJECT_DECISION branches to ASSISTANT_NOTICE (parallel) and PAYMENT_NOTICE (subflow)
        // We set preliminarySurveyRequired = false, materialReceiveRequired = false to skip preliminary-survey and material-receive (which is an old node, not the subflow)
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", false));

        // Now we should have an active task for ASSISTANT_NOTICE and a subflow running for PAYMENT_NOTICE
        completeTask(caseId, "ASSISTANT_NOTICE", Map.of());

        // Check subflow instance for payment-notice
        CaseSubflowInstance paymentNoticeSubflow = getRunningSubflow(caseId, "payment-notice");
        assertThat(paymentNoticeSubflow).isNotNull();

        // payment-notice chain: ASSISTANT_DRAFT -> PROJECT_REVIEW -> SEAL_APPLICATION -> ARCHIVE_UPLOAD -> PAYMENT_CONFIRM -> QUALITY_CONTROL
        Map<String, Object> paymentNoticeFormData = Map.ofEntries(
                Map.entry("letterDraftCompleted", true),
                Map.entry("letterType", "交费通知书"),
                Map.entry("letterSummary", "E2E交费通知"),
                Map.entry("sealRequired", false),
                Map.entry("sealedDocumentUploaded", true), // satisfy validator for later nodes
                Map.entry("paymentReceived", true),        // satisfy validator for later nodes
                Map.entry("nextRecommendation", "编制内部质量控制文件")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", paymentNoticeFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(caseId, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", true));
        completeTask(caseId, "PAYMENT_CONFIRM", Map.of("paymentReceived", true));

        // Wait for quality-control subflow
        CaseSubflowInstance qualityControlSubflow = getRunningSubflow(caseId, "quality-control");
        assertThat(qualityControlSubflow).isNotNull();

        // quality-control chain: ASSISTANT_DRAFT -> PROJECT_REVIEW -> SEAL_APPLICATION -> SEALED_FILE_UPLOAD -> NEXT_FLOW_DECISION
        Map<String, Object> qualityControlFormData = Map.ofEntries(
                Map.entry("qualityFileDraftCompleted", true),
                Map.entry("qualityFileSummary", "E2E质量控制"),
                Map.entry("formatType", "非中心格式"),
                Map.entry("contractAmount", 10000),
                Map.entry("fClassProject", false),
                Map.entry("projectReviewPassed", true),
                Map.entry("projectReviewRoute", "进入用章"),
                Map.entry("sealedQualityFileUploaded", true), // satisfy validator
                Map.entry("nextRecommendation", "材料接收与返还")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", qualityControlFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "projectReviewRoute", "进入用章"));
        
        // seal-application subflow runs here
        CaseSubflowInstance qcSealSubflow = getRunningSubflow(caseId, "seal-application");
        assertThat(qcSealSubflow).isNotNull();
        // seal-application nodes: APPLICANT_SUBMIT -> ARCHIVIST_REVIEW -> SEAL_OPERATOR -> ARCHIVIST_UPLOAD -> END
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "质量控制文件用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true,
                "archivistReviewed", true, // satisfy validator
                "sealCompleted", true,     // satisfy validator
                "sealedScanUploaded", true  // satisfy validator
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));

        // Back to quality-control
        completeTask(caseId, "SEALED_FILE_UPLOAD", Map.of("sealedQualityFileUploaded", true));
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "材料接收与返还"));

        // Wait for material-receive-return subflow
        CaseSubflowInstance materialSubflow = getRunningSubflow(caseId, "material-receive-return");
        assertThat(materialSubflow).isNotNull();

        // material-receive-return chain:
        // PROJECT_CONFIRM -> MATERIAL_UPLOAD -> PROJECT_MATERIAL_CONFIRM -> ASSISTANT_REGISTER
        // -> ARCHIVIST_HANDLE -> ARCHIVE + PROJECT_DECISION -> DRAFT_OPINION_REVIEW
        Map<String, Object> materialFormData = Map.ofEntries(
                Map.entry("materialReceiveType", "委托方直接提供"),
                Map.entry("materialUploaderId", 9L),
                Map.entry("materialSource", "法院寄送"),
                Map.entry("requireSupplementaryMaterial", false),
                Map.entry("supplementaryNoticeUploaded", false),
                Map.entry("materialsUploaded", true),
                Map.entry("projectMaterialConfirmed", true),
                Map.entry("materialDetails", "E2E材料详情"),
                Map.entry("receiveDate", "2026-06-12"),
                Map.entry("materialMediaType", "纸质原件"),
                Map.entry("storageLocation", "档案室1号柜"),
                Map.entry("requireReturn", false),
                Map.entry("storageStatus", "正常"),
                Map.entry("returnRegistrationCompleted", false),
                Map.entry("nextRecommendation", "鉴定意见书征求意见稿送审稿编制")
        );
        completeTask(caseId, "PROJECT_CONFIRM", materialFormData, 9L, "管理员");
        completeTask(caseId, "MATERIAL_UPLOAD", Map.of("materialsUploaded", true));
        completeTask(caseId, "PROJECT_MATERIAL_CONFIRM", Map.of("projectMaterialConfirmed", true));
        completeTask(caseId, "ASSISTANT_REGISTER", Map.of(
                "materialMediaType", "纸质原件",
                "storageLocation", "档案室1号柜",
                "requireReturn", false
        ));
        completeTask(caseId, "ARCHIVIST_HANDLE", Map.of("storageStatus", "正常"));
        completeTask(caseId, "PROJECT_DECISION", Map.of("nextRecommendation", "鉴定意见书征求意见稿送审稿编制"));

        // Wait for draft-opinion-review subflow
        CaseSubflowInstance draftSubflow = getRunningSubflow(caseId, "draft-opinion-review");
        assertThat(draftSubflow).isNotNull();

        // draft-opinion-review chain: PROJECT_ASSIGN -> ASSISTANT_DRAFT -> PROJECT_REVIEW -> TECHNICAL_REVIEW -> DEPARTMENT_REVIEW -> PROJECT_FINAL_UPLOAD -> ISSUE_DRAFT_OPINION
        Map<String, Object> draftOpinionFormData = Map.ofEntries(
                Map.entry("draftOpinionUploaded", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("technicalReviewPassed", true),
                Map.entry("departmentReviewPassed", true),
                Map.entry("finalDraftUploaded", true),
                Map.entry("nextRecommendation", "出具征求意见稿")
        );
        completeTask(caseId, "PROJECT_ASSIGN", Map.of());
        completeTask(caseId, "ASSISTANT_DRAFT", draftOpinionFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "TECHNICAL_REVIEW", Map.of("technicalReviewPassed", true));
        completeTask(caseId, "DEPARTMENT_REVIEW", Map.of("departmentReviewPassed", true));
        completeTask(caseId, "PROJECT_FINAL_UPLOAD", Map.of("nextRecommendation", "出具征求意见稿"));

        // Wait for issue-draft-opinion subflow
        CaseSubflowInstance issueDraftSubflow = getRunningSubflow(caseId, "issue-draft-opinion");
        assertThat(issueDraftSubflow).isNotNull();

        // issue-draft-opinion chain: ASSISTANT_SUPPLEMENT -> PROJECT_REVIEW -> ARCHIVIST_CONFIRM
        // -> SEALED_UPLOAD -> ARCHIVE_SUBFLOW / DELIVERY -> WAIT_FEEDBACK -> FINAL_OPINION_REVIEW
        Map<String, Object> issueDraftFormData = Map.ofEntries(
                Map.entry("explainLetterDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", false),
                Map.entry("draftOpinionUploaded", true),
                Map.entry("sealedDraftOpinionUploaded", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true),
                Map.entry("feedbackReceived", true),
                Map.entry("feedbackHasObjection", false),
                Map.entry("feedbackDecision", "无异议或未反馈")
        );
        completeTask(caseId, "ASSISTANT_SUPPLEMENT", issueDraftFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", false, "draftOpinionUploaded", true));
        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedDraftOpinionUploaded", true, "archiveConfirmed", true));
        completeTask(caseId, "DELIVERY", Map.of("deliveryMethod", "电子送达"));
        completeTask(caseId, "WAIT_FEEDBACK", Map.of("feedbackDecision", "无异议或未反馈"));

        // Wait for final-opinion-review subflow
        CaseSubflowInstance finalSubflow = getRunningSubflow(caseId, "final-opinion-review");
        assertThat(finalSubflow).isNotNull();

        // final-opinion-review chain: PROJECT_ASSIGN -> ASSISTANT_DRAFT -> PROJECT_REVIEW -> TECHNICAL_REVIEW -> DEPARTMENT_REVIEW -> PROJECT_FINAL_UPLOAD -> ISSUE_OPINION
        Map<String, Object> finalOpinionFormData = Map.ofEntries(
                Map.entry("opinionDraftUploaded", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("versionAUploaded", true),
                Map.entry("technicalReviewPassed", true),
                Map.entry("versionABUploaded", true),
                Map.entry("departmentReviewPassed", true),
                Map.entry("versionABCUploaded", true),
                Map.entry("finalDraftUploaded", true),
                Map.entry("nextRecommendation", "出具鉴定意见书")
        );
        completeTask(caseId, "PROJECT_ASSIGN", Map.of());
        completeTask(caseId, "ASSISTANT_DRAFT", finalOpinionFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "versionAUploaded", true));
        completeTask(caseId, "TECHNICAL_REVIEW", Map.of("technicalReviewPassed", true, "versionABUploaded", true));
        completeTask(caseId, "DEPARTMENT_REVIEW", Map.of("departmentReviewPassed", true, "versionABCUploaded", true));
        completeTask(caseId, "PROJECT_FINAL_UPLOAD", Map.of("nextRecommendation", "出具鉴定意见书"));

        // Wait for issue-opinion subflow
        CaseSubflowInstance issueSubflow = getRunningSubflow(caseId, "issue-opinion");
        assertThat(issueSubflow).isNotNull();

        // issue-opinion chain: PROJECT_MODIFY -> ASSISTANT_UPLOAD -> PROJECT_REVIEW -> ARCHIVIST_CONFIRM
        // -> PARALLEL_GATEWAY_SPLIT (SEALED_UPLOAD / FINANCE_INVOICE) -> PARALLEL_GATEWAY_JOIN -> DELIVERY_ARCHIVE -> ARCHIVE_SUBFLOW
        Map<String, Object> issueOpinionFormData = Map.ofEntries(
                Map.entry("opinionModified", true),
                Map.entry("commitmentDrafted", true),
                Map.entry("reviewOpinionDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", false),
                Map.entry("systemRegistrationUploaded", true),
                Map.entry("sealedOpinionUploaded", true),
                Map.entry("invoiceRequired", true),
                Map.entry("invoiceIssued", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true)
        );
        completeTask(caseId, "PROJECT_MODIFY", issueOpinionFormData);
        completeTask(caseId, "ASSISTANT_UPLOAD", issueOpinionFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", false, "invoiceRequired", true, "systemRegistrationUploaded", true));
        
        // Parallel gateway split -> should have SEALED_UPLOAD and FINANCE_INVOICE active
        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedOpinionUploaded", true));
        completeTask(caseId, "FINANCE_INVOICE", Map.of("invoiceIssued", true));

        completeTask(caseId, "DELIVERY_ARCHIVE", Map.of("archiveConfirmed", true));

        // Wait for archive subflow
        CaseSubflowInstance archiveSubflow = getRunningSubflow(caseId, "archive");
        assertThat(archiveSubflow).isNotNull();

        // Complete both archive branches: material handling already created one archive subflow,
        // and issue-opinion creates the final archive subflow.
        completeAllDirectArchiveSubflows(caseId);

        // Now everything should be completed
        caseInfo = caseInfoService.getById(caseId);
        assertThat(caseInfo.getCaseStatus())
                .withFailMessage("Expected case completed, active tasks: %s, running subflows: %s",
                        activeTaskNodeCodes(caseId), runningSubflowCodes(caseId))
                .isEqualTo(CaseStatus.COMPLETED.name());
        
        // Assert no active tasks
        long activeTasks = caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running"));
        assertThat(activeTasks).isEqualTo(0L);

        // Assert no running subflows
        long activeSubflows = caseSubflowInstanceMapper.selectCount(new LambdaQueryWrapper<CaseSubflowInstance>()
                .eq(CaseSubflowInstance::getCaseId, caseId)
                .eq(CaseSubflowInstance::getStatus, "running"));
        assertThat(activeSubflows).isEqualTo(0L);

        // Assert main wf completed
        mainWf = caseWfInstanceMapper.selectById(mainWf.getId());
        assertThat(mainWf.getStatus()).isEqualTo("completed");

        // Assert formData is persisted properly
        assertThat(caseInfo.getFormData()).isNotNull();
        assertThat(caseInfo.getFormData().get("clientName")).isEqualTo("E2E委托人");
        assertThat(caseInfo.getFormData().get("paymentReceived")).isEqualTo(true);
        assertThat(caseInfo.getFormData().get("invoiceIssued")).isEqualTo(true);
        assertThat(caseInfo.getCurrentNodeCode()).isNull();
        assertThat(caseInfo.getCurrentNodeName()).isNull();
        assertThat(caseInfo.getCurrentHandlerId()).isNull();
        assertThat(caseInfo.getCurrentHandlerName()).isNull();
        assertThat(mainWf.getCurrentNodeCode()).isNull();
        assertThat(mainWf.getCurrentNodeName()).isNull();
    }

    private void completeAllDirectArchiveSubflows(Long caseId) {
        Map<String, Object> archiveFormData = Map.ofEntries(
                Map.entry("projectArchiveUploaded", true),
                Map.entry("paperScansUploaded", true),
                Map.entry("electronicArchiveLocation", "http://e2e/archive"),
                Map.entry("deliveryRoute", "直接中心审核"),
                Map.entry("centralArchiveApproved", true)
        );
        while (countActiveTasks(caseId, "ARCHIVIST_PREPARE") > 0) {
            completeTask(caseId, "ARCHIVIST_PREPARE", archiveFormData);
        }
        while (countActiveTasks(caseId, "CENTRAL_REVIEW") > 0) {
            completeTask(caseId, "CENTRAL_REVIEW", Map.of("centralArchiveApproved", true));
        }
    }

    @Test
    void issueOpinionShouldWaitForSealSubflowWhenInvoiceCompletesFirst() {
        Long caseId = startIssueOpinionSubflowCase("E2E出具意见书等待用章和开票");

        completeTask(caseId, "PROJECT_MODIFY", Map.ofEntries(
                Map.entry("caseNo", "JA-WAIT-1"),
                Map.entry("projectLeaderId", 3L),
                Map.entry("archivistId", 4L),
                Map.entry("financeId", 5L),
                Map.entry("opinionModified", true),
                Map.entry("commitmentDrafted", true),
                Map.entry("reviewOpinionDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", true),
                Map.entry("systemRegistrationUploaded", true),
                Map.entry("sealedOpinionUploaded", true),
                Map.entry("invoiceRequired", true),
                Map.entry("invoiceIssued", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true)
        ));
        completeTask(caseId, "ASSISTANT_UPLOAD", Map.of("commitmentDrafted", true, "reviewOpinionDrafted", true));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", true, "invoiceRequired", true, "systemRegistrationUploaded", true));

        assertThat(getRunningSubflow(caseId, "seal-application")).isNotNull();
        completeTask(caseId, "FINANCE_INVOICE", Map.of("invoiceIssued", true));
        assertThat(countActiveTasks(caseId, "DELIVERY_ARCHIVE")).isZero();

        completeSealApplicationSubflow(caseId);
        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedOpinionUploaded", true));

        assertThat(countActiveTasks(caseId, "DELIVERY_ARCHIVE")).isEqualTo(1L);
    }

    @Test
    void issueOpinionShouldWaitForSealSubflowWhenInvoiceNotRequired() {
        Long caseId = startIssueOpinionSubflowCase("E2E出具意见书等待用章免开票");

        completeTask(caseId, "PROJECT_MODIFY", Map.ofEntries(
                Map.entry("caseNo", "JA-WAIT-2"),
                Map.entry("projectLeaderId", 3L),
                Map.entry("archivistId", 4L),
                Map.entry("financeId", 5L),
                Map.entry("opinionModified", true),
                Map.entry("commitmentDrafted", true),
                Map.entry("reviewOpinionDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", true),
                Map.entry("systemRegistrationUploaded", true),
                Map.entry("sealedOpinionUploaded", true),
                Map.entry("invoiceRequired", false),
                Map.entry("invoiceIssued", false),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true)
        ));
        completeTask(caseId, "ASSISTANT_UPLOAD", Map.of("commitmentDrafted", true, "reviewOpinionDrafted", true));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", true, "invoiceRequired", false, "systemRegistrationUploaded", true));

        assertThat(getRunningSubflow(caseId, "seal-application")).isNotNull();
        assertThat(countActiveTasks(caseId, "DELIVERY_ARCHIVE")).isZero();

        completeSealApplicationSubflow(caseId);
        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedOpinionUploaded", true));

        assertThat(countActiveTasks(caseId, "DELIVERY_ARCHIVE")).isEqualTo(1L);
    }

    private CaseSubflowInstance getRunningSubflow(Long caseId, String wfCode) {
        return caseSubflowInstanceMapper.selectOne(new LambdaQueryWrapper<CaseSubflowInstance>()
                .eq(CaseSubflowInstance::getCaseId, caseId)
                .eq(CaseSubflowInstance::getWfCode, wfCode)
                .eq(CaseSubflowInstance::getStatus, "running")
                .orderByDesc(CaseSubflowInstance::getId)
                .last("LIMIT 1"));
    }

    private Long startIssueOpinionSubflowCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "出具鉴定意见书", "测试法院", 1L));
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());
        caseInfoMapper.updateById(caseInfo);

        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, "issue-opinion")
                .eq(WfDefinition::getPublishStatus, "published")
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("LIMIT 1"));
        assertThat(definition).isNotNull();

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setCaseId(caseInfo.getId());
        wfInstance.setWfId(definition.getId());
        wfInstance.setWfCode(definition.getWfCode());
        wfInstance.setWfName(definition.getWfName());
        wfInstance.setStatus("running");
        wfInstance.setCurrentNodeCode("PROJECT_MODIFY");
        wfInstance.setCurrentNodeName("项目负责人修改鉴定意见书");
        caseWfInstanceMapper.insert(wfInstance);

        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseInfo.getId());
        nodeInstance.setWfInstanceId(wfInstance.getId());
        nodeInstance.setNodeCode("PROJECT_MODIFY");
        nodeInstance.setNodeName("项目负责人修改鉴定意见书");
        nodeInstance.setStatus("running");
        caseNodeInstanceMapper.insert(nodeInstance);

        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstance.getId());
        task.setNodeInstanceId(nodeInstance.getId());
        task.setTaskType("candidate");
        task.setTaskTitle(title + " - 项目负责人修改鉴定意见书");
        task.setNodeCode("PROJECT_MODIFY");
        task.setNodeName("项目负责人修改鉴定意见书");
        task.setStatus("pending");
        task.setAssigneeId(9L);
        task.setAssigneeName("管理员");
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        return caseInfo.getId();
    }

    private void completeSealApplicationSubflow(Long caseId) {
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "caseNo", "JA-SEAL",
                "applicantId", 3L,
                "archivistId", 4L,
                "sealOperatorId", 6L,
                "applicationReason", "鉴定意见书用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true,
                "archivistReviewed", true,
                "sealCompleted", true,
                "sealedScanUploaded", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
    }

    private long countActiveTasks(Long caseId, String nodeCode) {
        return caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running"));
    }

    private List<String> activeTaskNodeCodes(Long caseId) {
        return caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                        .eq(CaseTask::getCaseId, caseId)
                        .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running")
                        .orderByAsc(CaseTask::getId))
                .stream()
                .map(CaseTask::getNodeCode)
                .toList();
    }

    private List<String> runningSubflowCodes(Long caseId) {
        return caseSubflowInstanceMapper.selectList(new LambdaQueryWrapper<CaseSubflowInstance>()
                        .eq(CaseSubflowInstance::getCaseId, caseId)
                        .eq(CaseSubflowInstance::getStatus, "running")
                        .orderByAsc(CaseSubflowInstance::getId))
                .stream()
                .map(CaseSubflowInstance::getWfCode)
                .toList();
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        completeTask(caseId, nodeCode, formData, null, null);
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData, Long nextAssigneeId, String nextAssigneeName) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), ActionCode.APPROVE, "自动E2E执行", null, null, null,
                nextAssigneeId, nextAssigneeName, formData, null);
        workflowRuntimeService.completeTask(caseId, request, 9L, "管理员");
    }
}
