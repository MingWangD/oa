package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.service.CaseInfoService;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.platform.service.PlatformCatalogService;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PaymentNoticeBranchVerificationTest {

    private static final Long OPERATOR_ID = 9L;
    private static final String OPERATOR_NAME = "管理员";

    @Autowired
    private JudicialConfigImportService judicialConfigImportService;

    @Autowired
    private PlatformCatalogService platformCatalogService;

    @Autowired
    private CaseInfoService caseInfoService;

    @Autowired
    private WorkflowRuntimeService workflowRuntimeService;

    @Autowired
    private CaseTaskMapper caseTaskMapper;

    @Autowired
    private CaseSubflowInstanceMapper caseSubflowInstanceMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void sealRequired_shouldLaunchSealApplicationSubflow() {
        Long caseId = createCaseAtPaymentProjectReview("9.3-需要用章分支", true, true);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("sealRequired", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION");
        assertThat(runningSubflowCodes(caseId)).contains("seal-application");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ARCHIVE_UPLOAD", "PAYMENT_CONFIRM");
    }

    @Test
    void sealNotRequired_shouldMoveToArchiveUploadWithoutSealSubflow() {
        Long caseId = createCaseAtPaymentProjectReview("9.3-无需用章分支", false, true);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("sealRequired", false), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_UPLOAD");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("seal-application");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("SEAL_APPLICATION");
    }

    @Test
    void paymentReceived_shouldLaunchQualityControlSubflow() {
        Long caseId = createCaseAtPaymentConfirm("9.3-已缴费分支", true);

        completeTask(caseId, "PAYMENT_CONFIRM", ActionCode.APPROVE, Map.of(
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("QUALITY_CONTROL");
        assertThat(runningSubflowCodes(caseId)).contains("quality-control");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("TERMINATE_APPRAISAL");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("terminate-appraisal");
    }

    @Test
    void paymentNotReceived_shouldLaunchTerminateAppraisalSubflow() {
        Long caseId = createCaseAtPaymentConfirm("9.3-未缴费分支", false);

        completeTask(caseId, "PAYMENT_CONFIRM", ActionCode.APPROVE, Map.of(
                "paymentReceived", false,
                "nextRecommendation", "终止鉴定"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL");
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("QUALITY_CONTROL");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("quality-control");
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantDraft() {
        Long caseId = createCaseAtPaymentProjectReview("9.3-审核退回辅助人分支", false, true);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN, Map.of(), "缴费函件内容需修改");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("SEAL_APPLICATION", "ARCHIVE_UPLOAD", "PAYMENT_CONFIRM");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("seal-application");
    }

    @Test
    void paymentConfirmReturn_shouldReturnToArchiveUpload() {
        Long caseId = createCaseAtPaymentConfirm("9.3-缴费确认退回档案管理员分支", true);

        completeTask(caseId, "PAYMENT_CONFIRM", ActionCode.RETURN, Map.of(), "盖章件回传信息需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_UPLOAD");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("QUALITY_CONTROL", "TERMINATE_APPRAISAL");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("quality-control", "terminate-appraisal");
    }

    private Long createCaseAtPaymentConfirm(String title, boolean paymentReceived) {
        Long caseId = createCaseAtPaymentProjectReview(title, false, paymentReceived);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("sealRequired", false), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_UPLOAD");
        completeTask(caseId, "ARCHIVE_UPLOAD", ActionCode.APPROVE, Map.of("sealedDocumentUploaded", false), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PAYMENT_CONFIRM");
        return caseId;
    }

    private Long createCaseAtPaymentProjectReview(String title, boolean sealRequired, boolean paymentReceived) {
        Long caseId = createSubmittedReceivedEntrustCase(title);
        completeTask(caseId, "INIT_FILL", ActionCode.APPROVE, receivedEntrustFormData(), null);
        completeTask(caseId, "CLERK_REGISTER", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "DEPT_REVIEW", ActionCode.APPROVE, Map.of("entrustAccepted", true), null);
        completeTask(caseId, "PROJECT_DECISION", ActionCode.APPROVE, Map.of(
                "preliminarySurveyRequired", false,
                "materialReceiveRequired", false
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("payment-notice");

        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE, paymentNoticeFormData(sealRequired, paymentReceived), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createSubmittedReceivedEntrustCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 9.3 分支验证"), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private Map<String, Object> receivedEntrustFormData() {
        return Map.ofEntries(
                
                Map.entry("filingDate", "2026-06-13"),
                Map.entry("undertakingLegalPerson", "测试承办人"),
                Map.entry("institutionSelectionMethod", "随机抽取"),
                Map.entry("institutionSelectionTime", "2026-06-13"),
                Map.entry("applicantName", "测试原告"),
                Map.entry("respondentName", "测试被告"),
Map.entry("clientName", "9.3 分支验证委托人"),
                Map.entry("serialNo", "BRANCH-9-3"),
                Map.entry("initiatorName", "测试发起人"),
                Map.entry("initiatedDate", "2026-06-12"),
                Map.entry("receivedDate", "2026-06-12"),
                Map.entry("caseNo", "JA-BRANCH-9-3"),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "9.3 发交费通知书分支验证"),
                Map.entry("entrustAccepted", true),
                Map.entry("preliminarySurveyRequired", false),
                Map.entry("materialReceiveRequired", false),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L)
        );
    }

    private Map<String, Object> paymentNoticeFormData(boolean sealRequired, boolean paymentReceived) {
        return Map.ofEntries(
                Map.entry("letterDraftCompleted", true),
                Map.entry("letterType", "交费通知书"),
                Map.entry("letterSummary", "已编制交费通知书及相关函件。"),
                Map.entry("sealRequired", sealRequired),
                Map.entry("sealedDocumentUploaded", sealRequired),
                Map.entry("sendDate", "2026-06-12"),
                Map.entry("paymentReceived", paymentReceived),
                Map.entry("paymentConfirmedDate", "2026-06-12"),
                Map.entry("nextRecommendation", paymentReceived ? "编制内部质量控制文件" : "终止鉴定")
        );
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "9.3 分支验证", reason, null, null, formData, null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
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

    private void ensureOperatorHasAllJudicialRoles() {
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员"));
        for (String roleName : roles) {
            SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, roleName));
            if (role == null) {
                role = new SysRole();
                role.setRoleName(roleName);
                role.setRoleCode(roleName);
                role.setStatus("enabled");
                role.setDataScope("all");
                role.setDeleted(0);
                sysRoleMapper.insert(role);
            }
            Long count = sysUserRoleMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, OPERATOR_ID)
                    .eq(SysUserRole::getRoleId, role.getId()));
            if (count == null || count == 0L) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(OPERATOR_ID);
                userRole.setRoleId(role.getId());
                sysUserRoleMapper.insert(userRole);
            }
        }
    }
}
