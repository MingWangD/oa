package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
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
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
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

@SpringBootTest
class ScenarioThreeVerificationTest {

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
    void runScenarioThree_E2E_Objection_NoRollback() {
        System.out.println(">>> 开始运行场景三大额勘验与异议流转自动化测试 <<<");

        // 1. 创建草稿 (20万金额)
        String title = "场景3：大额现场勘验与征求意见反馈异议路径";
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "工程造价", "深圳市盐田区人民法院", 1L));
        Long caseId = caseInfo.getId();
        caseInfo.setCaseNo(title);
        caseInfoMapper.updateById(caseInfo);
        System.out.println("1. 成功创建案件草稿, ID=" + caseId);

        // 2. 提交流转
        CaseSubmitRequest submitRequest = new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起流程");
        WorkflowActionResult submitResult = caseInfoService.submitCase(caseId, submitRequest, OPERATOR_ID, OPERATOR_NAME);
        assertThat(submitResult.success()).isTrue();
        System.out.println("2. 成功提交流转案件, 主流程已开启");

        // 3. 填写委托信息 (INIT_FILL)
        Map<String, Object> initFormData = Map.ofEntries(
                Map.entry("clientName", "深圳市盐田区人民法院"),
                Map.entry("serialNo", "SC-3-123456"),
                Map.entry("initiatorName", "管理员"),
                Map.entry("initiatedDate", "2026-06-23"),
                Map.entry("receivedDate", "2026-06-23"),
                Map.entry("caseNo", "场景3：大额现场勘验与征求意见反馈异议路径"),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("projectAmount", 200000), // 项目估算金额 20万
                Map.entry("appraisalMatter", "大额项目现场勘验与异议司法鉴定"),
                Map.entry("entrustAccepted", true),
                Map.entry("preliminarySurveyRequired", false),
                Map.entry("departmentHeadId", 2L),
                Map.entry("projectLeaderId", 3L),
                Map.entry("projectAssistantId", 4L),
                Map.entry("materialReceiveRequired", false),
                Map.entry("filingDate", "2026-06-25"),
                Map.entry("undertakingLegalPerson", "张三"),
                Map.entry("institutionSelectionMethod", "随机抽取"),
                Map.entry("institutionSelectionTime", "2026-06-25"),
                Map.entry("applicantName", "原告张三"),
                Map.entry("respondentName", "被告李四")
        );
        completeTask(caseId, "INIT_FILL", initFormData);
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", true));
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", false));
        completeTask(caseId, "ASSISTANT_NOTICE", Map.of());
        System.out.println("3. 主流程登记受理完成，流转至缴费流程");

        // 4. 发交费通知子流程
        Map<String, Object> paymentNoticeFormData = Map.ofEntries(
                Map.entry("letterDraftCompleted", true),
                Map.entry("letterType", "交费通知书"),
                Map.entry("letterSummary", "20万缴费通知"),
                Map.entry("sealRequired", false),
                Map.entry("sealedDocumentUploaded", true),
                Map.entry("paymentReceived", true),
                Map.entry("nextRecommendation", "编制内部质量控制文件")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", paymentNoticeFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(caseId, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", true));
        completeTask(caseId, "PAYMENT_CONFIRM", Map.of("paymentReceived", true, "nextRecommendation", "编制内部质量控制文件"));
        System.out.println("4. 缴费确认完成，流转至质控子流程");

        // 5. 内部质控文件编制子流程
        Map<String, Object> qcFormData = Map.ofEntries(
                Map.entry("qualityFileDraftCompleted", true),
                Map.entry("qualityFileSummary", "大额现场勘验前质控"),
                Map.entry("formatType", "非中心格式"),
                Map.entry("contractAmount", 200000), // 合同金额20万 (非中心格式，小于25万，非F类)
                Map.entry("fClassProject", false),
                Map.entry("projectReviewPassed", true),
                Map.entry("projectReviewRoute", "进入用章"),
                Map.entry("sealedQualityFileUploaded", true),
                Map.entry("nextRecommendation", "现场勘验")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", qcFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "projectReviewRoute", "进入用章"));
        
        // 质控用章子流程
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "质控用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));

        // 回到质控主路径
        completeTask(caseId, "SEALED_FILE_UPLOAD", Map.of("sealedQualityFileUploaded", true));
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "现场勘验"));
        System.out.println("5. 质控子流程结束，开启现场勘验子流程");

        // 6. 现场勘验子流程 (由于金额 20万 > 15万，将触发高金额三级联审)
        System.out.println("6. 开始流转现场勘验子流程...");
        Map<String, Object> surveyFormData = Map.ofEntries(
                Map.entry("surveyDate", "2026-06-22"),
                Map.entry("surveyLocation", "测试大额现场"),
                Map.entry("surveyPlanUploaded", true),
                Map.entry("fieldRecordUploaded", true),
                Map.entry("equipmentOutboundRecorded", true),
                Map.entry("equipmentUsageRecorded", true),
                Map.entry("equipmentReturnRecorded", true),
                Map.entry("projectAmount", 200000),
                Map.entry("majorAmountProject", true), // 自动计算大于15万为大额
                Map.entry("projectReviewPassed", true),
                Map.entry("projectReviewRoute", "技术负责人审核"),
                Map.entry("technicalReviewPassed", true),
                Map.entry("departmentReviewPassed", true),
                Map.entry("projectMaterialReviewPassed", true),
                Map.entry("nextRecommendation", "材料接收与返还")
        );
        completeTask(caseId, "ASSISTANT_SURVEY", surveyFormData);

        // 项目负责人审核，由于是高金额大额案件，判定需要技术负责人审核
        completeTask(caseId, "PROJECT_REVIEW", Map.of(
                "projectReviewPassed", true,
                "projectReviewRoute", "技术负责人审核"
        ));
        System.out.println(" -> 项目负责人审核通过，触发大额案件技术负责人审核");

        // 技术负责人审核
        completeTask(caseId, "TECHNICAL_REVIEW", Map.of("technicalReviewPassed", true));
        System.out.println(" -> 技术负责人审核通过，触发部门负责人审核");

        // 部门负责人审核
        completeTask(caseId, "DEPARTMENT_REVIEW", Map.of("departmentReviewPassed", true));
        System.out.println(" -> 部门负责人审核通过，转交项目负责人填写仪器设备");

        // 负责人转交设备记录
        completeTask(caseId, "PROJECT_TO_EQUIPMENT", Map.of());
        // 辅助人填写仪器设备
        completeTask(caseId, "ASSISTANT_EQUIPMENT", Map.of("equipmentUsageRecorded", true));
        // 负责人审核材料
        completeTask(caseId, "PROJECT_MATERIAL_REVIEW", Map.of("projectMaterialReviewPassed", true));
        // 负责人决策后续流向
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "材料接收与返还"));
        System.out.println(" -> 现场勘验子流程完成，进入材料接收与返还子流程");

        // 7. 材料接收与返还子流程
        Map<String, Object> materialFormData = Map.ofEntries(
                Map.entry("materialReceiveType", "委托方直接提供"),
                Map.entry("materialUploaderId", 9L),
                Map.entry("materialSource", "法院寄送"),
                Map.entry("requireSupplementaryMaterial", false),
                Map.entry("materialsUploaded", true),
                Map.entry("projectMaterialConfirmed", true),
                Map.entry("materialDetails", "场景3勘验大额材料"),
                Map.entry("receiveDate", "2026-06-22"),
                Map.entry("materialMediaType", "纸质原件"),
                Map.entry("storageLocation", "柜子"),
                Map.entry("requireReturn", false),
                Map.entry("storageStatus", "正常"),
                Map.entry("nextRecommendation", "鉴定意见书征求意见稿送审稿编制")
        );
        completeTask(caseId, "PROJECT_CONFIRM", materialFormData, 9L, "管理员");
        completeTask(caseId, "MATERIAL_UPLOAD", Map.of("materialsUploaded", true));
        completeTask(caseId, "PROJECT_MATERIAL_CONFIRM", Map.of("projectMaterialConfirmed", true));
        completeTask(caseId, "ASSISTANT_REGISTER", Map.of(
                "materialMediaType", "纸质原件",
                "storageLocation", "柜子",
                "requireReturn", false
        ));
        completeTask(caseId, "ARCHIVIST_HANDLE", Map.of("storageStatus", "正常"));
        completeTask(caseId, "PROJECT_DECISION", Map.of("nextRecommendation", "鉴定意见书征求意见稿送审稿编制"));
        System.out.println("7. 材料接收子流程结束，开启征求意见稿编制子流程");

        // 8. 征求意见稿送审稿编制子流程 (draft-opinion-review)
        completeTask(caseId, "PROJECT_ASSIGN", Map.of());
        Map<String, Object> draftOpinionFormData = Map.ofEntries(
                Map.entry("draftOpinionUploaded", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("technicalReviewPassed", true),
                Map.entry("departmentReviewPassed", true),
                Map.entry("finalDraftUploaded", true),
                Map.entry("nextRecommendation", "出具征求意见稿")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", draftOpinionFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "TECHNICAL_REVIEW", Map.of("technicalReviewPassed", true));
        completeTask(caseId, "DEPARTMENT_REVIEW", Map.of("departmentReviewPassed", true));
        completeTask(caseId, "PROJECT_FINAL_UPLOAD", Map.of("nextRecommendation", "出具征求意见稿"));
        System.out.println("8. 意见稿编制完成，开始出具意见稿");

        // 9. 出具征求意见稿子流程 (issue-draft-opinion)
        Map<String, Object> issueDraftFormData = Map.ofEntries(
                Map.entry("explainLetterDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", false),
                Map.entry("draftOpinionUploaded", true),
                Map.entry("sealedDraftOpinionUploaded", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true),
                Map.entry("feedbackReceived", true),
                Map.entry("feedbackHasObjection", true), // 触发当事人异议反馈
                Map.entry("feedbackDecision", "收到异议")     // 触发异议流程
        );
        completeTask(caseId, "ASSISTANT_SUPPLEMENT", issueDraftFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", false, "draftOpinionUploaded", true));
        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedDraftOpinionUploaded", true, "archiveConfirmed", true));
        completeTask(caseId, "DELIVERY", Map.of("deliveryMethod", "电子送达"));
        
        // 核心跳转点：在等待反馈节点，选择“收到异议”
        completeTask(caseId, "WAIT_FEEDBACK", Map.of(
                "feedbackReceived", true,
                "feedbackHasObjection", true,
                "feedbackDecision", "收到异议"
        ));
        System.out.println("9. 出具征求意见稿反馈登记为“收到异议”，成功挂载开启并流转到法院异议函 (court-letter) 子流程");

        // 10. 进入 court-letter 收到法院函件(异议函) 子流程办理
        Map<String, Object> courtLetterFormData = Map.ofEntries(
                Map.entry("receivedLetterUploaded", true),
                Map.entry("projectLeaderAssigned", true),
                Map.entry("letterType", "异议函"),
                Map.entry("letterReceivedDate", "2026-06-22"),
                Map.entry("letterSummary", "当事人对测绘计算有异议"),
                Map.entry("objectionAccepted", true),
                Map.entry("replyDraftCompleted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", false),
                Map.entry("sealedReplyUploaded", true),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("trackingNo", "SF-E2E-OBJECTION"),
                Map.entry("deliveryDate", "2026-06-22"),
                Map.entry("archiveConfirmed", true),
                Map.entry("nextRecommendation", "进入出具鉴定意见书")
        );
        completeTask(caseId, "LETTER_UPLOAD", courtLetterFormData);
        completeTask(caseId, "PROJECT_REGISTER", Map.of("objectionAccepted", true));
        completeTask(caseId, "ASSISTANT_REPLY", Map.of("replyDraftCompleted", true));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", false));
        completeTask(caseId, "SEALED_REPLY_UPLOAD", Map.of("sealedReplyUploaded", true));
        
        // 并行分支：发函和归档
        completeTask(caseId, "DELIVERY_RELATED_LETTER", Map.of("archiveConfirmed", true, "deliveryDate", "2026-06-22"));
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "进入出具鉴定意见书"));
        System.out.println("10. 法院异议函子流程办理完毕，选择进入“出具鉴定意见书”流程");

        // 11. 进入主链的出具鉴定意见书子流程 (issue-opinion)
        System.out.println("11. 开始出具鉴定意见书子流程办理...");
        Map<String, Object> issueOpinionFormData = Map.ofEntries(
                Map.entry("opinionModified", true),
                Map.entry("commitmentDrafted", true),
                Map.entry("reviewOpinionDrafted", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("sealRequired", false),
                Map.entry("systemRegistrationUploaded", true),
                Map.entry("sealedOpinionUploaded", true),
                Map.entry("invoiceRequired", false),
                Map.entry("invoiceIssued", false),
                Map.entry("deliveryMethod", "电子送达"),
                Map.entry("archiveConfirmed", true)
        );
        completeTask(caseId, "PROJECT_MODIFY", issueOpinionFormData);
        completeTask(caseId, "ASSISTANT_UPLOAD", issueOpinionFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true));
        completeTask(caseId, "ARCHIVIST_CONFIRM", Map.of("sealRequired", false, "invoiceRequired", false, "systemRegistrationUploaded", true));
        
        // 并行分支：虽然无需用章，但仍需在 SEALED_UPLOAD 上传盖章件（或确认回传版）以汇聚并行网关
        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedOpinionUploaded", true));
        
        completeTask(caseId, "DELIVERY_ARCHIVE", Map.of("archiveConfirmed", true));
        System.out.println("12. 鉴定意见书出具完毕，启动结案归档");

        // 12. 归档子流程 (archive)
        // 包含主干归档和各个支线触发的归档，此处模拟全部归档任务直至结案
        completeAllArchiveSubflows(caseId);

        // 13. 验证案件状态变更为 COMPLETED
        CaseInfo finalCase = caseInfoMapper.selectById(caseId);
        assertThat(finalCase.getCaseStatus()).isEqualTo(CaseStatus.COMPLETED.name());
        System.out.println(">>> 自动化测试成功！大额现场勘验、异议反馈异议函处理及出具意见书归档完毕，案件状态为 COMPLETED <<<");
    }

    private void completeAllArchiveSubflows(Long caseId) {
        Map<String, Object> archiveFormData = Map.ofEntries(
                Map.entry("projectArchiveUploaded", true),
                Map.entry("paperScansUploaded", true),
                Map.entry("deliveryRoute", "直接中心审核")
        );
        while (countActiveTasks(caseId, "ARCHIVIST_PREPARE") > 0) {
            completeTask(caseId, "ARCHIVIST_PREPARE", archiveFormData);
        }
        while (countActiveTasks(caseId, "CENTRAL_REVIEW") > 0) {
            completeTask(caseId, "CENTRAL_REVIEW", Map.of("centralArchiveApproved", true));
        }
    }

    private long countActiveTasks(Long caseId, String nodeCode) {
        return caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing", "subflow_running"));
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        completeTask(caseId, nodeCode, ActionCode.APPROVE, formData, null, null, null);
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData, Long nextAssigneeId, String nextAssigneeName) {
        completeTask(caseId, nodeCode, ActionCode.APPROVE, formData, null, nextAssigneeId, nextAssigneeName);
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        completeTask(caseId, nodeCode, actionCode, formData, reason, null, null);
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason, Long nextAssigneeId, String nextAssigneeName) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("未找到或未激活的任务节点: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "集成自动化测试流转", reason, null, null,
                nextAssigneeId, nextAssigneeName, formData, null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
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
                        // ignore
                    }
                }
            }
        }
    }
}
