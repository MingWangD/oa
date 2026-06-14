package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
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
class FinanceReimbursementBranchVerificationTest {

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
    void expenseReimbursement_independentFlow_toCompletion() {
        // 1. Independent Start
        CaseInfo caseInfo = createCase("9.16-财务报销独立发起", "财务报销");
        startWorkflow(caseInfo.getId(), "expense-reimbursement");

        // 2. INITIATOR_SUBMIT
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("INITIATOR_SUBMIT");
        completeTask(caseInfo.getId(), "INITIATOR_SUBMIT", Map.of(
                "expenseSummary", "测试打车费和住宿费",
                "expenseAmount", 1250.50,
                "invoiceSummary", "发票共3张",
                "initiatorId", OPERATOR_ID,
                "financeId", OPERATOR_ID
        ));

        // 3. FINANCE_PROCESS (Approve)
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("FINANCE_PROCESS");
        completeTask(caseInfo.getId(), "FINANCE_PROCESS", Map.of(
                "financeProcessed", true,
                "financeResult", "已报销",
                "paymentDate", "2026-06-14",
                "handlerOpinion", "费用核对无误，已打款"
        ));

        // 4. Final State check
        assertThat(activeTaskNodeCodes(caseInfo.getId())).isEmpty();
        CaseInfo finalCase = caseInfoMapper.selectById(caseInfo.getId());
        assertThat(finalCase.getCaseStatus()).isEqualTo("COMPLETED");

        Map<String, Object> formData = finalCase.getFormData();
        assertThat(formData.get("expenseAmount")).isEqualTo(1250.50);
        assertThat(formData.get("financeResult")).isEqualTo("已报销");
    }

    @Test
    void expenseReimbursement_returnPath() {
        // 1. Independent Start
        CaseInfo caseInfo = createCase("9.16-财务报销退回路径", "财务报销");
        startWorkflow(caseInfo.getId(), "expense-reimbursement");

        // 2. INITIATOR_SUBMIT
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("INITIATOR_SUBMIT");
        completeTask(caseInfo.getId(), "INITIATOR_SUBMIT", Map.of(
                "expenseSummary", "测试退回",
                "expenseAmount", 500,
                "invoiceSummary", "发票1张"
        ));

        // 3. FINANCE_PROCESS (Return to INITIATOR_SUBMIT)
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("FINANCE_PROCESS");
        completeTask(caseInfo.getId(), "FINANCE_PROCESS", "RETURN", Map.of(
                "financeProcessed", true,
                "financeResult", "退回补充",
                "handlerOpinion", "发票抬头不对，请重新开具"
        ));

        // 4. Back to INITIATOR_SUBMIT
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("INITIATOR_SUBMIT");
        completeTask(caseInfo.getId(), "INITIATOR_SUBMIT", Map.of(
                "invoiceSummary", "发票1张（已重新开具正确抬头）"
        ));

        // 5. FINANCE_PROCESS (Approve)
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("FINANCE_PROCESS");
        completeTask(caseInfo.getId(), "FINANCE_PROCESS", Map.of(
                "financeProcessed", true,
                "financeResult", "已报销",
                "paymentDate", "2026-06-15"
        ));

        // 6. Final State check
        assertThat(activeTaskNodeCodes(caseInfo.getId())).isEmpty();
        CaseInfo finalCase = caseInfoMapper.selectById(caseInfo.getId());
        assertThat(finalCase.getCaseStatus()).isEqualTo("COMPLETED");
    }

    private CaseInfo createCase(String title, String type) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, type, "测试人员", 1L));
        caseInfo.setCaseNo("JA-" + System.currentTimeMillis());
        caseInfoMapper.updateById(caseInfo);
        return caseInfo;
    }

    private void startWorkflow(Long caseId, String wfCode) {
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

        completeTask(caseId, "START", Map.of("initiatorId", OPERATOR_ID, "financeId", OPERATOR_ID));
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

    private void ensureOperatorHasAllJudicialRoles() {
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
        roleNames.addAll(List.of("发起者", "发起人", "申请人", "收件人", "项目负责人", "档案管理员", "财务", "项目辅助人", "部门负责人", "盖章经办人", "技术负责人", "质量控制人", "邮寄人员", "中心档案管理员"));

        for (String name : roleNames) {
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