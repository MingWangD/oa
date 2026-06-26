package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.caseinfo.service.CaseInfoService;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUser;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.platform.service.PlatformCatalogService;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class TerminateAppraisalBranchVerificationTest {

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
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void terminateAppraisal_fullFlow_fromWithdrawCase() {
        // A. Upstream trigger: Withdraw Case (No Refund)
        Long caseId = triggerTerminateFromWithdrawCase("9.17-收到撤案函触发终止");

        // B. Terminate Appraisal Main Path
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal");
        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL", "ASSISTANT_DRAFT");

        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "测试撤案终止",
                "draftCompleted", true
        ));
        assertFormDataContains(caseId, Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "测试撤案终止",
                "draftCompleted", true
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL", "PROJECT_REVIEW");

        completeTask(caseId, "PROJECT_REVIEW", Map.of(
                "projectReviewPassed", true,
                "sealRequired", true
        ));
        assertFormDataContains(caseId, Map.of(
                "projectReviewPassed", true,
                "sealRequired", true
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL", "SEAL_APPLICATION", "APPLICANT_SUBMIT");
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal", "seal-application");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("SEALED_UPLOAD", "ARCHIVE_SUBFLOW");

        // D. Subflow Waiting Verification
        assertThat(getTask(caseId, "SEAL_APPLICATION").getStatus()).isEqualTo("subflow_running");

        completeSealApplicationSubflow(caseId);

        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL", "SEALED_UPLOAD");

        completeTask(caseId, "SEALED_UPLOAD", Map.of(
                "sealedTerminationUploaded", true,
                "archiveConfirmed", true
        ));
        assertFormDataContains(caseId, Map.of(
                "sealedTerminationUploaded", true,
                "archiveConfirmed", true
        ));

        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL", "ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal", "archive");
    }

    @Test
    void terminateAppraisal_returnPaths() {
        Long caseId = triggerTerminateFromWithdrawCase("9.17-终止鉴定退回路径");

        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "错误原因",
                "draftCompleted", true
        ));

        // C. Return Path 1: PROJECT_REVIEW -> ASSISTANT_DRAFT
        completeTask(caseId, "PROJECT_REVIEW", "RETURN", Map.of(
                "projectReviewPassed", false,
                "handlerOpinion", "原因写错了"
        ));
        assertFormDataContains(caseId, Map.of("projectReviewPassed", false));
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");

        completeTask(caseId, "ASSISTANT_DRAFT", Map.of("terminationReason", "修正原因"));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "sealRequired", true));

        completeSealApplicationSubflow(caseId);

        // C. Return Path 2: SEALED_UPLOAD -> PROJECT_REVIEW
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_UPLOAD");
        completeTask(caseId, "SEALED_UPLOAD", "RETURN", Map.of(
                "archiveConfirmed", false,
                "handlerOpinion", "盖章件模糊"
        ));
        assertFormDataContains(caseId, Map.of("archiveConfirmed", false));
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("archive");
    }

    @Test
    void terminateAppraisal_otherUpstreamTriggers() {
        // Upstream: Preliminary Survey (Not Feasible)
        Long caseId1 = createCaseRecord("9.17-初勘不具备条件触发", "收到委托书");
        startWorkflowRecord(caseId1, "received-entrust");
        completeTask(caseId1, "START", baseReceivedEntrustData(true, true, false));
        completeTask(caseId1, "INIT_FILL", baseReceivedEntrustData(true, true, false));
        completeTask(caseId1, "CLERK_REGISTER", Map.of());
        completeTask(caseId1, "DEPT_REVIEW", Map.of("entrustAccepted", true));
        completeTask(caseId1, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", true));
        assertThat(runningSubflowCodes(caseId1)).contains("preliminary-survey");
        assertThat(runningSubflowCodes(caseId1)).doesNotContain("terminate-appraisal");
        completeTask(caseId1, "ASSISTANT_PREPARE", preliminarySurveyFormData(false));
        completeTask(caseId1, "PROJECT_REVIEW", Map.of(
                "appraisalConditionMet", false,
                "nextRecommendation", "终止鉴定"
        ));
        assertFormDataContains(caseId1, Map.of(
                "appraisalConditionMet", false,
                "nextRecommendation", "终止鉴定"
        ));
        assertThat(activeTaskNodeCodes(caseId1)).contains("TERMINATE_APPRAISAL", "ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId1)).contains("preliminary-survey", "terminate-appraisal");
        assertThat(runningSubflowCodes(caseId1)).doesNotContain("payment-notice");

        // Upstream: Payment Notice (Not Paid)
        Long caseId2 = createCaseRecord("9.17-未缴费触发终止", "收到委托书");
        startWorkflowRecord(caseId2, "received-entrust");
        completeTask(caseId2, "START", baseReceivedEntrustData(true, false, false));
        completeTask(caseId2, "INIT_FILL", baseReceivedEntrustData(true, false, false));
        completeTask(caseId2, "CLERK_REGISTER", Map.of());
        completeTask(caseId2, "DEPT_REVIEW", Map.of("entrustAccepted", true));
        completeTask(caseId2, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", false));
        assertThat(runningSubflowCodes(caseId2)).contains("payment-notice");
        assertThat(runningSubflowCodes(caseId2)).doesNotContain("terminate-appraisal");
        completeTask(caseId2, "ASSISTANT_DRAFT", paymentNoticeFormData(false));
        completeTask(caseId2, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(caseId2, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", false));
        completeTask(caseId2, "PAYMENT_CONFIRM", Map.of(
                "paymentReceived", false,
                "nextRecommendation", "终止鉴定"
        ));
        assertFormDataContains(caseId2, Map.of(
                "paymentReceived", false,
                "nextRecommendation", "终止鉴定"
        ));
        assertThat(activeTaskNodeCodes(caseId2)).contains("TERMINATE_APPRAISAL", "ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId2)).contains("payment-notice", "terminate-appraisal");
        assertThat(runningSubflowCodes(caseId2)).doesNotContain("quality-control");
    }

    private Long triggerTerminateFromWithdrawCase(String taskName) {
        Long caseId = createCaseRecord(taskName, "收到撤案函");
        startWorkflowRecord(caseId, "withdraw-case-letter");
        completeTask(caseId, "START", Map.of("projectLeaderId", OPERATOR_ID));
        completeTask(caseId, "LETTER_REGISTER", Map.of("withdrawLetterReceivedDate", "2026-06-13", "withdrawReason", "撤案测试"));
        completeTask(caseId, "PROJECT_DECISION", Map.of("refundRequired", false));
        return caseId;
    }

    private Long createCaseRecord(String title, String type) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, type, "测试法院", 1L));
        caseInfo.setCaseNo("JA-" + System.currentTimeMillis());
        caseInfoMapper.updateById(caseInfo);
        return caseInfo.getId();
    }

    private void startWorkflowRecord(Long caseId, String wfCode) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        com.example.judicialappraisal.workflow.entity.WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<com.example.judicialappraisal.workflow.entity.WfDefinition>()
                .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getWfCode, wfCode)
                .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getPublishStatus, "published")
                .orderByDesc(com.example.judicialappraisal.workflow.entity.WfDefinition::getVersionNo)
                .last("LIMIT 1"));
                
        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setCaseId(caseInfo.getId());
        wfInstance.setWfId(definition.getId());
        wfInstance.setWfCode(definition.getWfCode());
        wfInstance.setWfName(definition.getWfName());
        wfInstance.setStatus("running");
        wfInstance.setCurrentNodeCode("START");
        wfInstance.setStartedBy(OPERATOR_ID);
        wfInstance.setStartedTime(java.time.LocalDateTime.now());
        caseWfInstanceMapper.insert(wfInstance);
        
        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
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
        
        caseInfo.setCaseStatus("PROCESSING");
        caseInfoMapper.updateById(caseInfo);
    }

    private Map<String, Object> baseReceivedEntrustData(boolean accepted, boolean prelim, boolean material) {
        return Map.ofEntries(
                Map.entry("clientName", "测试委托人"),
                Map.entry("serialNo", "TEST-NO"),
                Map.entry("initiatorName", "管理员"),
                Map.entry("initiatedDate", "2026-06-13"),
                Map.entry("receivedDate", "2026-06-13"),
                Map.entry("caseNo", "JA-BRANCH-" + System.currentTimeMillis()),
                Map.entry("entrustUnit", "测试法院"),
                Map.entry("entrustDate", "2026-06-13"),
                Map.entry("appraisalCategory", "法医临床"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "终止测试"),
                Map.entry("entrustAccepted", accepted),
                Map.entry("preliminarySurveyRequired", prelim),
                Map.entry("materialReceiveRequired", material),
                Map.entry("departmentHeadId", OPERATOR_ID),
                Map.entry("projectLeaderId", OPERATOR_ID),
                Map.entry("projectAssistantId", OPERATOR_ID),
                Map.entry("filingDate", "2026-06-13"),
                Map.entry("undertakingLegalPerson", "测试承办人"),
                Map.entry("institutionSelectionMethod", "随机抽取"),
                Map.entry("institutionSelectionTime", "2026-06-13"),
                Map.entry("applicantName", "测试原告"),
                Map.entry("respondentName", "测试被告")
        );
    }

    private Map<String, Object> preliminarySurveyFormData(boolean appraisalConditionMet) {
        return Map.ofEntries(
                Map.entry("surveyDate", "2026-06-13"),
                Map.entry("surveyLocation", "测试项目现场"),
                Map.entry("surveyPlanUploaded", true),
                Map.entry("equipmentOutboundRecorded", true),
                Map.entry("equipmentUsageRecorded", true),
                Map.entry("surveySummary", "终止鉴定上游触发验证"),
                Map.entry("appraisalConditionMet", appraisalConditionMet),
                Map.entry("nextRecommendation", appraisalConditionMet ? "发交费通知书及相关函件" : "终止鉴定")
        );
    }

    private Map<String, Object> paymentNoticeFormData(boolean paymentReceived) {
        return Map.ofEntries(
                Map.entry("letterDraftCompleted", true),
                Map.entry("letterType", "交费通知书"),
                Map.entry("letterSummary", "未缴费终止鉴定上游触发验证"),
                Map.entry("projectAmount", 120000),
                Map.entry("sealRequired", false),
                Map.entry("sealedDocumentUploaded", false),
                Map.entry("sendDate", "2026-06-13"),
                Map.entry("paymentReceived", paymentReceived),
                Map.entry("paymentConfirmedDate", "2026-06-13"),
                Map.entry("nextRecommendation", paymentReceived ? "编制内部质量控制文件" : "终止鉴定")
        );
    }

    private void completeSealApplicationSubflow(Long caseId) {
        assertThat(runningSubflowCodes(caseId)).contains("seal-application");
        assertThat(activeTaskNodeCodes(caseId)).contains("APPLICANT_SUBMIT");
        
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "终止文书盖章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        completeTask(caseId, nodeCode, "APPROVE", formData);
    }

    private void completeTask(Long caseId, String nodeCode, String actionCodeStr, Map<String, Object> formData) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode).isNotNull();

        ActionCode actionCode = ActionCode.valueOf(actionCodeStr);
        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "测试操作", null, null, null, formData, null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
    }

    private CaseTask getTask(Long caseId, String nodeCode) {
        return caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
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

    private void assertFormDataContains(Long caseId, Map<String, Object> expectedEntries) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        assertThat(caseInfo).isNotNull();
        Map<String, Object> actualFormData = caseInfo.getFormData();
        assertThat(actualFormData).isNotNull();
        for (Map.Entry<String, Object> entry : expectedEntries.entrySet()) {
            Object actualValue = actualFormData.get(entry.getKey());
            Object expectedValue = entry.getValue();
            if (actualValue instanceof Number actualNumber && expectedValue instanceof Number expectedNumber) {
                assertThat(actualNumber.longValue()).as(entry.getKey()).isEqualTo(expectedNumber.longValue());
            } else {
                assertThat(actualValue).as(entry.getKey()).isEqualTo(expectedValue);
            }
        }
    }

    private void ensureOperatorHasAllJudicialRoles() {
        // Ensure user 9 exists
        SysUser user = sysUserMapper.selectById(OPERATOR_ID);
        if (user == null) {
            user = new SysUser();
            user.setId(OPERATOR_ID);
            user.setUsername("admin_test_" + System.currentTimeMillis());
            user.setRealName(OPERATOR_NAME);
            user.setStatus("enabled");
            sysUserMapper.insert(user);
        }

        List<String> roleNames = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roleNames.addAll(List.of("发起者", "申请人", "收件人", "项目负责人", "档案管理员", "财务", "项目辅助人", "部门负责人", "盖章经办人", "技术负责人", "质量控制人", "邮寄人员", "中心档案管理员"));
        
        for (String name : roleNames) {
            // Find all roles that match this name or common codes
            List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                    .and(w -> w.eq(SysRole::getRoleName, name).or().eq(SysRole::getRoleCode, name)));
            
            if (roles.isEmpty()) {
                SysRole role = new SysRole();
                role.setRoleName(name);
                role.setRoleCode(name);
                role.setStatus("enabled");
                role.setDataScope("all");
                role.setDeleted(0);
                sysRoleMapper.insert(role);
                roles = List.of(role);
            }
            
            for (SysRole role : roles) {
                SysUserRole existing = sysUserRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, OPERATOR_ID)
                        .eq(SysUserRole::getRoleId, role.getId()));
                if (existing == null) {
                    SysUserRole ur = new SysUserRole();
                    ur.setUserId(OPERATOR_ID);
                    ur.setRoleId(role.getId());
                    sysUserRoleMapper.insert(ur);
                }
            }
        }
    }
}
