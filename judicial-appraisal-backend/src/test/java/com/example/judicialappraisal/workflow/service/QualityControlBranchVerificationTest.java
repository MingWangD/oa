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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class QualityControlBranchVerificationTest {

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
    void nonFClass_shouldGoToSealAfterProjectReview() {
        Long caseId = createCaseAtQualityProjectReview("9.4-非F类分支", "中心格式", 400000);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "fClassProject", false,
                "projectReviewPassed", true,
                "projectReviewRoute", "进入用章"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("APPLICANT_SUBMIT");
        assertThat(runningSubflowCodes(caseId)).contains("seal-application");
    }

    @Test
    void fClass_shouldGoToDeptReviewAfterProjectReview() {
        Long caseId = createCaseAtQualityProjectReview("9.4-F类分支", "中心格式", 600000);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "fClassProject", true,
                "projectReviewPassed", true,
                "projectReviewRoute", "部门负责人审核"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");
    }

    @Test
    void deptReviewApprove_shouldGoToSeal() {
        Long caseId = createCaseAtQualityDeptReview("9.4-部门审核通过分支");

        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.APPROVE, Map.of("departmentReviewPassed", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("APPLICANT_SUBMIT");
        assertThat(runningSubflowCodes(caseId)).contains("seal-application");
    }

    @Test
    void deptReviewReturn_shouldReturnToProjectReview() {
        Long caseId = createCaseAtQualityDeptReview("9.4-部门审核退回分支");

        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.RETURN, Map.of("departmentReviewPassed", false), "F类判断需核实");

        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantDraft() {
        Long caseId = createCaseAtQualityProjectReview("9.4-项目负责人退回分支", "中心格式", 100000);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN, Map.of(
                "projectReviewPassed", false,
                "projectReviewRoute", "退回修改"
        ), "质控文件需完善");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");
    }

    @Test
    void sealedFileUploadReturn_shouldReturnToProjectReview() {
        Long caseId = createCaseAtQualitySealedFileUpload("9.4-回传退回分支");

        completeTask(caseId, "SEALED_FILE_UPLOAD", ActionCode.RETURN, Map.of(), "盖章件不清晰");

        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
    }

    @ParameterizedTest
    @CsvSource({
            "现场勘验, FIELD_SURVEY, field-survey",
            "材料接收与返还, MATERIAL_RECEIVE_RETURN, material-receive-return",
            "鉴定意见书征求意见稿送审稿编制, DRAFT_OPINION_REVIEW, draft-opinion-review",
            "鉴定意见书送审稿编制, FINAL_OPINION_REVIEW, final-opinion-review",
            "退费, REFUND, refund",
            "终止鉴定, TERMINATE_APPRAISAL, terminate-appraisal"
    })
    void nextRecommendation_shouldTriggerCorrectSubflow(String recommendation, String expectedNode, String expectedSubflow) {
        Long caseId = createCaseAtNextFlowDecision("9.4-后续流向验证-" + recommendation);

        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", recommendation), null);

        assertThat(activeTaskNodeCodes(caseId)).contains(expectedNode);
        assertThat(runningSubflowCodes(caseId)).contains(expectedSubflow);
    }

    private Long createCaseAtNextFlowDecision(String title) {
        Long caseId = createCaseAtQualitySealedFileUpload(title);
        completeTask(caseId, "SEALED_FILE_UPLOAD", ActionCode.APPROVE, Map.of("sealedQualityFileUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");
        return caseId;
    }

    private Long createCaseAtQualitySealedFileUpload(String title) {
        Long caseId = createCaseAtQualityProjectReview(title, "中心格式", 100000);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "fClassProject", false,
                "projectReviewPassed", true,
                "projectReviewRoute", "进入用章"
        ), null);
        
        // Complete seal-application subflow
        Map<String, Object> sealData = new java.util.HashMap<>();
        sealData.put("caseNo", "JA-BRANCH-9-4");
        sealData.put("applicantId", OPERATOR_ID);
        sealData.put("archivistId", OPERATOR_ID);
        sealData.put("sealOperatorId", OPERATOR_ID);
        sealData.put("applicationReason", "质量控制文件用章");
        sealData.put("sealMode", "线下盖章");
        sealData.put("applicationFilesPrepared", true);
        sealData.put("archivistReviewed", false);
        sealData.put("sealCompleted", false);
        sealData.put("sealedScanUploaded", false);
        
        completeTask(caseId, "APPLICANT_SUBMIT", sealData);
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true, "sealedScanUploaded", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));

        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_FILE_UPLOAD");
        return caseId;
    }

    private Long createCaseAtQualityDeptReview(String title) {
        Long caseId = createCaseAtQualityProjectReview(title, "中心格式", 600000);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "fClassProject", true,
                "projectReviewPassed", true,
                "projectReviewRoute", "部门负责人审核"
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");
        return caseId;
    }

    private Long createCaseAtQualityProjectReview(String title, String formatType, int amount) {
        Long caseId = createSubmittedReceivedEntrustCase(title);
        completeTask(caseId, "INIT_FILL", ActionCode.APPROVE, receivedEntrustFormData(), null);
        completeTask(caseId, "CLERK_REGISTER", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "DEPT_REVIEW", ActionCode.APPROVE, Map.of("entrustAccepted", true), null);
        completeTask(caseId, "PROJECT_DECISION", ActionCode.APPROVE, Map.of(
                "preliminarySurveyRequired", false,
                "materialReceiveRequired", false
        ), null);

        // Transition to quality-control subflow via payment-notice
        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE, paymentNoticeFormData(true), null);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("sealRequired", false), null);
        completeTask(caseId, "ARCHIVE_UPLOAD", ActionCode.APPROVE, Map.of("sealedDocumentUploaded", true), null);
        completeTask(caseId, "PAYMENT_CONFIRM", ActionCode.APPROVE, Map.of(
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("quality-control");

        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE, qualityControlFormData(formatType, amount), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createSubmittedReceivedEntrustCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 9.4 分支验证"), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private Map<String, Object> receivedEntrustFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("clientName", "9.4 分支验证委托人");
        data.put("serialNo", "BRANCH-9-4");
        data.put("initiatorName", "测试发起人");
        data.put("initiatedDate", "2026-06-12");
        data.put("receivedDate", "2026-06-12");
        data.put("caseNo", "JA-BRANCH-9-4");
        data.put("appraisalCategory", "工程造价");
        data.put("urgencyLevel", "普通");
        data.put("caseChannel", "线下");
        data.put("appraisalMatter", "9.4 编制内部质量控制文件分支验证");
        data.put("entrustAccepted", true);
        data.put("preliminarySurveyRequired", false);
        data.put("materialReceiveRequired", false);
        data.put("departmentHeadId", 2L);
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        return data;
    }

    private Map<String, Object> paymentNoticeFormData(boolean paymentReceived) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-4");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("letterDraftCompleted", true);
        data.put("letterType", "交费通知书");
        data.put("letterSummary", "已编制交费通知书。");
        data.put("sealRequired", false);
        data.put("sealedDocumentUploaded", true);
        data.put("paymentReceived", paymentReceived);
        data.put("nextRecommendation", paymentReceived ? "编制内部质量控制文件" : "终止鉴定");
        return data;
    }

    private Map<String, Object> qualityControlFormData(String formatType, int amount) {
        boolean fClass = ("中心格式".equals(formatType) && amount > 500000) || ("非中心格式".equals(formatType) && amount > 250000);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-4");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("qualityFileDraftCompleted", true);
        data.put("qualityFileSummary", "质控文件摘要内容。");
        data.put("formatType", formatType);
        data.put("contractAmount", amount);
        data.put("fClassProject", fClass);
        data.put("projectReviewPassed", true);
        data.put("projectReviewRoute", fClass ? "部门负责人审核" : "进入用章");
        data.put("sealRequired", true);
        data.put("sealedQualityFileUploaded", true);
        data.put("nextRecommendation", "现场勘验");
        return data;
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
                task.getId(), actionCode, "9.4 分支验证", reason, null, null, new java.util.HashMap<>(formData), null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        completeTask(caseId, nodeCode, ActionCode.APPROVE, formData, null);
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

    private synchronized void ensureOperatorHasAllJudicialRoles() {
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员"));
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
