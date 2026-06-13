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
class SealApplicationBranchVerificationTest {

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
    void sealApplication_independentFlow_toCompletion() {
        // 1. Independent Start
        CaseInfo caseInfo = createCase("9.19-用章申请独立发起", "用章申请表");
        startWorkflow(caseInfo.getId(), "seal-application");

        // 2. APPLICANT_SUBMIT
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("APPLICANT_SUBMIT");
        completeTask(caseInfo.getId(), "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "测试独立用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true,
                "applicantId", OPERATOR_ID,
                "archivistId", OPERATOR_ID,
                "sealOperatorId", OPERATOR_ID
        ));

        // 3. ARCHIVIST_REVIEW -> SEAL_OPERATOR (Approve)
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("ARCHIVIST_REVIEW");
        completeTask(caseInfo.getId(), "ARCHIVIST_REVIEW", Map.of(
                "archivistReviewed", true,
                "handlerOpinion", "材料审核通过"
        ));

        // 4. SEAL_OPERATOR -> ARCHIVIST_UPLOAD (Approve)
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("SEAL_OPERATOR");
        completeTask(caseInfo.getId(), "SEAL_OPERATOR", Map.of(
                "sealCompleted", true
        ));

        // 5. ARCHIVIST_UPLOAD -> END
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("ARCHIVIST_UPLOAD");
        completeTask(caseInfo.getId(), "ARCHIVIST_UPLOAD", Map.of(
                "sealedScanUploaded", true
        ));

        // 6. Final State
        assertThat(activeTaskNodeCodes(caseInfo.getId())).isEmpty();
        CaseInfo finalCase = caseInfoMapper.selectById(caseInfo.getId());
        assertThat(finalCase.getCaseStatus()).isEqualTo("COMPLETED");
        
        Map<String, Object> formData = finalCase.getFormData();
        assertThat(formData.get("sealCompleted")).isEqualTo(true);
        assertThat(formData.get("sealedScanUploaded")).isEqualTo(true);
    }

    @Test
    void sealApplication_subflowWaitAndReturnPaths() {
        // 1. Trigger from Terminate Appraisal (reached via a simple chain)
        Long caseId = triggerSealFromTerminateAppraisal("9.19-子流程等待与退回路径测试");

        // 2. Assert parent flow is waiting
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal", "seal-application");
        assertThat(getTask(caseId, "SEAL_APPLICATION").getStatus()).isEqualTo("subflow_running");
        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION", "APPLICANT_SUBMIT");

        // 3. APPLICANT_SUBMIT -> ARCHIVIST_REVIEW
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "测试子流程用章",
                "sealMode", "电子盖章",
                "applicationFilesPrepared", true
        ));

        // 4. ARCHIVIST_REVIEW -> Return to APPLICANT_SUBMIT
        completeTask(caseId, "ARCHIVIST_REVIEW", "RETURN", Map.of(
                "archivistReviewed", false,
                "handlerOpinion", "文件页码不对"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION", "APPLICANT_SUBMIT");

        // 5. APPLICANT_SUBMIT -> ARCHIVIST_REVIEW -> SEAL_OPERATOR
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of("applicationFilesPrepared", true));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION", "SEAL_OPERATOR");

        // 6. SEAL_OPERATOR -> Return to ARCHIVIST_REVIEW
        completeTask(caseId, "SEAL_OPERATOR", "RETURN", Map.of(
                "sealCompleted", false,
                "handlerOpinion", "印油不足"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION", "ARCHIVIST_REVIEW");

        // 7. Complete Seal Flow
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));

        // 8. Parent Awakening
        // After seal-application ends, terminate-appraisal wakes up and moves to SEALED_UPLOAD
        assertThat(runningSubflowCodes(caseId)).contains("terminate-appraisal");
        assertThat(runningSubflowCodes(caseId)).doesNotContain("seal-application");
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_UPLOAD");
    }

    private Long triggerSealFromTerminateAppraisal(String taskName) {
        CaseInfo caseInfo = createCase(taskName, "收到撤案函");
        startWorkflow(caseInfo.getId(), "withdraw-case-letter");
        
        // startWorkflow already completes START
        completeTask(caseInfo.getId(), "LETTER_REGISTER", Map.of("withdrawLetterReceivedDate", "2026-06-13", "withdrawReason", "测试触发用章"));
        completeTask(caseInfo.getId(), "PROJECT_DECISION", Map.of("refundRequired", false));
        
        completeTask(caseInfo.getId(), "ASSISTANT_DRAFT", Map.of("draftCompleted", true));
        completeTask(caseInfo.getId(), "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "sealRequired", true));
        
        return caseInfo.getId();
    }

    private CaseInfo createCase(String title, String type) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, type, "测试法院", 1L));
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

        completeTask(caseId, "START", Map.of("projectLeaderId", OPERATOR_ID, "archivistId", OPERATOR_ID, "sealOperatorId", OPERATOR_ID));
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
        roleNames.addAll(List.of("发起者", "申请人", "收件人", "项目负责人", "档案管理员", "财务", "项目辅助人", "部门负责人", "盖章经办人", "技术负责人", "质量控制人", "邮寄人员", "中心档案管理员"));
        
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
