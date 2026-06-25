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
class RefundBranchVerificationTest {

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

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void refundFlow_fullPath_to_termination() {
        // 1. Trigger refund from withdraw-case-letter
        Long caseId = triggerRefundFromWithdrawCase("9.15-退费全流程测试");

        // 2. Assert refund subflow is running and at PROJECT_PREPARE
        assertThat(runningSubflowCodes(caseId)).contains("refund");
        assertThat(activeTaskNodeCodes(caseId)).contains("REFUND", "PROJECT_PREPARE");

        // 3. PROJECT_PREPARE -> ARCHIVIST_APPLY
        completeTask(caseId, "PROJECT_PREPARE", Map.of(
                "contractChangeCompleted", true,
                "revenueConfirmed", true,
                "projectLeaderId", OPERATOR_ID,
                "archivistId", OPERATOR_ID,
                "financeId", OPERATOR_ID
        ));
        assertFormDataContains(caseId, Map.of(
                "contractChangeCompleted", true,
                "revenueConfirmed", true,
                "projectLeaderId", OPERATOR_ID,
                "archivistId", OPERATOR_ID,
                "financeId", OPERATOR_ID
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("REFUND", "ARCHIVIST_APPLY");

        // 4. ARCHIVIST_APPLY -> FINANCE_PAYMENT
        completeTask(caseId, "ARCHIVIST_APPLY", Map.of(
                "refundApplicationSubmitted", true
        ));
        assertFormDataContains(caseId, Map.of("refundApplicationSubmitted", true));
        assertThat(activeTaskNodeCodes(caseId)).contains("REFUND", "FINANCE_PAYMENT");

        // 5. FINANCE_PAYMENT -> TERMINATE_APPRAISAL (Successful payment)
        completeTask(caseId, "FINANCE_PAYMENT", Map.of(
                "paymentCompleted", true,
                "paymentDate", "2026-06-13",
                "paymentVoucherUploaded", true
        ));
        assertFormDataContains(caseId, Map.of(
                "paymentCompleted", true,
                "paymentDate", "2026-06-13",
                "paymentVoucherUploaded", true
        ));
        
        // Assert refund subflow finishes (or moves to subflow node) and triggers terminate-appraisal
        assertThat(activeTaskNodeCodes(caseId)).contains("REFUND", "TERMINATE_APPRAISAL", "ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("refund", "terminate-appraisal");
    }

    @Test
    void refundFlow_returnPaths() {
        Long caseId = triggerRefundFromWithdrawCase("9.15-退费退回路径测试");

        // PROJECT_PREPARE -> ARCHIVIST_APPLY
        completeTask(caseId, "PROJECT_PREPARE", Map.of(
                "contractChangeCompleted", true,
                "revenueConfirmed", true
        ));

        // ARCHIVIST_APPLY -> Return to PROJECT_PREPARE
        completeTask(caseId, "ARCHIVIST_APPLY", "RETURN", Map.of(
                "refundApplicationSubmitted", false,
                "handlerOpinion", "材料不齐"
        ));
        assertFormDataContains(caseId, Map.of("refundApplicationSubmitted", false));
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_PREPARE");

        // Back to ARCHIVIST_APPLY
        completeTask(caseId, "PROJECT_PREPARE", Map.of("contractChangeCompleted", true, "revenueConfirmed", true));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_APPLY");

        // ARCHIVIST_APPLY -> FINANCE_PAYMENT
        completeTask(caseId, "ARCHIVIST_APPLY", Map.of("refundApplicationSubmitted", true));

        // FINANCE_PAYMENT -> Return to ARCHIVIST_APPLY (Payment failed/incomplete)
        completeTask(caseId, "FINANCE_PAYMENT", "RETURN", Map.of(
                "paymentCompleted", false,
                "handlerOpinion", "账号信息错误"
        ));
        assertFormDataContains(caseId, Map.of("paymentCompleted", false));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_APPLY");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("terminate-appraisal");
    }

    private Long triggerRefundFromWithdrawCase(String taskName) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(taskName, "收到撤案函", "测试法院", 1L));
        caseInfo.setCaseNo("JA-REFUND-TRIGGER-" + System.currentTimeMillis());
        caseInfo.setCaseStatus("PROCESSING");
        caseInfoMapper.updateById(caseInfo);
        
        com.example.judicialappraisal.workflow.entity.WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<com.example.judicialappraisal.workflow.entity.WfDefinition>()
                .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getWfCode, "withdraw-case-letter")
                .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getPublishStatus, "published")
                .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getDeleted, 0)
                .orderByDesc(com.example.judicialappraisal.workflow.entity.WfDefinition::getVersionNo)
                .orderByDesc(com.example.judicialappraisal.workflow.entity.WfDefinition::getId)
                .last("LIMIT 1"));
                
        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setCaseId(caseInfo.getId());
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

        completeTask(caseInfo.getId(), "START", Map.of("projectLeaderId", OPERATOR_ID));
        completeTask(caseInfo.getId(), "LETTER_REGISTER", Map.of("withdrawLetterReceivedDate", "2026-06-13", "withdrawReason", "测试退费触发"));
        completeTask(caseInfo.getId(), "PROJECT_DECISION", Map.of("refundRequired", true));
        
        return caseInfo.getId();
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
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "收件人", "项目负责人", "档案管理员", "财务", "项目辅助人"));
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
}
