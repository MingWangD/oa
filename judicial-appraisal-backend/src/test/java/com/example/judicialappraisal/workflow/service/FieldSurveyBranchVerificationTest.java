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
class FieldSurveyBranchVerificationTest {

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
    void qualityControlDecision_shouldLaunchFieldSurveySubflowToAssistant() {
        Long caseId = createCaseAtFieldSurveyAssistant("9.5-从质控进入现场勘验");

        assertThat(activeTaskNodeCodes(caseId)).contains("FIELD_SURVEY", "ASSISTANT_SURVEY");
        assertThat(runningSubflowCodes(caseId)).contains("field-survey");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("nextRecommendation", "现场勘验")
                .containsEntry("sealedQualityFileUploaded", true);
    }

    @Test
    void lowAmountProject_shouldSkipTechnicalAndDepartmentReviewAndGoToEquipmentRecords() {
        Long caseId = createCaseAtFieldSurveyProjectReview("9.5-15万以下分支", 120000, false);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "projectReviewPassed", true,
                "projectReviewRoute", "确认后续流程"
        ), null);

        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_TO_EQUIPMENT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("TECHNICAL_REVIEW", "DEPARTMENT_REVIEW");
        assertThat(caseInfoMapper.selectById(caseId).getFormData())
                .containsEntry("projectAmount", 120000)
                .containsEntry("majorAmountProject", false);
    }

    @Test
    void highAmountProject_shouldGoThroughTechnicalAndDepartmentReview() {
        Long caseId = createCaseAtFieldSurveyProjectReview("9.5-15万以上逐级审核分支", 260000, true);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "projectReviewPassed", true,
                "projectReviewRoute", "技术负责人审核"
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("TECHNICAL_REVIEW");

        completeTask(caseId, "TECHNICAL_REVIEW", ActionCode.APPROVE, Map.of("technicalReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");

        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.APPROVE, Map.of("departmentReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_TO_EQUIPMENT");
    }

    @Test
    void projectReviewReturn_shouldReturnToAssistantSurvey() {
        Long caseId = createCaseAtFieldSurveyProjectReview("9.5-项目负责人退回分支", 120000, false);

        completeTask(caseId, "PROJECT_REVIEW", ActionCode.RETURN, Map.of(
                "projectReviewPassed", false,
                "projectReviewRoute", "退回修改"
        ), "现场工作方案和设备记录需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SURVEY");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("NEXT_FLOW_DECISION", "TECHNICAL_REVIEW");
    }

    @Test
    void technicalReviewReturn_shouldReturnToAssistantSurvey() {
        Long caseId = createCaseAtFieldSurveyTechnicalReview("9.5-技术负责人退回分支");

        completeTask(caseId, "TECHNICAL_REVIEW", ActionCode.RETURN, Map.of("technicalReviewPassed", false), "技术方案需复核");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SURVEY");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("DEPARTMENT_REVIEW", "NEXT_FLOW_DECISION");
    }

    @Test
    void departmentReviewReturn_shouldReturnToAssistantSurvey() {
        Long caseId = createCaseAtFieldSurveyDepartmentReview("9.5-部门负责人退回分支");

        completeTask(caseId, "DEPARTMENT_REVIEW", ActionCode.RETURN, Map.of("departmentReviewPassed", false), "部门意见需技术负责人补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SURVEY");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("NEXT_FLOW_DECISION");
    }

    @Test
    void equipmentAndMaterialReview_shouldRequireAssistantRecordsThenProjectReview() {
        Long caseId = createCaseAtFieldSurveyEquipmentTransfer("9.5-设备记录与材料审核路径");

        completeTask(caseId, "PROJECT_TO_EQUIPMENT", ActionCode.APPROVE, Map.of(), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_EQUIPMENT");

        completeTask(caseId, "ASSISTANT_EQUIPMENT", ActionCode.APPROVE, Map.of(
                "equipmentOutboundRecorded", true,
                "equipmentUsageRecorded", true,
                "equipmentReturnRecorded", true
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_MATERIAL_REVIEW");

        completeTask(caseId, "PROJECT_MATERIAL_REVIEW", ActionCode.APPROVE, Map.of("projectMaterialReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");
    }

    @Test
    void materialReviewReturn_shouldReturnToAssistantEquipment() {
        Long caseId = createCaseAtFieldSurveyMaterialReview("9.5-材料审核退回分支");

        completeTask(caseId, "PROJECT_MATERIAL_REVIEW", ActionCode.RETURN, Map.of("projectMaterialReviewPassed", false), "设备出入库记录需补充");

        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_EQUIPMENT");
        assertThat(activeTaskNodeCodes(caseId)).doesNotContain("NEXT_FLOW_DECISION");
    }

    @ParameterizedTest
    @CsvSource({
            "材料接收与返还, MATERIAL_RECEIVE_RETURN, material-receive-return",
            "鉴定意见书征求意见稿送审稿编制, DRAFT_OPINION_REVIEW, draft-opinion-review",
            "鉴定意见书送审稿编制, FINAL_OPINION_REVIEW, final-opinion-review",
            "退费, REFUND, refund",
            "终止鉴定, TERMINATE_APPRAISAL, terminate-appraisal"
    })
    void nextRecommendation_shouldTriggerCorrectSubflow(String recommendation, String expectedNode, String expectedSubflow) {
        Long caseId = createCaseAtFieldSurveyNextDecision("9.5-后续流向验证-" + recommendation);

        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", recommendation), null);

        assertThat(activeTaskNodeCodes(caseId)).contains(expectedNode);
        assertThat(runningSubflowCodes(caseId)).contains(expectedSubflow);
    }

    private Long createCaseAtFieldSurveyNextDecision(String title) {
        Long caseId = createCaseAtFieldSurveyMaterialReview(title);
        completeTask(caseId, "PROJECT_MATERIAL_REVIEW", ActionCode.APPROVE, Map.of("projectMaterialReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("NEXT_FLOW_DECISION");
        return caseId;
    }

    private Long createCaseAtFieldSurveyMaterialReview(String title) {
        Long caseId = createCaseAtFieldSurveyEquipmentTransfer(title);
        completeTask(caseId, "PROJECT_TO_EQUIPMENT", ActionCode.APPROVE, Map.of(), null);
        completeTask(caseId, "ASSISTANT_EQUIPMENT", ActionCode.APPROVE, Map.of(
                "equipmentOutboundRecorded", true,
                "equipmentUsageRecorded", true,
                "equipmentReturnRecorded", true
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_MATERIAL_REVIEW");
        return caseId;
    }

    private Long createCaseAtFieldSurveyEquipmentTransfer(String title) {
        Long caseId = createCaseAtFieldSurveyProjectReview(title, 120000, false);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "projectReviewPassed", true,
                "projectReviewRoute", "确认后续流程"
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_TO_EQUIPMENT");
        return caseId;
    }

    private Long createCaseAtFieldSurveyDepartmentReview(String title) {
        Long caseId = createCaseAtFieldSurveyTechnicalReview(title);
        completeTask(caseId, "TECHNICAL_REVIEW", ActionCode.APPROVE, Map.of("technicalReviewPassed", true), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("DEPARTMENT_REVIEW");
        return caseId;
    }

    private Long createCaseAtFieldSurveyTechnicalReview(String title) {
        Long caseId = createCaseAtFieldSurveyProjectReview(title, 260000, true);
        completeTask(caseId, "PROJECT_REVIEW", ActionCode.APPROVE, Map.of(
                "projectReviewPassed", true,
                "projectReviewRoute", "技术负责人审核"
        ), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("TECHNICAL_REVIEW");
        return caseId;
    }

    private Long createCaseAtFieldSurveyProjectReview(String title, int projectAmount, boolean majorAmountProject) {
        Long caseId = createCaseAtFieldSurveyAssistant(title);
        completeTask(caseId, "ASSISTANT_SURVEY", ActionCode.APPROVE, fieldSurveyFormData(projectAmount, majorAmountProject), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("PROJECT_REVIEW");
        return caseId;
    }

    private Long createCaseAtFieldSurveyAssistant(String title) {
        Long caseId = createCaseAtQualityNextFlowDecision(title);
        completeTask(caseId, "NEXT_FLOW_DECISION", ActionCode.APPROVE, Map.of("nextRecommendation", "现场勘验"), null);
        assertThat(activeTaskNodeCodes(caseId)).contains("ASSISTANT_SURVEY");
        assertThat(runningSubflowCodes(caseId)).contains("field-survey");
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

    private void completeSealApplicationSubflow(Long caseId) {
        Map<String, Object> sealData = new java.util.HashMap<>();
        sealData.put("caseNo", "JA-BRANCH-9-5");
        sealData.put("applicantId", OPERATOR_ID);
        sealData.put("archivistId", OPERATOR_ID);
        sealData.put("sealOperatorId", OPERATOR_ID);
        sealData.put("applicationReason", "现场勘验前质量控制文件用章");
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
        caseInfoService.submitCase(caseInfo.getId(), new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起 9.5 分支验证"), OPERATOR_ID, OPERATOR_NAME);
        return caseInfo.getId();
    }

    private Map<String, Object> receivedEntrustFormData() {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("clientName", "9.5 分支验证委托人");
        data.put("serialNo", "BRANCH-9-5");
        data.put("initiatorName", "测试发起人");
        data.put("initiatedDate", "2026-06-13");
        data.put("receivedDate", "2026-06-13");
        data.put("caseNo", "JA-BRANCH-9-5");
        data.put("appraisalCategory", "工程造价");
        data.put("urgencyLevel", "普通");
        data.put("caseChannel", "线下");
        data.put("appraisalMatter", "9.5 现场勘验分支验证");
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
        data.put("caseNo", "JA-BRANCH-9-5");
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
        data.put("caseNo", "JA-BRANCH-9-5");
        data.put("projectLeaderId", 3L);
        data.put("projectAssistantId", 4L);
        data.put("qualityFileDraftCompleted", true);
        data.put("qualityFileSummary", "现场勘验前质量控制文件摘要。");
        data.put("formatType", "中心格式");
        data.put("contractAmount", 100000);
        data.put("fClassProject", false);
        data.put("projectReviewPassed", true);
        data.put("projectReviewRoute", "进入用章");
        data.put("sealRequired", true);
        data.put("sealedQualityFileUploaded", true);
        data.put("nextRecommendation", "现场勘验");
        return data;
    }

    private Map<String, Object> fieldSurveyFormData(int projectAmount, boolean majorAmountProject) {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("caseNo", "JA-BRANCH-9-5");
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
        data.put("projectAmount", projectAmount);
        data.put("majorAmountProject", majorAmountProject);
        data.put("projectReviewPassed", true);
        data.put("projectReviewRoute", majorAmountProject ? "技术负责人审核" : "确认后续流程");
        data.put("technicalReviewPassed", true);
        data.put("departmentReviewPassed", true);
        data.put("projectMaterialReviewPassed", true);
        data.put("nextRecommendation", "鉴定意见书送审稿编制");
        return data;
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("Task not found or not active: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "9.5 分支验证", reason, null, null, new java.util.HashMap<>(formData), null);
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
