package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.caseinfo.service.CaseInfoService;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.platform.service.JudicialConfigImportService;
import com.example.judicialappraisal.platform.service.PlatformCatalogService;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.entity.CaseNodeInstance;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.mapper.CaseNodeInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class CourtAppearanceBranchVerificationTest {

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
    private CaseNodeInstanceMapper caseNodeInstanceMapper;

    @Autowired
    private CaseTaskMapper caseTaskMapper;

    @Autowired
    private CaseWfInstanceMapper caseWfInstanceMapper;

    @Autowired
    private WfDefinitionMapper wfDefinitionMapper;

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
    @Transactional
    void courtAppearance_shouldStartWithProjectRegisterWhenManuallyCreatedAndLinked() {
        Long caseId = startCourtAppearanceWorkflow("9.12-手动新建出庭通知");

        assertThat(activeTaskNodeCodes(caseId)).containsExactly("PROJECT_REGISTER");
        assertThat(activeTaskNames(caseId)).containsExactly("项目负责人确认出庭通知和项目编号");
    }

    @Test
    @Transactional
    void feeRequiredPath_shouldLaunchPaymentNoticeSubflow() {
        Long caseId = startCourtAppearanceWorkflow("9.12-出庭费通知");

        completeTask(caseId, "PROJECT_REGISTER", ActionCode.APPROVE,
                projectRegisterData("9.12-出庭费通知", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("PAYMENT_NOTICE", "ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("payment-notice");
    }

    @Test
    void noFeeCenterArchivePath_shouldRetrievePrepareAppearAndArchive() {
        Long caseId = createCaseAtArchiveRetrieval("场景7：出庭通知流程-" + System.currentTimeMillis(), false);

        completeTask(caseId, "ARCHIVE_RETRIEVAL", ActionCode.APPROVE, Map.of(
                "archiveRetrievalRequired", true,
                "archiveBorrowRegisterUploaded", true,
                "storedInCenterArchive", true,
                "archiveRetrieved", false
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("CENTER_ARCHIVE_RETRIEVAL");

        completeTask(caseId, "CENTER_ARCHIVE_RETRIEVAL", ActionCode.APPROVE,
                Map.of("centerArchiveHandled", true, "archiveRetrieved", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("APPEARANCE_PREPARE");

        completeTask(caseId, "APPEARANCE_PREPARE", ActionCode.APPROVE, Map.of(
                "appearancePreparationCompleted", true,
                "appearancePreparationFilesUploaded", true
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("COURT_APPEARANCE");

        completeTask(caseId, "COURT_APPEARANCE", ActionCode.APPROVE, Map.of(
                "appearanceStatusEntered", true,
                "appearanceCompleted", true,
                "appearanceSummary", "已按通知完成出庭"
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("POST_APPEARANCE");

        completeTask(caseId, "POST_APPEARANCE", ActionCode.APPROVE,
                Map.of("postAppearanceMaterialsUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");

        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "归档", "archiveConfirmed", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("courtNoticeUploaded", true)
                .containsEntry("projectNoConfirmed", true)
                .containsEntry("centerArchiveHandled", true)
                .containsEntry("appearancePreparationCompleted", true)
                .containsEntry("appearanceStatusEntered", true)
                .containsEntry("postAppearanceMaterialsUploaded", true)
                .containsEntry("nextRecommendation", "归档");

        // Complete archiving subflow
        completeTask(caseId, "ARCHIVIST_PREPARE", ActionCode.APPROVE, Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "deliveryRoute", "直接中心审核"
        ), null);
        completeTask(caseId, "CENTRAL_REVIEW", ActionCode.APPROVE, Map.of(
                "centralArchiveApproved", true,
                "archiveRoomLocation", "A1-D4架"
        ), null);

        // Assert case is fully completed and archived
        CaseInfo finalCase = caseInfoMapper.selectById(caseId);
        assertThat(finalCase.getCaseStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Transactional
    void nonCenterArchivePath_shouldSkipCenterArchiveAndLaunchIssueOpinion() {
        Long caseId = createCaseAtArchiveRetrieval("9.12-非中心档案室调档后出具意见书", false);

        completeTask(caseId, "ARCHIVE_RETRIEVAL", ActionCode.APPROVE, Map.of(
                "archiveRetrievalRequired", true,
                "archiveBorrowRegisterUploaded", true,
                "storedInCenterArchive", false,
                "archiveRetrieved", true
        ), null);
        completeTask(caseId, "APPEARANCE_PREPARE", ActionCode.APPROVE,
                Map.of("appearancePreparationCompleted", true, "appearancePreparationFilesUploaded", true), null);
        completeTask(caseId, "COURT_APPEARANCE", ActionCode.APPROVE,
                Map.of("appearanceStatusEntered", true, "appearanceCompleted", true), null);
        completeTask(caseId, "POST_APPEARANCE", ActionCode.APPROVE,
                Map.of("postAppearanceMaterialsUploaded", true), null);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "进入出具鉴定意见书"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ISSUE_OPINION", "PROJECT_MODIFY");
        assertThat(runningSubflowCodes(caseId)).contains("issue-opinion");
    }

    @Test
    @Transactional
    void archiveRetrievalReturn_shouldReturnToProjectRegister() {
        Long caseId = createCaseAtArchiveRetrieval("9.12-调档退回确认", false);

        completeTask(caseId, "ARCHIVE_RETRIEVAL", ActionCode.RETURN,
                Map.of("archiveBorrowRegisterUploaded", false), "调档信息需重新确认");

        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REGISTER");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("APPEARANCE_PREPARE");
    }

    @Test
    @Transactional
    void courtAppearanceNotCompleted_shouldReturnToPrepare() {
        Long caseId = createCaseAtCourtAppearance("9.12-出庭未完成退回准备");

        completeTask(caseId, "COURT_APPEARANCE", ActionCode.RETURN,
                Map.of("appearanceCompleted", false), "出庭准备材料需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("APPEARANCE_PREPARE");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("POST_APPEARANCE");
    }

    private Long createCaseAtCourtAppearance(String title) {
        Long caseId = createCaseAtArchiveRetrieval(title, false);
        completeTask(caseId, "ARCHIVE_RETRIEVAL", ActionCode.APPROVE, Map.of(
                "archiveRetrievalRequired", true,
                "archiveBorrowRegisterUploaded", true,
                "storedInCenterArchive", false,
                "archiveRetrieved", true
        ), null);
        completeTask(caseId, "APPEARANCE_PREPARE", ActionCode.APPROVE, Map.of(
                "appearancePreparationCompleted", true,
                "appearancePreparationFilesUploaded", true
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("COURT_APPEARANCE");
        return caseId;
    }

    private Long createCaseAtArchiveRetrieval(String title, boolean feeRequired) {
        Long caseId = startCourtAppearanceWorkflow(title);
        completeTask(caseId, "PROJECT_REGISTER", ActionCode.APPROVE, projectRegisterData(title, feeRequired), null);
        assertThat(activeTaskNodeCodes(caseId)).contains(feeRequired ? "PAYMENT_NOTICE" : "ARCHIVE_RETRIEVAL");
        return caseId;
    }

    private Map<String, Object> projectRegisterData(String caseNo, boolean feeRequired) {
        return Map.ofEntries(
                Map.entry("caseNo", caseNo),
                Map.entry("linkedWorkflowCode", "issue-opinion"),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L),
                Map.entry("archivistId", 5L),
                Map.entry("financeId", 6L),
                Map.entry("courtNoticeUploaded", true),
                Map.entry("courtName", "测试法院"),
                Map.entry("noticeReceivedDate", "2026-06-13"),
                Map.entry("appearanceDate", "2026-06-20"),
                Map.entry("appearanceLocation", "第一法庭"),
                Map.entry("projectNoConfirmed", true),
                Map.entry("appearanceFeeRequired", feeRequired),
                Map.entry("archiveRetrievalRequired", true)
        );
    }

    private Long startCourtAppearanceWorkflow(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "收到出庭通知", "测试法院", 1L));
        caseInfo.setCaseNo(title + "-" + System.currentTimeMillis());
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());
        caseInfoMapper.updateById(caseInfo);

        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, "court-appearance")
                .eq(WfDefinition::getPublishStatus, "published")
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("LIMIT 1"));
        assertThat(definition).isNotNull();

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setCaseId(caseInfo.getId());
        wfInstance.setWfId(definition.getId());
        wfInstance.setWfCode(definition.getWfCode());
        wfInstance.setWfName(definition.getWfName());
        wfInstance.setStatus("running");
        wfInstance.setCurrentNodeCode("PROJECT_REGISTER");
        wfInstance.setCurrentNodeName("项目负责人确认出庭通知和项目编号");
        caseWfInstanceMapper.insert(wfInstance);

        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseInfo.getId());
        nodeInstance.setWfInstanceId(wfInstance.getId());
        nodeInstance.setNodeCode("PROJECT_REGISTER");
        nodeInstance.setNodeName("项目负责人确认出庭通知和项目编号");
        nodeInstance.setStatus("running");
        caseNodeInstanceMapper.insert(nodeInstance);

        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstance.getId());
        task.setNodeInstanceId(nodeInstance.getId());
        task.setTaskType("candidate");
        task.setTaskTitle(title + " - 项目负责人确认出庭通知和项目编号");
        task.setNodeCode("PROJECT_REGISTER");
        task.setNodeName("项目负责人确认出庭通知和项目编号");
        task.setStatus("pending");
        task.setAssigneeId(OPERATOR_ID);
        task.setAssigneeName(OPERATOR_NAME);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        return caseInfo.getId();
    }

    private List<String> activeTaskNodeCodes(Long caseId) {
        return activeTasks(caseId).stream()
                .map(CaseTask::getNodeCode)
                .toList();
    }

    private List<String> activeTaskNames(Long caseId) {
        return activeTasks(caseId).stream()
                .map(CaseTask::getNodeName)
                .toList();
    }

    private List<CaseTask> activeTasks(Long caseId) {
        return caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running")
                .orderByAsc(CaseTask::getId));
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

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String opinion) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode
                + ", active tasks: " + activeTaskNodeCodes(caseId)).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, opinion == null ? "9.12 自动分支验证" : opinion,
                null, null, null, null, null, formData, null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
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
