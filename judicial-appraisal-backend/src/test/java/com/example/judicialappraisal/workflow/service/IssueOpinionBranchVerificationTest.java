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
class IssueOpinionBranchVerificationTest {

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
    void issueOpinion_shouldStartAtProjectLeaderModifyNode() {
        Long caseId = startIssueOpinionWorkflow("9.9-出具鉴定意见书首节点");

        assertThat(activeTaskNodeCodes(caseId)).containsExactly("PROJECT_MODIFY");
        assertThat(activeTaskNames(caseId)).containsExactly("项目负责人修改鉴定意见书");
    }

    @Test
    void happyPath_shouldUploadCommitmentReviewOpinionRunSealAndInvoiceThenLaunchArchive() {
        Long caseId = startIssueOpinionWorkflow("9.9-盖章开票后归档");

        completeTask(caseId, "PROJECT_MODIFY", ActionCode.APPROVE, baseIssueOpinionFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_UPLOAD");

        completeTask(caseId, "ASSISTANT_UPLOAD", ActionCode.APPROVE,
                Map.of("commitmentDrafted", true, "reviewOpinionDrafted", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("projectReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_CONFIRM");

        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.APPROVE,
                Map.of("sealRequired", true, "invoiceRequired", true, "systemRegistrationUploaded", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION", "FINANCE_INVOICE");
        assertThat(runningSubflowCodes(caseId)).contains("seal-application");

        completeTask(caseId, "FINANCE_INVOICE", ActionCode.APPROVE, Map.of("invoiceIssued", true), null);
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("DELIVERY_ARCHIVE");

        completeSealApplicationSubflow(caseId);
        completeTask(caseId, "SEALED_UPLOAD", ActionCode.APPROVE, Map.of("sealedOpinionUploaded", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("DELIVERY_ARCHIVE");
        completeTask(caseId, "DELIVERY_ARCHIVE", ActionCode.APPROVE,
                Map.of("deliveryMethod", "电子送达", "archiveConfirmed", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE_SUBFLOW", "ARCHIVIST_PREPARE");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("opinionModified", true)
                .containsEntry("commitmentDrafted", true)
                .containsEntry("reviewOpinionDrafted", true)
                .containsEntry("systemRegistrationUploaded", true)
                .containsEntry("invoiceIssued", true)
                .containsEntry("sealedOpinionUploaded", true)
                .containsEntry("archiveConfirmed", true);
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantUpload() {
        Long caseId = createCaseAtProjectReview("9.9-项目负责人审核退回");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN,
                Map.of("projectReviewPassed", false), "承诺书或复核意见需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_UPLOAD");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ARCHIVIST_CONFIRM");
    }

    @Test
    void invoiceNotRequired_shouldWaitOnlyForSealBranchBeforeDelivery() {
        Long caseId = createCaseAtArchivistConfirm("9.9-免开票等待用章");

        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.APPROVE,
                Map.of("sealRequired", true, "invoiceRequired", false, "systemRegistrationUploaded", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("SEAL_APPLICATION");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("FINANCE_INVOICE", "DELIVERY_ARCHIVE");

        completeSealApplicationSubflow(caseId);
        completeTask(caseId, "SEALED_UPLOAD", ActionCode.APPROVE, Map.of("sealedOpinionUploaded", true), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("DELIVERY_ARCHIVE");
    }

    @Test
    void sealedUploadReturn_shouldReturnToAssistantUploadWithoutLaunchingArchive() {
        Long caseId = createCaseAtSealedUpload("9.9-盖章件上传退回");

        completeTask(caseId, "SEALED_UPLOAD", ActionCode.RETURN,
                Map.of("sealedOpinionUploaded", false), "盖章扫描件或前置材料需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_UPLOAD");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("DELIVERY_ARCHIVE", "ARCHIVE_SUBFLOW");
    }

    private Long createCaseAtSealedUpload(String title) {
        Long caseId = createCaseAtArchivistConfirm(title);
        completeTask(caseId, "ARCHIVIST_CONFIRM", ActionCode.APPROVE,
                Map.of("sealRequired", false, "invoiceRequired", false, "systemRegistrationUploaded", true), null);
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
        Long caseId = startIssueOpinionWorkflow(title);
        completeTask(caseId, "PROJECT_MODIFY", ActionCode.APPROVE, baseIssueOpinionFormData(), null);
        completeTask(caseId, "ASSISTANT_UPLOAD", ActionCode.APPROVE,
                Map.of("commitmentDrafted", true, "reviewOpinionDrafted", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Map<String, Object> baseIssueOpinionFormData() {
        return Map.ofEntries(
                Map.entry("caseNo", "JA-ISSUE-9-9"),
                Map.entry("projectLeaderId", 3L),
                Map.entry("archivistId", 4L),
                Map.entry("financeId", 5L),
                Map.entry("opinionModified", true),
                Map.entry("commitmentDrafted", true),
                Map.entry("reviewOpinionDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", true),
                Map.entry("systemRegistrationUploaded", true),
                Map.entry("sealedOpinionUploaded", true),
                Map.entry("invoiceRequired", true),
                Map.entry("invoiceIssued", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true)
        );
    }

    private Long startIssueOpinionWorkflow(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "出具鉴定意见书", "测试法院", 1L));
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());
        caseInfoMapper.updateById(caseInfo);

        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, "issue-opinion")
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
        wfInstance.setCurrentNodeCode("PROJECT_MODIFY");
        wfInstance.setCurrentNodeName("项目负责人修改鉴定意见书");
        caseWfInstanceMapper.insert(wfInstance);

        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseInfo.getId());
        nodeInstance.setWfInstanceId(wfInstance.getId());
        nodeInstance.setNodeCode("PROJECT_MODIFY");
        nodeInstance.setNodeName("项目负责人修改鉴定意见书");
        nodeInstance.setStatus("running");
        caseNodeInstanceMapper.insert(nodeInstance);

        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstance.getId());
        task.setNodeInstanceId(nodeInstance.getId());
        task.setTaskType("candidate");
        task.setTaskTitle(title + " - 项目负责人修改鉴定意见书");
        task.setNodeCode("PROJECT_MODIFY");
        task.setNodeName("项目负责人修改鉴定意见书");
        task.setStatus("pending");
        task.setAssigneeId(OPERATOR_ID);
        task.setAssigneeName(OPERATOR_NAME);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        return caseInfo.getId();
    }

    private void completeSealApplicationSubflow(Long caseId) {
        completeTask(caseId, "APPLICANT_SUBMIT", ActionCode.APPROVE, Map.of(
                "caseNo", "JA-SEAL-9-9",
                "applicantId", 3L,
                "archivistId", 4L,
                "sealOperatorId", 6L,
                "applicationReason", "鉴定意见书用章",
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
                task.getId(), actionCode, opinion == null ? "9.9 自动分支验证" : opinion,
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
