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
class RejectAcceptanceBranchVerificationTest {

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
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void rejectAcceptanceFlow_shouldCompleteAllNodes() {
        // 1. Create case from Received Entrust branch
        Long caseId = createSubmittedReceivedEntrustCase("6.13-不予受理全流程");

        // Reach DEPT_REVIEW and select not accepted
        completeTask(caseId, "INIT_FILL", baseFormData(false));
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", false));

        // 2. Assert we entered REJECT_ACCEPTANCE and launched the subflow
        assertThat(activeTaskNodeCodes(caseId)).contains("REJECT_ACCEPTANCE", "ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("reject-acceptance");

        // 3. ASSISTANT_DRAFT -> PROJECT_REVIEW
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "rejectionReason", "不符合鉴定条件",
                "noticeDraftCompleted", true,
                "noticeSummary", "已通知委托方不予受理"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");

        // 4. PROJECT_REVIEW -> Return to ASSISTANT_DRAFT (Test return path)
        completeTask(caseId, "PROJECT_REVIEW", "RETURN", Map.of(
                "projectReviewPassed", false,
                "reviewOpinion", "原因写得不够详细"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");

        // 5. ASSISTANT_DRAFT -> PROJECT_REVIEW
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "rejectionReason", "超出业务范围",
                "noticeSummary", "更新通知内容"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");

        // 6. PROJECT_REVIEW -> ARCHIVIST_CONFIRM (Approve)
        completeTask(caseId, "PROJECT_REVIEW", "APPROVE", Map.of(
                "projectReviewPassed", true,
                "reviewOpinion", "同意"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_CONFIRM");

        // 7. ARCHIVIST_CONFIRM -> SEAL_APPLICATION
        completeTask(caseId, "ARCHIVIST_CONFIRM", "APPROVE", Map.of(
                "sealRequired", true
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION");
        
        // Complete the seal application subflow
        completeSealApplicationSubflow(caseId);

        // 8. SEAL_APPLICATION -> SEALED_NOTICE_UPLOAD
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_NOTICE_UPLOAD");

        // 9. SEALED_NOTICE_UPLOAD -> ARCHIVE_SUBFLOW
        completeTask(caseId, "SEALED_NOTICE_UPLOAD", "APPROVE", Map.of(
                "sealedNoticeUploaded", true
        ));
        
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
    }
    
    @Test
    void rejectAcceptanceFlow_withoutSeal() {
        Long caseId = createSubmittedReceivedEntrustCase("6.13-不予受理无需用章");

        // Reach DEPT_REVIEW and select not accepted
        completeTask(caseId, "INIT_FILL", baseFormData(false));
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", false));

        // Skip to ARCHIVIST_CONFIRM
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "rejectionReason", "不符合鉴定条件",
                "noticeDraftCompleted", true
        ));
        completeTask(caseId, "PROJECT_REVIEW", "APPROVE", Map.of(
                "projectReviewPassed", true
        ));
        
        // ARCHIVIST_CONFIRM -> SEALED_NOTICE_UPLOAD
        completeTask(caseId, "ARCHIVIST_CONFIRM", "APPROVE", Map.of(
                "sealRequired", false
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_NOTICE_UPLOAD");
        
        completeTask(caseId, "SEALED_NOTICE_UPLOAD", "APPROVE", Map.of(
                "sealedNoticeUploaded", true
        ));
        
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");
    }

    private void completeSealApplicationSubflow(Long caseId) {
        assertThat(runningSubflowCodes(caseId)).contains("seal-application");
        assertThat(activeTaskNodeCodes(caseId)).contains("APPLICANT_SUBMIT");
        
        completeTask(caseId, "APPLICANT_SUBMIT", "APPROVE", Map.of(
                "applicationReason", "不予受理盖章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", "APPROVE", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", "APPROVE", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", "APPROVE", Map.of("sealedScanUploaded", true));
    }

    // --- Helpers ---
    
    private Long createSubmittedReceivedEntrustCase(String taskName) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(taskName, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 " + taskName), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private Map<String, Object> baseFormData(boolean accepted) {
        return Map.ofEntries(
                Map.entry("clientName", "测试委托人"),
                Map.entry("serialNo", "TEST-NO"),
                Map.entry("initiatorName", "测试发起人"),
                Map.entry("initiatedDate", "2026-06-13"),
                Map.entry("receivedDate", "2026-06-13"),
                Map.entry("caseNo", "JA-" + System.currentTimeMillis()),
                Map.entry("entrustUnit", "测试法院"),
                Map.entry("entrustDate", "2026-06-13"),
                Map.entry("appraisalCategory", "法医临床"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "不予受理测试"),
                Map.entry("entrustAccepted", accepted),
                Map.entry("preliminarySurveyRequired", false),
                Map.entry("materialReceiveRequired", false),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L)
        );
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
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员", "部门负责人"));
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
            SysUserRole existingUserRole = sysUserRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, OPERATOR_ID)
                    .eq(SysUserRole::getRoleId, role.getId()));
            if (existingUserRole == null) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(OPERATOR_ID);
                userRole.setRoleId(role.getId());
                sysUserRoleMapper.insert(userRole);
            }
        }
    }
}