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
class FinalOpinionReviewBranchVerificationTest {

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
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void qualityControlDecision_shouldLaunchFinalOpinionReviewToProjectLeaderFirst() {
        Long caseId = createCaseAtFinalOpinionReviewFromQuality("9.8-从质控进入鉴定意见书送审稿编制");

        assertThat(activeTaskNodeCodes(caseId)).contains("FINAL_OPINION_REVIEW", "PROJECT_ASSIGN");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("final-opinion-review");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("nextRecommendation", "鉴定意见书送审稿编制");
    }

    @Test
    void fieldSurveyDecision_shouldLaunchFinalOpinionReviewSubflow() {
        Long caseId = createCaseAtFieldSurveyNextDecision("9.8-从现场勘验进入鉴定意见书送审稿编制");

        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "鉴定意见书送审稿编制"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("FINAL_OPINION_REVIEW", "PROJECT_ASSIGN");
        assertThat(runningSubflowCodes(caseId)).contains("final-opinion-review");
    }

    @Test
    void materialReceiveReturnDecision_shouldLaunchFinalOpinionReviewSubflow() {
        Long caseId = createCaseAtMaterialProjectDecision("9.8-从材料接收与返还进入鉴定意见书送审稿编制");

        completeTask(caseId, "PROJECT_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "鉴定意见书送审稿编制"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("FINAL_OPINION_REVIEW", "PROJECT_ASSIGN");
        assertThat(runningSubflowCodes(caseId)).contains("final-opinion-review");
    }

    @Test
    void happyPath_shouldKeepThreeLevelSerialReviewAndLaunchIssueFinalOpinion() {
        Long caseId = createCaseAtFinalOpinionReviewFromQuality("9.8-三级审核通过进入出具鉴定意见书");

        completeTask(caseId, "PROJECT_ASSIGN", ActionCode.APPROVE, Map.of(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");

        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE,
                finalOpinionFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("TECHNICAL_REVIEW", "DEPARTMENT_REVIEW");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE,
                Map.of("projectReviewPassed", true, "versionAUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("TECHNICAL_REVIEW");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("DEPARTMENT_REVIEW");

        completeTask(caseId, "TECHNICAL_REVIEW", ActionCode.APPROVE,
                Map.of("technicalReviewPassed", true, "versionABUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");

        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.APPROVE,
                Map.of("departmentReviewPassed", true, "versionABCUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_FINAL_UPLOAD");

        completeTask(caseId, "PROJECT_FINAL_UPLOAD", ActionCode.APPROVE, Map.of(
                "finalDraftUploaded", true,
                "nextRecommendation", "出具鉴定意见书"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ISSUE_OPINION", "PROJECT_MODIFY");
        assertThat(runningSubflowCodes(caseId)).contains("issue-opinion");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("opinionDraftUploaded", true)
                .containsEntry("versionAUploaded", true)
                .containsEntry("versionABUploaded", true)
                .containsEntry("versionABCUploaded", true)
                .containsEntry("finalDraftUploaded", true);
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantDraft() {
        Long caseId = createCaseAtDraftProjectReview("9.8-项目负责人审核退回");

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN,
                Map.of("projectReviewPassed", false), "初稿依据不足");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("TECHNICAL_REVIEW");
    }

    @Test
    void technicalReviewReturn_shouldReturnToProjectReview() {
        Long caseId = createCaseAtDraftTechnicalReview("9.8-技术负责人审核退回");

        completeTask(caseId, "TECHNICAL_REVIEW", ActionCode.RETURN,
                Map.of("technicalReviewPassed", false), "技术论证需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("DEPARTMENT_REVIEW");
    }

    @Test
    void departmentReviewReturn_shouldReturnToTechnicalReview() {
        Long caseId = createCaseAtDraftDepartmentReview("9.8-部门负责人审核退回");

        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.RETURN,
                Map.of("departmentReviewPassed", false), "部门意见需复核");

        assertThat(activeTaskNodeCodes(caseId)).contains("TECHNICAL_REVIEW");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PROJECT_FINAL_UPLOAD");
    }

    @Test
    void projectFinalUploadReturn_shouldReturnToDepartmentReview() {
        Long caseId = createCaseAtDraftFinalUpload("9.8-项目负责人定稿退回");

        completeTask(caseId, "PROJECT_FINAL_UPLOAD", ActionCode.RETURN,
                Map.of("finalDraftUploaded", false), "定稿版本需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ISSUE_OPINION");
    }

    private Long createCaseAtDraftFinalUpload(String title) {
        Long caseId = createCaseAtDraftDepartmentReview(title);
        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.APPROVE,
                Map.of("departmentReviewPassed", true, "versionABCUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_FINAL_UPLOAD");
        return caseId;
    }

    private Long createCaseAtDraftDepartmentReview(String title) {
        Long caseId = createCaseAtDraftTechnicalReview(title);
        completeTask(caseId, "TECHNICAL_REVIEW", ActionCode.APPROVE,
                Map.of("technicalReviewPassed", true, "versionABUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");
        return caseId;
    }

    private Long createCaseAtDraftTechnicalReview(String title) {
        Long caseId = createCaseAtDraftProjectReview(title);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE,
                Map.of("projectReviewPassed", true, "versionAUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("TECHNICAL_REVIEW");
        return caseId;
    }

    private Long createCaseAtDraftProjectReview(String title) {
        Long caseId = createCaseAtFinalOpinionReviewFromQuality(title);
        completeTask(caseId, "PROJECT_ASSIGN", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE,
                finalOpinionFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createCaseAtFinalOpinionReviewFromQuality(String title) {
        Long caseId = createCaseAtQualityNextFlowDecision(title);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "鉴定意见书送审稿编制"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_ASSIGN");
        assertThat(runningSubflowCodes(caseId)).contains("final-opinion-review");
        return caseId;
    }

    private Long createCaseAtMaterialProjectDecision(String title) {
        Long caseId = createCaseAtQualityNextFlowDecision(title);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "材料接收与返还"), null);
        completeTask(caseId, "PROJECT_CONFIRM", ActionCode.APPROVE,
                directMaterialFormData(), null, OPERATOR_ID, OPERATOR_NAME);
        completeTask(caseId, "MATERIAL_UPLOAD", ActionCode.APPROVE, Map.of("materialsUploaded", true), null);
        completeTask(caseId, "PROJECT_MATERIAL_CONFIRM", ActionCode.APPROVE,
                Map.of("projectMaterialConfirmed", true), null);
        completeTask(caseId, "ASSISTANT_REGISTER", ActionCode.APPROVE, Map.of(
                "materialMediaType", "纸质原件",
                "storageLocation", "档案室1号柜",
                "requireReturn", false
        ), null);
        completeTask(caseId, "ARCHIVIST_HANDLE", ActionCode.APPROVE, Map.of("storageStatus", "正常"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_DECISION");
        return caseId;
    }

    private Long createCaseAtFieldSurveyNextDecision(String title) {
        Long caseId = createCaseAtQualityNextFlowDecision(title);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE,
                Map.of("nextRecommendation", "现场勘验"), null);
        completeTask(caseId, "ASSISTANT_SURVEY", ActionCode.APPROVE, fieldSurveyFormData(), null);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "projectReviewPassed", true,
                "projectReviewRoute", "确认后续流程"
        ), null);
        completeTask(caseId, "PROJECT_TO_EQUIPMENT", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "ASSISTANT_EQUIPMENT", ActionCode.APPROVE, Map.of(
                "equipmentOutboundRecorded", true,
                "equipmentUsageRecorded", true,
                "equipmentReturnRecorded", true
        ), null);
        completeTask(caseId, "PROJECT_MATERIAL_REVIEW", ActionCode.APPROVE,
                Map.of("projectMaterialReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");
        return caseId;
    }

    private Long createCaseAtQualityNextFlowDecision(String title) {
        Long caseId = createCaseAtQualityProjectReview(title);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "fClassProject", false,
                "projectReviewPassed", true,
                "projectReviewRoute", "进入用章"
        ), null);
        completeSealApplicationSubflow(caseId);
        completeTask(caseId, "SEALED_FILE_UPLOAD", ActionCode.APPROVE,
                Map.of("sealedQualityFileUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");
        return caseId;
    }

    private Long createCaseAtQualityProjectReview(String title) {
        Long caseId = createSubmittedReceivedEntrustCase(title);
        completeTask(caseId, "INIT_FILL", ActionCode.APPROVE, receivedEntrustFormData(), null);
        completeTask(caseId, "CLERK_REGISTER", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "DEPT_REVIEW", ActionCode.APPROVE, Map.of("entrustAccepted", true), null);
        completeTask(caseId, "PROJECT_DECISION", ActionCode.APPROVE, Map.of(
                "preliminarySurveyRequired", false,
                "materialReceiveRequired", false
        ), null);
        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE, paymentNoticeFormData(), null);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of("sealRequired", false), null);
        completeTask(caseId, "ARCHIVE_UPLOAD", ActionCode.APPROVE,
                Map.of("sealedDocumentUploaded", true), null);
        completeTask(caseId, "PAYMENT_CONFIRM", ActionCode.APPROVE, Map.of(
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ), null);
        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE, qualityControlFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private void completeSealApplicationSubflow(Long caseId) {
        completeTask(caseId, "APPLICANT_SUBMIT", ActionCode.APPROVE, Map.of(
                "caseNo", "JA-BRANCH-9-8",
                "applicantId", OPERATOR_ID,
                "archivistId", OPERATOR_ID,
                "sealOperatorId", OPERATOR_ID,
                "applicationReason", "鉴定意见书送审稿编制验证前质量控制文件用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true,
                "archivistReviewed", false,
                "sealCompleted", false,
                "sealedScanUploaded", false
        ), null);
        completeTask(caseId, "ARCHIVIST_REVIEW", ActionCode.APPROVE,
                Map.of("archivistReviewed", true), null);
        completeTask(caseId, "SEAL_OPERATOR", ActionCode.APPROVE,
                Map.of("sealCompleted", true, "sealedScanUploaded", true), null);
        completeTask(caseId, "ARCHIVIST_UPLOAD", ActionCode.APPROVE,
                Map.of("sealedScanUploaded", true), null);
    }

    private Long createSubmittedReceivedEntrustCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 9.8 分支验证"), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private Map<String, Object> receivedEntrustFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("clientName", "9.8 分支验证委托人");
        data.put("serialNo", "BRANCH-9-8");
        data.put("initiatorName", "测试发起人");
        data.put("initiatedDate", "2026-06-13");
        data.put("receivedDate", "2026-06-13");
        data.put("caseNo", "JA-BRANCH-9-8");
        data.put("appraisalCategory", "工程造价");
        data.put("urgencyLevel", "普通");
        data.put("caseChannel", "线下");
        data.put("appraisalMatter", "9.8 鉴定意见书送审稿编制分支验证");
        data.put("entrustAccepted", true);
        data.put("filingDate", "2026-06-12");
        data.put("undertakingLegalPerson", "测试承办人");
        data.put("institutionSelectionMethod", "随机抽取");
        data.put("institutionSelectionTime", "2026-06-12");
        data.put("applicantName", "测试原告");
        data.put("respondentName", "测试被告");
        data.put("receivedDate", "2026-06-12");
        data.put("clientName", "测试委托人");
        data.put("caseNo", "JA-TEST-" + java.util.UUID.randomUUID().toString().substring(0,8));
        data.put("appraisalCategory", "法医临床鉴定");
        data.put("urgencyLevel", "普通");
        data.put("caseChannel", "线下");
        data.put("appraisalMatter", "测试鉴定事项");

        data.put("preliminarySurveyRequired", false);
        data.put("materialReceiveRequired", false);
        data.put("departmentHeadId", 2L);
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("technicalLeaderId", 5L);
        return data;
    }

    private Map<String, Object> paymentNoticeFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-8");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("letterDraftCompleted", true);
        data.put("letterType", "交费通知书");
        data.put("letterSummary", "已编制交费通知书。");
        data.put("sealRequired", false);
        data.put("sealedDocumentUploaded", true);
        data.put("sendDate", "2026-06-13");
        data.put("paymentReceived", true);
        data.put("paymentConfirmedDate", "2026-06-13");
        data.put("nextRecommendation", "编制内部质量控制文件");
        return data;
    }

    private Map<String, Object> qualityControlFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-8");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("qualityFileDraftCompleted", true);
        data.put("qualityFileSummary", "鉴定意见书送审稿编制前质量控制文件摘要。");
        data.put("formatType", "中心格式");
        data.put("contractAmount", 100000);
        data.put("fClassProject", false);
        data.put("projectReviewPassed", true);
        data.put("projectReviewRoute", "进入用章");
        data.put("sealRequired", true);
        data.put("sealedQualityFileUploaded", true);
        data.put("nextRecommendation", "鉴定意见书送审稿编制");
        return data;
    }

    private Map<String, Object> fieldSurveyFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-8");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("technicalLeaderId", 5L);
        data.put("departmentHeadId", 2L);
        data.put("surveyDate", "2026-06-13");
        data.put("surveyLocation", "测试项目现场");
        data.put("surveyPlanUploaded", true);
        data.put("fieldRecordUploaded", true);
        data.put("equipmentOutboundRecorded", true);
        data.put("equipmentUsageRecorded", true);
        data.put("equipmentReturnRecorded", true);
        data.put("projectAmount", 120000);
        data.put("majorAmountProject", false);
        data.put("projectReviewPassed", true);
        data.put("projectReviewRoute", "确认后续流程");
        data.put("technicalReviewPassed", true);
        data.put("departmentReviewPassed", true);
        data.put("projectMaterialReviewPassed", true);
        data.put("nextRecommendation", "鉴定意见书送审稿编制");
        return data;
    }

    private Map<String, Object> directMaterialFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-8");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("archivistId", OPERATOR_ID);
        data.put("materialReceiveType", "委托方直接提供");
        data.put("materialUploaderId", OPERATOR_ID);
        data.put("materialSource", "委托方提交");
        data.put("requireSupplementaryMaterial", false);
        data.put("supplementaryNoticeUploaded", false);
        data.put("materialsUploaded", true);
        data.put("projectMaterialConfirmed", true);
        data.put("materialDetails", "施工合同1份、工程量清单1份、电子数据光盘1张");
        data.put("receiveDate", "2026-06-13");
        data.put("materialMediaType", "纸质及电子介质");
        data.put("storageLocation", "档案室1号柜");
        data.put("requireReturn", false);
        data.put("storageStatus", "正常");
        data.put("nextRecommendation", "鉴定意见书送审稿编制");
        return data;
    }

    private Map<String, Object> finalOpinionFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-8");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("technicalLeaderId", 5L);
        data.put("departmentHeadId", 2L);
        data.put("opinionDraftUploaded", true);
        data.put("projectReviewPassed", true);
        data.put("versionAUploaded", true);
        data.put("technicalReviewPassed", true);
        data.put("versionABUploaded", true);
        data.put("departmentReviewPassed", true);
        data.put("versionABCUploaded", true);
        data.put("finalDraftUploaded", true);
        data.put("nextRecommendation", "出具鉴定意见书");
        return data;
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        completeTask(caseId, nodeCode, actionCode, formData, reason, null, null);
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData,
                              String reason, Long nextAssigneeId, String nextAssigneeName) {
        CaseTask task = activeTask(caseId, nodeCode);
        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "9.8 分支验证", reason, null, null,
                nextAssigneeId, nextAssigneeName, new java.util.HashMap<>(formData), null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
    }

    private CaseTask activeTask(Long caseId, String nodeCode) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode).isNotNull();
        return task;
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

    private synchronized void ensureOperatorHasAllJudicialRoles() {
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员"));
        for (String roleName : roles) {
            SysRole role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, roleName));
            if (role == null) {
                try {
                    role = new SysRole();
                    role.setRoleName(roleName);
                    role.setRoleCode(roleName);
                    role.setStatus("enabled");
                    role.setDataScope("all");
                    role.setDeleted(0);
                    sysRoleMapper.insert(role);
                } catch (Exception e) {
                    role = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                            .eq(SysRole::getRoleCode, roleName));
                }
            }
            if (role != null) {
                SysUserRole userRole = sysUserRoleMapper.selectOne(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, OPERATOR_ID)
                        .eq(SysUserRole::getRoleId, role.getId()));
                if (userRole == null) {
                    try {
                        userRole = new SysUserRole();
                        userRole.setUserId(OPERATOR_ID);
                        userRole.setRoleId(role.getId());
                        sysUserRoleMapper.insert(userRole);
                    } catch (Exception e) {
                        // ignore duplicate role bindings from parallel test setup
                    }
                }
            }
        }
    }
}
