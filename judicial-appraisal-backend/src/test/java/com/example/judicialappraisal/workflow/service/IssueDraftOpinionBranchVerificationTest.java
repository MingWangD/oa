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
class IssueDraftOpinionBranchVerificationTest {

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
    void issueDraftOpinion_shouldStartWithAssistantSupplement() {
        Long caseId = startIssueDraftOpinionWorkflow("9.10-出具征求意见稿首节点");

        assertThat(activeTaskNodeCodes(caseId)).containsExactly("ASSISTANT_SUPPLEMENT");
        assertThat(activeTaskNames(caseId)).containsExactly("项目辅助人编制并上传鉴定说明函");
    }

    @Test
    void noObjectionPath_shouldArchiveDeliverAndLaunchFinalOpinionReview() {
        Long caseId = createCaseAtAssistantSealedUpload("9.10-无异议进入送审稿编制", false);

        completeTask(caseId, "SEALED_UPLOAD", ActionCode.APPROVE,
                Map.of("sealedDraftOpinionUploaded", true, "archiveConfirmed", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE", "DELIVERY");
        assertThat(runningSubflowCodes(caseId)).contains("archive");

        completeTask(caseId, "DELIVERY", ActionCode.APPROVE,
                Map.of("deliveryMethod", "邮寄", "trackingNo", "SF-9-10-001"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("WAIT_FEEDBACK");

        completeTask(caseId, "WAIT_FEEDBACK", ActionCode.APPROVE,
                Map.of("feedbackReceived", true, "feedbackHasObjection", false, "feedbackDecision", "无异议或未反馈"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("FINAL_OPINION_REVIEW", "PROJECT_ASSIGN");
        assertThat(runningSubflowCodes(caseId)).contains("final-opinion-review");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("explainLetterDrafted", true)
                .containsEntry("projectReviewPassed", true)
                .containsEntry("draftOpinionUploaded", true)
                .containsEntry("sealedDraftOpinionUploaded", true)
                .containsEntry("archiveConfirmed", true)
                .containsEntry("trackingNo", "SF-9-10-001")
                .containsEntry("feedbackDecision", "无异议或未反馈");
    }

    @Test
    void objectionPath_shouldLaunchCourtLetterSubflow() {
        Long caseId = createCaseAtWaitFeedback("9.10-收到异议进入法院函件");

        completeTask(caseId, "WAIT_FEEDBACK", ActionCode.APPROVE, Map.of(
                "feedbackReceived", true,
                "feedbackHasObjection", true,
                "feedbackDecision", "收到异议",
                "objectionReason", "法院反馈存在异议"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("COURT_LETTER", "LETTER_UPLOAD");
        assertThat(runningSubflowCodes(caseId)).contains("court-letter");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("FINAL_OPINION_REVIEW");
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantSupplement() {
        Long caseId = createCaseAtProjectReview("9.10-项目负责人审核退回");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN,
                Map.of("projectReviewPassed", false), "鉴定说明函需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SUPPLEMENT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ARCHIVIST_CONFIRM");
    }

    @Test
    void archivistReturn_shouldReturnToAssistantSupplement() {
        Long caseId = createCaseAtArchivistConfirm("9.10-档案管理员退回说明函");

        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.RETURN,
                Map.of("draftOpinionUploaded", false), "征求意见稿附件需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SUPPLEMENT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("SEAL_APPLICATION", "SEALED_UPLOAD");
    }

    @Test
    void deliveryReturn_shouldReturnToSealedUpload() {
        Long caseId = createCaseAtDelivery("9.10-材料寄出退回补充盖章件");

        completeTask(caseId, "DELIVERY", ActionCode.RETURN,
                Map.of("deliveryMethod", "邮寄"), "快递信息或盖章扫描件需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_UPLOAD");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("WAIT_FEEDBACK");
    }

    private Long createCaseAtDelivery(String title) {
        Long caseId = createCaseAtAssistantSealedUpload(title, false);
        completeTask(caseId, "SEALED_UPLOAD", ActionCode.APPROVE,
                Map.of("sealedDraftOpinionUploaded", true, "archiveConfirmed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("DELIVERY");
        return caseId;
    }

    private Long createCaseAtWaitFeedback(String title) {
        Long caseId = createCaseAtDelivery(title);
        completeTask(caseId, "DELIVERY", ActionCode.APPROVE,
                Map.of("deliveryMethod", "电子送达"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("WAIT_FEEDBACK");
        return caseId;
    }

    private Long createCaseAtAssistantSealedUpload(String title, boolean sealRequired) {
        Long caseId = createCaseAtArchivistConfirm(title);
        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.APPROVE,
                Map.of("sealRequired", sealRequired, "draftOpinionUploaded", true), null);
        if (sealRequired) {
            assertThat(runningSubflowCodes(caseId)).contains("seal-application");
            completeSealApplicationSubflow(caseId);
        }
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_UPLOAD");
        return caseId;
    }

    private Long createCaseAtArchivistConfirm(String title) {
        Long caseId = createCaseAtProjectReview(title);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("projectReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_CONFIRM");
        return caseId;
    }

    private Long createCaseAtProjectReview(String title) {
        Long caseId = startIssueDraftOpinionWorkflow(title);
        completeTask(caseId, "ASSISTANT_SUPPLEMENT", ActionCode.APPROVE, issueDraftFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Map<String, Object> issueDraftFormData() {
        return Map.ofEntries(
                Map.entry("caseNo", "JA-ISSUE-DRAFT-9-10"),
                Map.entry("projectLeaderId", 3L),
                Map.entry("archivistId", 4L),
                Map.entry("explainLetterDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", false),
                Map.entry("draftOpinionUploaded", true),
                Map.entry("sealedDraftOpinionUploaded", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true),
                Map.entry("feedbackReceived", true),
                Map.entry("feedbackHasObjection", false),
                Map.entry("feedbackDecision", "无异议或未反馈")
        );
    }

    private Long startIssueDraftOpinionWorkflow(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "出具征求意见稿", "测试法院", 1L));
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());
        caseInfoMapper.updateById(caseInfo);

        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, "issue-draft-opinion")
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
        wfInstance.setCurrentNodeCode("ASSISTANT_SUPPLEMENT");
        wfInstance.setCurrentNodeName("项目辅助人编制并上传鉴定说明函");
        caseWfInstanceMapper.insert(wfInstance);

        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseInfo.getId());
        nodeInstance.setWfInstanceId(wfInstance.getId());
        nodeInstance.setNodeCode("ASSISTANT_SUPPLEMENT");
        nodeInstance.setNodeName("项目辅助人编制并上传鉴定说明函");
        nodeInstance.setStatus("running");
        caseNodeInstanceMapper.insert(nodeInstance);

        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstance.getId());
        task.setNodeInstanceId(nodeInstance.getId());
        task.setTaskType("candidate");
        task.setTaskTitle(title + " - 项目辅助人编制并上传鉴定说明函");
        task.setNodeCode("ASSISTANT_SUPPLEMENT");
        task.setNodeName("项目辅助人编制并上传鉴定说明函");
        task.setStatus("pending");
        task.setAssigneeId(OPERATOR_ID);
        task.setAssigneeName(OPERATOR_NAME);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        return caseInfo.getId();
    }

    private void completeSealApplicationSubflow(Long caseId) {
        completeTask(caseId, "APPLICANT_SUBMIT", ActionCode.APPROVE, Map.of(
                "caseNo", "JA-SEAL-9-10",
                "applicantId", 3L,
                "archivistId", 4L,
                "sealOperatorId", 6L,
                "applicationReason", "征求意见稿和鉴定说明函用章",
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
                task.getId(), actionCode, opinion == null ? "9.10 自动分支验证" : opinion,
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
