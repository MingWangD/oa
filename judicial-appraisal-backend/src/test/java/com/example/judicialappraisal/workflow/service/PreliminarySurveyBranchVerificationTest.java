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
class PreliminarySurveyBranchVerificationTest {

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
    void appraisalConditionMet_shouldLaunchPaymentNoticeSubflow() {
        Long caseId = createCaseAtPreliminarySurveyReview("9.2-具备鉴定条件分支");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "appraisalConditionMet", true,
                "nextRecommendation", "发交费通知书及相关函件"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("PAYMENT_NOTICE");
        assertThat(runningSubflowCodes(caseId)).contains("payment-notice");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("TERMINATE_APPRAISAL", "ASSISTANT_PREPARE");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("terminate-appraisal");
    }

    @Test
    void appraisalConditionNotMet_shouldLaunchTerminateAppraisalSubflow() {
        Long caseId = createCaseAtPreliminarySurveyReview("9.2-不具备鉴定条件分支");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "appraisalConditionMet", false,
                "nextRecommendation", "终止鉴定"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL");
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PAYMENT_NOTICE", "ASSISTANT_PREPARE");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("payment-notice");
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantPrepare() {
        Long caseId = createCaseAtPreliminarySurveyReview("9.2-退回辅助人补充分支");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN, Map.of(), "现场工作方案和设备记录需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_PREPARE");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PAYMENT_NOTICE", "TERMINATE_APPRAISAL");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("payment-notice", "terminate-appraisal");
    }

    private Long createCaseAtPreliminarySurveyReview(String title) {
        Long caseId = createSubmittedReceivedEntrustCase(title);
        completeTask(caseId, "INIT_FILL", ActionCode.APPROVE, receivedEntrustFormData(), null);
        completeTask(caseId, "CLERK_REGISTER", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "DEPT_REVIEW", ActionCode.APPROVE, Map.of("entrustAccepted", true), null);
        completeTask(caseId, "PROJECT_DECISION", ActionCode.APPROVE, Map.of(
                "preliminarySurveyRequired", true,
                "materialReceiveRequired", false
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_PREPARE");
        assertThat(runningSubflowCodes(caseId)).contains("preliminary-survey");

        completeTask(caseId, "ASSISTANT_PREPARE", ActionCode.APPROVE, preliminarySurveyFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createSubmittedReceivedEntrustCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 9.2 分支验证"), OPERATOR_ID, OPERATOR_NAME);
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
Map.entry("clientName", "9.2 分支验证委托人"),
                Map.entry("serialNo", "BRANCH-9-2"),
                Map.entry("initiatorName", "测试发起人"),
                Map.entry("initiatedDate", "2026-06-12"),
                Map.entry("receivedDate", "2026-06-12"),
                Map.entry("caseNo", "JA-BRANCH-9-2"),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "9.2 初步勘验分支验证"),
                Map.entry("entrustAccepted", true),
                Map.entry("preliminarySurveyRequired", true),
                Map.entry("materialReceiveRequired", false),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L)
        );
    }

    private Map<String, Object> preliminarySurveyFormData() {
        return Map.ofEntries(
                Map.entry("surveyDate", "2026-06-12"),
                Map.entry("surveyLocation", "测试项目现场"),
                Map.entry("surveyPlanUploaded", true),
                Map.entry("equipmentOutboundRecorded", true),
                Map.entry("equipmentUsageRecorded", true),
                Map.entry("surveySummary", "已完成初步勘验，现场方案、设备出入库和设备使用记录齐全。"),
                Map.entry("appraisalConditionMet", true),
                Map.entry("nextRecommendation", "发交费通知书及相关函件")
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
                task.getId(), actionCode, "9.2 分支验证", reason, null, null, formData, null);
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
