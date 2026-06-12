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
class ReceivedEntrustBranchVerificationTest {

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
    void rejectAcceptanceBranch_shouldLaunchRejectAcceptanceSubflowOnly() {
        Long caseId = createSubmittedReceivedEntrustCase("3.1-不受理分支");

        completeTask(caseId, "INIT_FILL", baseFormData(false, false, false));
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", false));

        assertThat(activeTaskNodeCodes(caseId)).contains("REJECT_ACCEPTANCE");
        assertThat(runningSubflowCodes(caseId)).containsExactly("reject-acceptance");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PROJECT_DECISION", "ASSISTANT_NOTICE", "MATERIAL_RECEIVE", "PRELIMINARY_SURVEY", "PAYMENT_NOTICE");
    }

    @Test
    void acceptedWithPreliminarySurvey_shouldNotifyAssistantAndLaunchPreliminarySurvey() {
        Long caseId = createSubmittedReceivedEntrustCase("3.1-受理初勘分支");

        reachProjectDecision(caseId, baseFormData(true, true, false));
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", true, "materialReceiveRequired", false));

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_NOTICE", "PRELIMINARY_SURVEY");
        assertThat(runningSubflowCodes(caseId)).containsExactly("preliminary-survey");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("MATERIAL_RECEIVE", "PAYMENT_NOTICE", "REJECT_ACCEPTANCE");
    }

    @Test
    void acceptedWithoutPreliminarySurvey_shouldNotifyAssistantAndLaunchPaymentNotice() {
        Long caseId = createSubmittedReceivedEntrustCase("3.1-受理不初勘分支");

        reachProjectDecision(caseId, baseFormData(true, false, false));
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", false));

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_NOTICE", "PAYMENT_NOTICE");
        assertThat(runningSubflowCodes(caseId)).containsExactly("payment-notice");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("MATERIAL_RECEIVE", "PRELIMINARY_SURVEY", "REJECT_ACCEPTANCE");
    }

    @Test
    void acceptedWithPreliminarySurveyAndMaterialReceive_shouldCreateAllSelectedParallelBranches() {
        Long caseId = createSubmittedReceivedEntrustCase("3.1-初勘材料并行分支");

        reachProjectDecision(caseId, baseFormData(true, true, true));
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", true, "materialReceiveRequired", true));

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_NOTICE", "MATERIAL_RECEIVE", "PRELIMINARY_SURVEY");
        assertThat(runningSubflowCodes(caseId)).containsExactly("preliminary-survey");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PAYMENT_NOTICE", "REJECT_ACCEPTANCE");
    }

    @Test
    void acceptedWithPaymentNoticeAndMaterialReceive_shouldCreateAllSelectedParallelBranches() {
        Long caseId = createSubmittedReceivedEntrustCase("3.1-交费材料并行分支");

        reachProjectDecision(caseId, baseFormData(true, false, true));
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", true));

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_NOTICE", "MATERIAL_RECEIVE", "PAYMENT_NOTICE");
        assertThat(runningSubflowCodes(caseId)).containsExactly("payment-notice");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PRELIMINARY_SURVEY", "REJECT_ACCEPTANCE");
    }

    private Long createSubmittedReceivedEntrustCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 3.1 分支验证"), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private void reachProjectDecision(Long caseId, Map<String, Object> initialFormData) {
        completeTask(caseId, "INIT_FILL", initialFormData);
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", true));
    }

    private Map<String, Object> baseFormData(boolean accepted, boolean preliminarySurveyRequired, boolean materialReceiveRequired) {
        return Map.ofEntries(
                Map.entry("clientName", "3.1 分支验证委托人"),
                Map.entry("serialNo", "BRANCH-3-1"),
                Map.entry("initiatorName", "测试发起人"),
                Map.entry("initiatedDate", "2026-06-12"),
                Map.entry("receivedDate", "2026-06-12"),
                Map.entry("caseNo", "JA-BRANCH-3-1"),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "3.1 收到委托书分支验证"),
                Map.entry("entrustAccepted", accepted),
                Map.entry("preliminarySurveyRequired", preliminarySurveyRequired),
                Map.entry("materialReceiveRequired", materialReceiveRequired),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L)
        );
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), ActionCode.APPROVE, "3.1 分支验证", null, null, null, formData, null);
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
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(OPERATOR_ID);
            userRole.setRoleId(role.getId());
            sysUserRoleMapper.insert(userRole);
        }
    }
}
