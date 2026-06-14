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
@Transactional
class CourtLetterBranchVerificationTest {

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
    void courtLetter_shouldStartWithLetterUploadWhenManuallyCreatedAndLinked() {
        Long caseId = startCourtLetterWorkflow("9.11-手动新建法院函件");

        assertThat(activeTaskNodeCodes(caseId)).containsExactly("LETTER_UPLOAD");
        assertThat(activeTaskNames(caseId)).containsExactly("上传法院函件并选择项目负责人");
    }

    @Test
    void objectionReplyPath_shouldSealArchiveDeliverAndLaunchFinalOpinionReview() {
        Long caseId = createCaseAtSealedReplyUpload("9.11-异议回复后返回送审稿", true);

        completeTask(caseId, "SEALED_REPLY_UPLOAD", ActionCode.APPROVE,
                Map.of("sealedReplyUploaded", true, "archiveConfirmed", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE", "DELIVERY_RELATED_LETTER");
        assertThat(runningSubflowCodes(caseId)).contains("archive");

        completeTask(caseId, "DELIVERY_RELATED_LETTER", ActionCode.APPROVE,
                Map.of("deliveryMethod", "邮寄", "trackingNo", "SF-9-11-001", "deliveryDate", "2026-06-13"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");

        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "返回鉴定意见书送审稿编制"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("FINAL_OPINION_REVIEW", "PROJECT_ASSIGN");
        assertThat(runningSubflowCodes(caseId)).contains("final-opinion-review");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("receivedLetterUploaded", true)
                .containsEntry("projectLeaderAssigned", true)
                .containsEntry("objectionAccepted", true)
                .containsEntry("replyDraftCompleted", true)
                .containsEntry("projectReviewPassed", true)
                .containsEntry("sealRequired", true)
                .containsEntry("sealedReplyUploaded", true)
                .containsEntry("nextRecommendation", "返回鉴定意见书送审稿编制");
    }

    @Test
    void nonObjectionReplyPath_shouldAllowNoSealAndLaunchIssueOpinion() {
        Long caseId = createCaseAtSealedReplyUpload("9.11-非异议需回复后出具意见书", false);

        completeTask(caseId, "SEALED_REPLY_UPLOAD", ActionCode.APPROVE, Map.of("sealedReplyUploaded", true), null);
        completeTask(caseId, "DELIVERY_RELATED_LETTER", ActionCode.APPROVE,
                Map.of("deliveryMethod", "电子送达", "deliveryDate", "2026-06-13"), null);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "进入出具鉴定意见书"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ISSUE_OPINION", "PROJECT_MODIFY");
        assertThat(runningSubflowCodes(caseId)).contains("issue-opinion");
    }

    @Test
    void nonObjectionNoReplyPath_shouldArchiveDirectly() {
        Long caseId = createCaseAtProjectRegister("9.11-非异议无需回复直接归档");

        completeTask(caseId, "PROJECT_REGISTER", ActionCode.APPROVE,
                Map.of("objectionAccepted", false, "replyRequired", false), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ASSISTANT_REPLY");
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantReply() {
        Long caseId = createCaseAtProjectReview("9.11-项目负责人退回回复函", true);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN,
                Map.of("projectReviewPassed", false), "回复函内容需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_REPLY");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ARCHIVIST_CONFIRM");
    }

    @Test
    void archivistReturn_shouldReturnToProjectReview() {
        Long caseId = createCaseAtArchivistConfirm("9.11-档案管理员退回复核", true);

        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.RETURN,
                Map.of("sealRequired", false), "盖章材料需项目负责人复核");

        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("SEAL_APPLICATION", "SEALED_REPLY_UPLOAD");
    }

    @Test
    void deliveryReturn_shouldReturnToSealedReplyUpload() {
        Long caseId = createCaseAtSealedReplyUpload("9.11-发函退回补充盖章件", false);
        completeTask(caseId, "SEALED_REPLY_UPLOAD", ActionCode.APPROVE, Map.of("sealedReplyUploaded", true), null);

        completeTask(caseId, "DELIVERY_RELATED_LETTER", ActionCode.RETURN,
                Map.of("deliveryMethod", "邮寄"), "寄送记录需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_REPLY_UPLOAD");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("NEXT_FLOW_DECISION");
    }

    private Long createCaseAtSealedReplyUpload(String title, boolean sealRequired) {
        Long caseId = createCaseAtArchivistConfirm(title, !sealRequired);
        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.APPROVE, Map.of("sealRequired", sealRequired), null);
        if (sealRequired) {
            assertThat(runningSubflowCodes(caseId)).contains("seal-application");
            completeSealApplicationSubflow(caseId);
        }
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_REPLY_UPLOAD");
        return caseId;
    }

    private Long createCaseAtArchivistConfirm(String title, boolean nonObjection) {
        Long caseId = createCaseAtProjectReview(title, nonObjection);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("projectReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_CONFIRM");
        return caseId;
    }

    private Long createCaseAtProjectReview(String title, boolean nonObjection) {
        Long caseId = createCaseAtProjectRegister(title);
        Map<String, Object> decision = nonObjection
                ? Map.of("objectionAccepted", false, "replyRequired", true)
                : Map.of("objectionAccepted", true, "replyRequired", false);
        completeTask(caseId, "PROJECT_REGISTER", ActionCode.APPROVE, decision, null);
        completeTask(caseId, "ASSISTANT_REPLY", ActionCode.APPROVE,
                Map.of("replyDraftCompleted", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createCaseAtProjectRegister(String title) {
        Long caseId = startCourtLetterWorkflow(title);
        completeTask(caseId, "LETTER_UPLOAD", ActionCode.APPROVE, courtLetterUploadData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REGISTER");
        return caseId;
    }

    private Map<String, Object> courtLetterUploadData() {
        return Map.ofEntries(
                Map.entry("caseNo", "JA-COURT-LETTER-9-11"),
                Map.entry("linkedWorkflowCode", "issue-draft-opinion"),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L),
                Map.entry("archivistId", 5L),
                Map.entry("receivedLetterUploaded", true),
                Map.entry("projectLeaderAssigned", true),
                Map.entry("letterType", "异议函"),
                Map.entry("letterReceivedDate", "2026-06-13"),
                Map.entry("letterSummary", "法院对征求意见稿提出异议")
        );
    }

    private Long startCourtLetterWorkflow(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "收到法院其他函件", "测试法院", 1L));
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());
        caseInfoMapper.updateById(caseInfo);

        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, "court-letter")
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
        wfInstance.setCurrentNodeCode("LETTER_UPLOAD");
        wfInstance.setCurrentNodeName("上传法院函件并选择项目负责人");
        caseWfInstanceMapper.insert(wfInstance);

        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseInfo.getId());
        nodeInstance.setWfInstanceId(wfInstance.getId());
        nodeInstance.setNodeCode("LETTER_UPLOAD");
        nodeInstance.setNodeName("上传法院函件并选择项目负责人");
        nodeInstance.setStatus("running");
        caseNodeInstanceMapper.insert(nodeInstance);

        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstance.getId());
        task.setNodeInstanceId(nodeInstance.getId());
        task.setTaskType("candidate");
        task.setTaskTitle(title + " - 上传法院函件并选择项目负责人");
        task.setNodeCode("LETTER_UPLOAD");
        task.setNodeName("上传法院函件并选择项目负责人");
        task.setStatus("pending");
        task.setAssigneeId(OPERATOR_ID);
        task.setAssigneeName(OPERATOR_NAME);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        return caseInfo.getId();
    }

    private void completeSealApplicationSubflow(Long caseId) {
        completeTask(caseId, "APPLICANT_SUBMIT", ActionCode.APPROVE, Map.of(
                "caseNo", "JA-SEAL-9-11",
                "applicantId", 5L,
                "archivistId", 5L,
                "sealOperatorId", 6L,
                "applicationReason", "法院函件回复函用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true,
                "archivistReviewed", true,
                "sealCompleted", true,
                "sealedScanUploaded", true
        ), null);
        completeTask(caseId, "ARCHIVIST_REVIEW", ActionCode.APPROVE, Map.of("archivistReviewed", true), null);
        completeTask(caseId, "SEAL_OPERATOR", ActionCode.APPROVE, Map.of("sealCompleted", true), null);
        completeTask(caseId, "ARCHIVIST_UPLOAD", ActionCode.APPROVE, Map.of("sealedScanUploaded", true), null);
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
                task.getId(), actionCode, opinion == null ? "9.11 自动分支验证" : opinion,
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
            Long count = sysUserRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, OPERATOR_ID)
                    .eq(SysUserRole::getRoleId, role.getId()));
            if (count == 0) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(OPERATOR_ID);
                userRole.setRoleId(role.getId());
                sysUserRoleMapper.insert(userRole);
            }
        }
    }
}
