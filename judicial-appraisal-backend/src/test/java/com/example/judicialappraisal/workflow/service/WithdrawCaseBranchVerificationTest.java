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
class WithdrawCaseBranchVerificationTest {

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
    void withdrawCaseFlow_refundBranch() {
        Long caseId = createSubmittedWithdrawCase("6.14-收到撤案函退费分支");

        assertThat(activeTaskNodeCodes(caseId)).contains("LETTER_REGISTER");

        completeTask(caseId, "LETTER_REGISTER", Map.of(
                "withdrawLetterReceivedDate", "2026-06-13",
                "withdrawReason", "原告主动撤诉"
        ));
        
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_DECISION");

        completeTask(caseId, "PROJECT_DECISION", "RETURN", Map.of(
                "refundRequired", true,
                "decisionSummary", "登记信息有误"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("LETTER_REGISTER");

        completeTask(caseId, "LETTER_REGISTER", Map.of(
                "withdrawReason", "原告主动撤诉，双方已和解"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_DECISION");

        completeTask(caseId, "PROJECT_DECISION", "APPROVE", Map.of(
                "refundRequired", true,
                "decisionSummary", "需要退费"
        ));
        
        assertThat(activeTaskNodeCodes(caseId)).contains("REFUND");
        assertThat(runningSubflowCodes(caseId)).contains("refund");
    }
    
    @Test
    void withdrawCaseFlow_noRefundBranch() {
        Long caseId = createSubmittedWithdrawCase("6.14-收到撤案函不退费分支");

        completeTask(caseId, "LETTER_REGISTER", Map.of(
                "withdrawLetterReceivedDate", "2026-06-13",
                "withdrawReason", "鉴定已完成部分工作，不同意退费"
        ));
        
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_DECISION");

        completeTask(caseId, "PROJECT_DECISION", "APPROVE", Map.of(
                "refundRequired", false,
                "decisionSummary", "无需退费，直接终止"
        ));
        
        assertThat(activeTaskNodeCodes(caseId)).contains("TERMINATE_APPRAISAL");
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal");
    }
    
    private Long createSubmittedWithdrawCase(String taskName) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(taskName, "收到撤案函", "测试法院", 1L));
        caseInfo.setCaseNo("JA-WITHDRAW-" + System.currentTimeMillis());
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

        completeTask(caseInfo.getId(), "START", Map.ofEntries(
            Map.entry("linkedWorkflowCode", "field-survey"),
            Map.entry("projectLeaderId", 3L)
        ));

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

    private void ensureOperatorHasAllJudicialRoles() {
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "收件人", "项目负责人"));
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