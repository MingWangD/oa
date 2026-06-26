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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MaterialReceiveReturnBranchVerificationTest {

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
    void qualityControlDecision_shouldLaunchMaterialReceiveReturnSubflowToProjectLeader() {
        Long caseId = createCaseAtMaterialProjectConfirmFromQuality("9.6-从质控进入材料接收与返还");

        assertThat(activeTaskNodeCodes(caseId)).contains("MATERIAL_RECEIVE_RETURN", "PROJECT_CONFIRM");
        assertThat(runningSubflowCodes(caseId)).contains("material-receive-return");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("nextRecommendation", "材料接收与返还");
    }

    @Test
    void fieldSurveyDecision_shouldLaunchMaterialReceiveReturnSubflowToProjectLeader() {
        Long caseId = createCaseAtFieldSurveyNextDecision("9.6-从现场勘验进入材料接收与返还");

        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", "材料接收与返还"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("MATERIAL_RECEIVE_RETURN", "PROJECT_CONFIRM");
        assertThat(runningSubflowCodes(caseId)).contains("material-receive-return");
    }

    @Test
    void directProvidedMaterials_shouldAssignUploaderThenRequireProjectConfirmationAndAllowReturn() {
        Long caseId = createCaseAtMaterialProjectConfirmFromQuality("9.6-委托方直接提供材料并返还");

        completeTask(caseId, "PROJECT_CONFIRM", ActionCode.APPROVE, directMaterialFormData(true), null, OPERATOR_ID, OPERATOR_NAME);

        CaseTask uploadTask = activeTask(caseId, "MATERIAL_UPLOAD");
        assertThat(uploadTask.getAssigneeId()).isEqualTo(OPERATOR_ID);

        completeTask(caseId, "MATERIAL_UPLOAD", ActionCode.APPROVE, Map.of("materialsUploaded", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_MATERIAL_CONFIRM");

        completeTask(caseId, "PROJECT_MATERIAL_CONFIRM", ActionCode.APPROVE, Map.of("projectMaterialConfirmed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_REGISTER");

        completeTask(caseId, "ASSISTANT_REGISTER", ActionCode.APPROVE, Map.of(
                "materialMediaType", "纸质原件",
                "storageLocation", "返还暂存柜",
                "requireReturn", true
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_RETURN");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ARCHIVIST_HANDLE");

        completeTask(caseId, "ASSISTANT_RETURN", ActionCode.APPROVE, Map.of(
                "returnRegistrationCompleted", true,
                "returnReceiver", "委托方经办人",
                "returnDate", "2026-06-13"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE", "PROJECT_DECISION");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
    }

    @Test
    void directProvidedMaterialsWithoutReturn_shouldGoToArchivistStorageThenArchiveAndProjectDecision() {
        Long caseId = createCaseAtMaterialProjectConfirmFromQuality("9.6-委托方直接提供材料无需返还");

        completeDirectMaterialToAssistantRegister(caseId, false);
        completeTask(caseId, "ASSISTANT_REGISTER", ActionCode.APPROVE, Map.of(
                "materialMediaType", "电子数据",
                "storageLocation", "档案室1号柜",
                "requireReturn", false
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_HANDLE");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ASSISTANT_RETURN");

        completeTask(caseId, "ARCHIVIST_HANDLE", ActionCode.APPROVE, Map.of("storageStatus", "正常"), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE", "PROJECT_DECISION");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
    }

    @Test
    void supplementaryMaterial_shouldLaunchPaymentNoticeSubflow() {
        Long caseId = createCaseAtMaterialProjectConfirmFromQuality("9.6-补充材料进入发交费通知");

        completeTask(caseId, "PROJECT_CONFIRM", ActionCode.APPROVE, supplementaryMaterialFormData(), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("PAYMENT_NOTICE", "ASSISTANT_DRAFT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("MATERIAL_UPLOAD", "ASSISTANT_REGISTER");
        assertThat(runningSubflowCodes(caseId)).contains("payment-notice");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("requireSupplementaryMaterial", true)
                .containsEntry("supplementaryNoticeUploaded", true);
    }

    @Test
    void projectMaterialConfirmReturn_shouldReturnToMaterialUploader() {
        Long caseId = createCaseAtMaterialProjectConfirmFromQuality("9.6-项目负责人确认材料退回");

        completeTask(caseId, "PROJECT_CONFIRM", ActionCode.APPROVE, directMaterialFormData(false), null, OPERATOR_ID, OPERATOR_NAME);
        completeTask(caseId, "MATERIAL_UPLOAD", ActionCode.APPROVE, Map.of("materialsUploaded", true), null);

        completeTask(caseId, "PROJECT_MATERIAL_CONFIRM", ActionCode.RETURN, Map.of("projectMaterialConfirmed", false), "上传材料清单需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("MATERIAL_UPLOAD");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("ASSISTANT_REGISTER");
    }

    @Test
    void archivistReturn_shouldReturnToAssistantRegister() {
        Long caseId = createCaseAtMaterialArchivistHandle("9.6-档案管理员退回登记材料");

        completeTask(caseId, "ARCHIVIST_HANDLE", ActionCode.RETURN, Map.of("storageStatus", "损毁"), "存放地址和介质类别需复核");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_REGISTER");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("PROJECT_DECISION", "ARCHIVE");
    }

    @ParameterizedTest
    @CsvSource({
            "鉴定意见书征求意见稿送审稿编制, DRAFT_OPINION_REVIEW, draft-opinion-review",
            "鉴定意见书送审稿编制, FINAL_OPINION_REVIEW, final-opinion-review",
            "退费, REFUND, refund",
            "终止鉴定, TERMINATE_APPRAISAL, terminate-appraisal"
    })
    void projectDecision_shouldTriggerCorrectFollowUpSubflow(String recommendation, String expectedNode, String expectedSubflow) {
        Long caseId = createCaseAtMaterialProjectDecision("9.6-后续流向验证-" + recommendation);

        completeTask(caseId, "PROJECT_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", recommendation), null);

        assertThat(activeTaskNodeCodes(caseId)).contains(expectedNode);
        assertThat(runningSubflowCodes(caseId)).contains(expectedSubflow);
    }

    private Long createCaseAtMaterialProjectDecision(String title) {
        Long caseId = createCaseAtMaterialArchivistHandle(title);
        completeTask(caseId, "ARCHIVIST_HANDLE", ActionCode.APPROVE, Map.of("storageStatus", "正常"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVE", "PROJECT_DECISION");
        assertThat(runningSubflowCodes(caseId)).contains("archive");
        return caseId;
    }

    private Long createCaseAtMaterialArchivistHandle(String title) {
        Long caseId = createCaseAtMaterialProjectConfirmFromQuality(title);
        completeDirectMaterialToAssistantRegister(caseId, false);
        completeTask(caseId, "ASSISTANT_REGISTER", ActionCode.APPROVE, Map.of(
                "materialMediaType", "纸质复印件",
                "storageLocation", "档案室1号柜",
                "requireReturn", false
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ARCHIVIST_HANDLE");
        return caseId;
    }

    private void completeDirectMaterialToAssistantRegister(Long caseId, boolean requireReturn) {
        completeTask(caseId, "PROJECT_CONFIRM", ActionCode.APPROVE, directMaterialFormData(requireReturn), null, OPERATOR_ID, OPERATOR_NAME);
        completeTask(caseId, "MATERIAL_UPLOAD", ActionCode.APPROVE, Map.of("materialsUploaded", true), null);
        completeTask(caseId, "PROJECT_MATERIAL_CONFIRM", ActionCode.APPROVE, Map.of("projectMaterialConfirmed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_REGISTER");
    }

    private Long createCaseAtMaterialProjectConfirmFromQuality(String title) {
        Long caseId = createCaseAtQualityNextFlowDecision(title);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", "材料接收与返还"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_CONFIRM");
        assertThat(runningSubflowCodes(caseId)).contains("material-receive-return");
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
        assertThat(activeTaskNodeCodes(caseId)).contains("SEALED_FILE_UPLOAD");

        completeTask(caseId, "SEALED_FILE_UPLOAD", ActionCode.APPROVE, Map.of("sealedQualityFileUploaded", true), null);
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
        completeTask(caseId, "ARCHIVE_UPLOAD", ActionCode.APPROVE, Map.of("sealedDocumentUploaded", true), null);
        completeTask(caseId, "PAYMENT_CONFIRM", ActionCode.APPROVE, Map.of(
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_DRAFT");
        assertThat(runningSubflowCodes(caseId)).contains("quality-control");

        completeTask(caseId, "ASSISTANT_DRAFT", ActionCode.APPROVE, qualityControlFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createCaseAtFieldSurveyNextDecision(String title) {
        Long caseId = createCaseAtFieldSurveyMaterialReview(title);
        completeTask(caseId, "PROJECT_MATERIAL_REVIEW", ActionCode.APPROVE, Map.of("projectMaterialReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");
        return caseId;
    }

    private Long createCaseAtFieldSurveyMaterialReview(String title) {
        Long caseId = createCaseAtFieldSurveyProjectReview(title);
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
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_MATERIAL_REVIEW");
        return caseId;
    }

    private Long createCaseAtFieldSurveyProjectReview(String title) {
        Long caseId = createCaseAtQualityNextFlowDecision(title);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", "现场勘验"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SURVEY");
        completeTask(caseId, "ASSISTANT_SURVEY", ActionCode.APPROVE, fieldSurveyFormData(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private void completeSealApplicationSubflow(Long caseId) {
        Map<String, Object> sealData = new java.util.HashMap<>();
        sealData.put("caseNo", "JA-BRANCH-9-6");
        sealData.put("applicantId", OPERATOR_ID);
        sealData.put("archivistId", OPERATOR_ID);
        sealData.put("sealOperatorId", OPERATOR_ID);
        sealData.put("applicationReason", "材料接收与返还验证前质量控制文件用章");
        sealData.put("sealMode", "线下盖章");
        sealData.put("applicationFilesPrepared", true);
        sealData.put("archivistReviewed", false);
        sealData.put("sealCompleted", false);
        sealData.put("sealedScanUploaded", false);

        completeTask(caseId, "APPLICANT_SUBMIT", ActionCode.APPROVE, sealData, null);
        completeTask(caseId, "ARCHIVIST_REVIEW", ActionCode.APPROVE, Map.of("archivistReviewed", true), null);
        completeTask(caseId, "SEAL_OPERATOR", ActionCode.APPROVE, Map.of("sealCompleted", true, "sealedScanUploaded", true), null);
        completeTask(caseId, "ARCHIVIST_UPLOAD", ActionCode.APPROVE, Map.of("sealedScanUploaded", true), null);
    }

    private Long createSubmittedReceivedEntrustCase(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "司法鉴定", "测试法院", 1L));
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 9.6 分支验证"), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private Map<String, Object> receivedEntrustFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("clientName", "9.6 分支验证委托人");
        data.put("serialNo", "BRANCH-9-6");
        data.put("initiatorName", "测试发起人");
        data.put("initiatedDate", "2026-06-13");
        data.put("receivedDate", "2026-06-13");
        data.put("caseNo", "JA-BRANCH-9-6");
        data.put("appraisalCategory", "工程造价");
        data.put("urgencyLevel", "普通");
        data.put("caseChannel", "线下");
        data.put("appraisalMatter", "9.6 材料接收与返还分支验证");
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
        data.put("caseNo", "JA-BRANCH-9-6");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("letterDraftCompleted", true);
        data.put("letterType", "交费通知书");
        data.put("letterSummary", "已编制交费通知书。");
        data.put("projectAmount", 120000);
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
        data.put("caseNo", "JA-BRANCH-9-6");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("qualityFileDraftCompleted", true);
        data.put("qualityFileSummary", "材料接收前质量控制文件摘要。");
        data.put("formatType", "中心格式");
        data.put("contractAmount", 100000);
        data.put("fClassProject", false);
        data.put("projectReviewPassed", true);
        data.put("projectReviewRoute", "进入用章");
        data.put("sealRequired", true);
        data.put("sealedQualityFileUploaded", true);
        data.put("nextRecommendation", "材料接收与返还");
        return data;
    }

    private Map<String, Object> fieldSurveyFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-6");
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
        data.put("nextRecommendation", "材料接收与返还");
        return data;
    }

    private Map<String, Object> directMaterialFormData(boolean requireReturn) {
        Map<String, Object> data = baseMaterialFormData();
        data.put("materialReceiveType", "委托方直接提供");
        data.put("materialUploaderId", OPERATOR_ID);
        data.put("requireSupplementaryMaterial", false);
        data.put("supplementaryNoticeUploaded", false);
        data.put("materialsUploaded", true);
        data.put("projectMaterialConfirmed", true);
        data.put("requireReturn", requireReturn);
        data.put("returnRegistrationCompleted", requireReturn);
        if (requireReturn) {
            data.put("returnReceiver", "委托方经办人");
            data.put("returnDate", "2026-06-13");
        }
        data.put("nextRecommendation", "鉴定意见书送审稿编制");
        return data;
    }

    private Map<String, Object> supplementaryMaterialFormData() {
        Map<String, Object> data = baseMaterialFormData();
        data.put("materialReceiveType", "需要补充材料");
        data.put("requireSupplementaryMaterial", true);
        data.put("supplementaryNotice", "需补充造价明细、原始票据和施工记录。");
        data.put("supplementaryNoticeUploaded", true);
        data.put("materialsUploaded", false);
        data.put("projectMaterialConfirmed", false);
        data.put("requireReturn", false);
        data.put("nextRecommendation", "鉴定意见书送审稿编制");
        return data;
    }

    private Map<String, Object> baseMaterialFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-6");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("archivistId", OPERATOR_ID);
        data.put("materialSource", "委托方提交");
        data.put("materialDetails", "施工合同1份、工程量清单1份、电子数据光盘1张");
        data.put("receiveDate", "2026-06-13");
        data.put("materialMediaType", "纸质及电子介质");
        data.put("storageLocation", "档案室1号柜");
        data.put("storageStatus", "正常");
        return data;
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        completeTask(caseId, nodeCode, actionCode, formData, reason, null, null);
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData,
                              String reason, Long nextAssigneeId, String nextAssigneeName) {
        CaseTask task = activeTask(caseId, nodeCode);
        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "9.6 分支验证", reason, null, null,
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
