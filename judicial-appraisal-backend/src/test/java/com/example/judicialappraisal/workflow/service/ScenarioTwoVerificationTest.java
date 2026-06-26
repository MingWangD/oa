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

@SpringBootTest
class ScenarioTwoVerificationTest {

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
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @BeforeEach
    void setUp() {
        ensureOperatorHasAllJudicialRoles();
        judicialConfigImportService.importCatalog(true);
    }

    @Test
    void runScenarioTwo_E2E_NoRollback() {
        System.out.println(">>> 开始运行场景二自动化测试流程 <<<");

        // 1. 创建草稿
        String title = "场景2：F类质控文件审核与用章驳回退回";
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "工程造价", "深圳市福田区人民法院", 1L));
        Long caseId = caseInfo.getId();
        caseInfo.setCaseNo(title + "-" + System.currentTimeMillis());
        caseInfoMapper.updateById(caseInfo);
        System.out.println("1. 成功创建案件草稿, ID=" + caseId);

        // 2. 提交案件开启流程
        CaseSubmitRequest submitRequest = new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起流程");
        WorkflowActionResult submitResult = caseInfoService.submitCase(caseId, submitRequest, OPERATOR_ID, OPERATOR_NAME);
        assertThat(submitResult.success()).isTrue();
        System.out.println("2. 成功提交流转案件, 主流程已开启");

        // 3. 填写委托信息 (INIT_FILL)
        Map<String, Object> initFormData = Map.ofEntries(
                Map.entry("clientName", "深圳市福田区人民法院"),
                Map.entry("serialNo", "SC-2-123456"),
                Map.entry("initiatorName", "管理员"),
                Map.entry("initiatedDate", "2026-06-23"),
                Map.entry("receivedDate", "2026-06-23"),
                Map.entry("caseNo", "场景2：F类质控文件审核与用章驳回退回-" + System.currentTimeMillis()),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("appraisalMatter", "工程造价司法鉴定"),
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
        System.out.println("3. 成功提交 INIT_FILL 登记节点数据");

        // 4. 收案员登记 (CLERK_REGISTER)
        completeTask(caseId, "CLERK_REGISTER", Map.of());
        System.out.println("4. 成功提交 CLERK_REGISTER 节点");

        // 5. 部门负责人审阅 (DEPT_REVIEW)
        completeTask(caseId, "DEPT_REVIEW", Map.of("entrustAccepted", true));
        System.out.println("5. 成功提交 DEPT_REVIEW 部门负责人受理决策");

        // 6. 项目负责人决策 (PROJECT_DECISION)
        completeTask(caseId, "PROJECT_DECISION", Map.of("preliminarySurveyRequired", false, "materialReceiveRequired", false));
        System.out.println("6. 成功提交 PROJECT_DECISION，流转进入发交费通知子流程");

        // 并行通知辅助人节点 (ASSISTANT_NOTICE)
        completeTask(caseId, "ASSISTANT_NOTICE", Map.of());

        // 7. 进入 payment-notice 子流程
        System.out.println("7. 开启发交费通知子流程办理...");
        Map<String, Object> paymentNoticeFormData = Map.ofEntries(
                Map.entry("letterDraftCompleted", true),
                Map.entry("letterType", "交费通知书"),
                Map.entry("letterSummary", "大额缴费通知书草稿已编制"),
                Map.entry("projectAmount", 120000),
                Map.entry("sealRequired", false),
                Map.entry("sealedDocumentUploaded", true),
                Map.entry("paymentReceived", true),
                Map.entry("nextRecommendation", "编制内部质量控制文件")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", paymentNoticeFormData);
        completeTask(caseId, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(caseId, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", true));
        
        // 核心修正：此处为发收费确认节点，仅勾选 paymentReceived = true 进入质控，不填写合同金额
        completeTask(caseId, "PAYMENT_CONFIRM", Map.of("paymentReceived", true, "nextRecommendation", "编制内部质量控制文件"));
        System.out.println("8. 发交费通知子流程结束，开启编制内部质量控制文件子流程...");

        // 8. 进入 quality-control 内部质量控制文件编制子流程
        Map<String, Object> qcFormData = Map.ofEntries(
                Map.entry("qualityFileDraftCompleted", true),
                Map.entry("qualityFileSummary", "大额质控文件草稿编制完成"),
                Map.entry("formatType", "非中心格式"),
                Map.entry("contractAmount", 300000), // 30万 (非中心格式，大于25万属于F类)
                Map.entry("fClassProject", true),
                Map.entry("projectReviewPassed", true),
                Map.entry("projectReviewRoute", "部门负责人审核"),
                Map.entry("sealedQualityFileUploaded", true),
                Map.entry("nextRecommendation", "终止鉴定")
        );
        completeTask(caseId, "ASSISTANT_DRAFT", qcFormData);

        // 核心修正：此处为质控审核节点，项目负责人在此处判定并填入 300000 金额与 F 类标记，流向为部门负责人审核
        completeTask(caseId, "PROJECT_REVIEW", Map.of(
                "formatType", "非中心格式",
                "contractAmount", 300000,
                "fClassProject", true,
                "projectReviewPassed", true,
                "projectReviewRoute", "部门负责人审核"
        ));
        System.out.println("9. 成功在质控审核节点判定 F 类大额合同金额为 30万 并流转至部门负责人");

        // 9. 部门负责人审核 F 类项目 (DEPARTMENT_REVIEW)
        completeTask(caseId, "DEPARTMENT_REVIEW", Map.of("departmentReviewPassed", true));
        System.out.println("10. 部门负责人审核 F 类质控文件通过，进入用章申请流程...");

        // 10. 进入 seal-application 子流程 (驳回与退回测试)
        System.out.println("11. 启动用章流程驳回退回测试链路...");
        
        // APPLICANT_SUBMIT 用章申请提交
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "F类质控文件用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));

        // 第一次驳回：档案管理员审核退回给申请人
        completeTask(caseId, "ARCHIVIST_REVIEW", ActionCode.RETURN, Map.of("archivistReviewed", false), "材料不完整，予以退回");
        System.out.println(" -> 档案管理员审核驳回成功，任务回退至申请人提交");

        // 申请人重新提交
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "F类质控文件用章(已修改重新提交)",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));

        // 档案管理员重新审核通过
        completeTask(caseId, "ARCHIVIST_REVIEW", ActionCode.APPROVE, Map.of("archivistReviewed", true), null);
        System.out.println(" -> 档案管理员重新审核通过成功，转交盖章人");

        // 第二次驳回：盖章经办人退回给档案管理员
        completeTask(caseId, "SEAL_OPERATOR", ActionCode.RETURN, Map.of("sealCompleted", false), "印鉴不匹配，退回重新审核");
        System.out.println(" -> 盖章经办人驳回成功，任务回退至档案管理员审核");

        // 档案管理员第三次审核通过
        completeTask(caseId, "ARCHIVIST_REVIEW", ActionCode.APPROVE, Map.of("archivistReviewed", true), null);

        // 盖章经办人最终通过
        completeTask(caseId, "SEAL_OPERATOR", ActionCode.APPROVE, Map.of("sealCompleted", true), null);

        // 档案管理员回传用章扫描件
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
        System.out.println("12. 用章流程通过并上传完毕，用章子流程结束");

        // 11. 回到 quality-control 子流程回传与建议
        completeTask(caseId, "SEALED_FILE_UPLOAD", Map.of("sealedQualityFileUploaded", true));
        
        // 核心流向：为了以最合理的闭环快速结案归档，我们将后续流向设定为“终止鉴定”
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "终止鉴定"));
        System.out.println("13. 质控子流程确认缴费与用章完成，流转进入“终止鉴定”子流程");

        // 12. 进入 terminate-appraisal (终止鉴定) 子流程推进结案
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "测试F类案件终止",
                "draftCompleted", true
        ));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "sealRequired", true));
        
        // 由于系统设计中，终止文书审核通过后强制流转用章子流程，在此模拟流转用章子流程
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "终止文书用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
        System.out.println(" -> 终止用章流程办理完成");

        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedTerminationUploaded", true, "archiveConfirmed", true));
        System.out.println("14. 终止鉴定函件审核、用章与回传已完成，自动发起归档子流程...");

        // 13. 进入 archive (归档) 子流程进行物理与电子件入库结案
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "deliveryRoute", "直接中心审核"
        ));
        completeTask(caseId, "CENTRAL_REVIEW", Map.of("centralArchiveApproved", true));
        System.out.println("15. 档案管理员归档整理与中心档案管理员入库审核已完成");

        // 14. 验证最终归档结案状态
        CaseInfo finalCase = caseInfoMapper.selectById(caseId);
        assertThat(finalCase.getCaseStatus()).isEqualTo(CaseStatus.COMPLETED.name());
        System.out.println(">>> 自动化测试完美结束！案件状态已变更为 COMPLETED (已归档结案) <<<");
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        completeTask(caseId, nodeCode, ActionCode.APPROVE, formData, null);
    }

    private void completeTask(Long caseId, String nodeCode, ActionCode actionCode, Map<String, Object> formData, String reason) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("未找到或未激活的任务节点: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), actionCode, "集成自动化测试流转", reason, null, null,
                null, null, formData, null);
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
