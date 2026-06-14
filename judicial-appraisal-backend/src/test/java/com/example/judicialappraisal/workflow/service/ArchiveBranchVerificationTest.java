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
class ArchiveBranchVerificationTest {

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
    void archiveFlow_noMail_toCompletion() {
        // 1. Trigger archive from terminate-appraisal (reached via withdraw-case-letter)
        Long caseId = triggerArchiveFromTerminateAppraisal("9.18-归档全流程测试-直接审核");

        // 2. Assert archive subflow is running and at ARCHIVIST_PREPARE
        assertThat(runningSubflowCodes(caseId)).contains("archive");
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");

        // 3. ARCHIVIST_PREPARE -> CENTRAL_REVIEW (Select '直接中心审核')
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "electronicArchiveLocation", "http://nas/archive/v1",
                "deliveryRoute", "直接中心审核",
                "archivistId", OPERATOR_ID,
                "centralArchivistId", OPERATOR_ID,
                "mailerId", OPERATOR_ID
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "CENTRAL_REVIEW");

        // 4. CENTRAL_REVIEW -> END (Success)
        completeTask(caseId, "CENTRAL_REVIEW", Map.of(
                "centralArchiveApproved", true,
                "archiveRoomLocation", "档案室A区-01架-02层"
        ));

        // 5. Final state assertions
        assertThat(runningSubflowCodes(caseId)).isEmpty();
        
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        assertThat(caseInfo.getCaseStatus()).isEqualTo("COMPLETED");
        
        // Assert form data
        Map<String, Object> formData = caseInfo.getFormData();
        assertThat(formData.get("centralArchiveApproved")).isEqualTo(true);
        assertThat(formData.get("archiveRoomLocation")).isEqualTo("档案室A区-01架-02层");
    }

    @Test
    void archiveFlow_mailAndReturnPaths() {
        Long caseId = triggerArchiveFromTerminateAppraisal("9.18-归档全流程测试-邮寄退回");

        // 1. ARCHIVIST_PREPARE -> MAIL_TRANSFER (Select '邮寄入库')
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "electronicArchiveLocation", "http://nas/archive/v2",
                "deliveryRoute", "邮寄入库"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "MAIL_TRANSFER");

        // 2. MAIL_TRANSFER -> Return to ARCHIVIST_PREPARE
        completeTask(caseId, "MAIL_TRANSFER", "RETURN", Map.of(
                "handlerOpinion", "材料包装不全"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_PREPARE");

        // 3. Back to MAIL_TRANSFER
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of("deliveryRoute", "邮寄入库"));
        assertThat(activeTaskNodeCodes(caseId)).contains("MAIL_TRANSFER");

        // 4. MAIL_TRANSFER -> CENTRAL_REVIEW
        completeTask(caseId, "MAIL_TRANSFER", Map.of(
                "mailTrackingNo", "SF123456789"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("CENTRAL_REVIEW");

        // 5. CENTRAL_REVIEW -> Return to ARCHIVIST_PREPARE (Audit failed)
        completeTask(caseId, "CENTRAL_REVIEW", "RETURN", Map.of(
                "centralArchiveApproved", false,
                "handlerOpinion", "纸质件扫描不清晰"
        ));
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_PREPARE");

        // 6. Complete eventually
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of("deliveryRoute", "直接中心审核"));
        completeTask(caseId, "CENTRAL_REVIEW", Map.of(
                "centralArchiveApproved", true,
                "archiveRoomLocation", "档案室B区"
        ));
        assertThat(runningSubflowCodes(caseId)).isEmpty();
    }

    private Long triggerArchiveFromTerminateAppraisal(String taskName) {
        // chain: received-entrust -> preliminary-survey (Not Feasible) -> terminate-appraisal -> archive
        CaseInfo caseInfo = createCase(taskName, "司法鉴定");
        startWorkflow(caseInfo.getId(), "received-entrust");
        
        completeTask(caseInfo.getId(), "START", Map.of("projectLeaderId", OPERATOR_ID, "projectAssistantId", OPERATOR_ID, "departmentHeadId", OPERATOR_ID));
        completeTask(caseInfo.getId(), "INIT_FILL", Map.ofEntries(
                Map.entry("clientName", "测试委托人"),
                Map.entry("entrustOrgName", "测试法院"),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("entrustAccepted", true)
        ));
        completeTask(caseInfo.getId(), "CLERK_REGISTER", Map.of());
        completeTask(caseInfo.getId(), "DEPT_REVIEW", Map.of("entrustAccepted", true));
        completeTask(caseInfo.getId(), "PROJECT_DECISION", Map.of("preliminarySurveyRequired", true));
        
        // Parallel branch: ASSISTANT_NOTICE
        completeTask(caseInfo.getId(), "ASSISTANT_NOTICE", Map.of());
        
        // In preliminary-survey
        completeTask(caseInfo.getId(), "ASSISTANT_PREPARE", Map.of("surveyPlanUploaded", true));
        completeTask(caseInfo.getId(), "PROJECT_REVIEW", Map.of("appraisalConditionMet", false));
        
        // In terminate-appraisal
        assertThat(runningSubflowCodes(caseInfo.getId())).contains("terminate-appraisal");
        
        completeTask(caseInfo.getId(), "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "测试触发归档",
                "draftCompleted", true
        ));
        completeTask(caseInfo.getId(), "PROJECT_REVIEW", Map.of(
                "projectReviewPassed", true,
                "sealRequired", true
        ));
        
        // Complete Seal Subflow
        completeSealApplicationSubflow(caseInfo.getId());
        
        // Now in SEALED_UPLOAD
        assertThat(activeTaskNodeCodes(caseInfo.getId())).contains("SEALED_UPLOAD");
        completeTask(caseInfo.getId(), "SEALED_UPLOAD", Map.of(
                "sealedTerminationUploaded", true,
                "archiveConfirmed", true
        ));
        
        return caseInfo.getId();
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
