package com.example.judicialappraisal.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.caseinfo.service.CaseInfoService;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.entity.SysUserRole;
import java.util.ArrayList;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.common.enums.ActionCode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FinalE2EWalkthroughVerificationTest {

    private static final Long OPERATOR_ID = 1L; // Admin
    private static final String OPERATOR_NAME = "系统管理员";

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
    void runUltimateE2EWalkthrough() {
        System.out.println("==========================================================");
        System.out.println(">>> 开启 最终全链路+必填项 自动化测试流程 (图二主流程) <<<");
        System.out.println("==========================================================");

        // 1. 创建草稿
        String title = "最终检验：全链路必填项测试案件-测试版-" + System.currentTimeMillis();
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "法医临床", "测试法院", OPERATOR_ID));
        Long caseId = caseInfo.getId();
        caseInfo.setCaseNo(title + "-" + System.currentTimeMillis());
        caseInfoMapper.updateById(caseInfo);
        System.out.println("1. 成功创建案件草稿, ID=" + caseId);

        // 2. 提交案件开启流程
        CaseSubmitRequest submitRequest = new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起全链路测试流程");
        WorkflowActionResult submitResult = caseInfoService.submitCase(caseId, submitRequest, OPERATOR_ID, OPERATOR_NAME);
        assertThat(submitResult.success()).isTrue();
        System.out.println("2. 成功提交流转案件, 主流程已开启");

        // 3. 填写委托信息 (INIT_FILL) (带着所有必填项)
        Map<String, Object> formData = new HashMap<>();
        formData.put("clientName", "测试委托人");
        formData.put("serialNo", "TEST-2026-06-25");
        formData.put("initiatorName", "管理员");
        formData.put("initiatedDate", "2026-06-25");
        formData.put("receivedDate", "2026-06-25");
        formData.put("caseNo", title);
        formData.put("appraisalCategory", "法医临床");
        formData.put("urgencyLevel", "普通");
        formData.put("caseChannel", "线下");
        formData.put("appraisalMatter", "法医临床鉴定");
        formData.put("entrustAccepted", true);
        formData.put("preliminarySurveyRequired", false);
        formData.put("departmentHeadId", 8L);
        formData.put("projectLeaderId", 28L);
        formData.put("projectAssistantId", 30L);
        formData.put("materialReceiveRequired", false);
        formData.put("filingDate", "2026-06-25");
        formData.put("undertakingLegalPerson", "法定代表人测试");
        formData.put("institutionSelectionMethod", "随机抽取");
        formData.put("institutionSelectionTime", "2026-06-25");
        formData.put("applicantName", "原告张三");
        formData.put("respondentName", "被告李四");
        completeTask(caseId, "INIT_FILL", formData);
        System.out.println("3. 成功提交 INIT_FILL 登记节点数据");

        // 4. 收案员登记 (CLERK_REGISTER)
        completeTask(caseId, "CLERK_REGISTER", new HashMap<>());
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
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "letterDraftCompleted", true,
                "letterType", "交费通知书",
                "letterSummary", "通知书草稿已编制",
                "projectAmount", 120000,
                "sealRequired", false,
                "sealedDocumentUploaded", true,
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(caseId, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", true));
        
        // PAYMENT_CONFIRM
        completeTask(caseId, "PAYMENT_CONFIRM", Map.of("paymentReceived", true, "nextRecommendation", "编制内部质量控制文件"));
        System.out.println("8. 发交费通知子流程结束，开启编制内部质量控制文件子流程...");

        // 8. 进入 quality-control 内部质量控制文件编制子流程
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "qualityFileDraftCompleted", true,
                "qualityFileSummary", "质控文件草稿编制完成",
                "formatType", "中心格式",
                "contractAmount", 10000, 
                "fClassProject", false,
                "projectReviewPassed", true,
                "sealedQualityFileUploaded", true,
                "nextRecommendation", "出具鉴定意见书"
        ));
        completeTask(caseId, "PROJECT_REVIEW", Map.of(
                "formatType", "中心格式",
                "contractAmount", 10000,
                "fClassProject", false,
                "projectReviewPassed", true,
                "projectReviewRoute", "进入用章",
                "sealRequired", true,
                "sealedQualityFileUploaded", true
        ));
        System.out.println("9. 成功提交质控审核节点，由于是普通类别，直接进入用章申请...");

        // 用章子流程 (seal-application)
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "质控文件用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
        System.out.println("10. 用章流程通过并上传完毕，用章子流程结束");

        completeTask(caseId, "SEALED_FILE_UPLOAD", Map.of("sealedQualityFileUploaded", true));
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "终止鉴定"));
        System.out.println("11. 质控子流程结束，流转进入“终止鉴定”子流程");

        // 9. 进入 terminate-appraisal 子流程
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "测试最终主流程流转并归档",
                "draftCompleted", true
        ));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "sealRequired", true));
        
        // 用章子流程
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "终止文书用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));
        System.out.println("12. 终止鉴定函用章办理完成");

        completeTask(caseId, "SEALED_UPLOAD", Map.of("sealedTerminationUploaded", true, "archiveConfirmed", true));
        System.out.println("13. 终止鉴定函回传已完成，进入“归档”阶段！");

        // 10. 进入 archive-subflow 归档子流程
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "deliveryRoute", "直接中心审核"
        ));
        completeTask(caseId, "CENTRAL_REVIEW", Map.of("centralArchiveApproved", true, "archiveConfirmed", true, "archiveNotes", "全链路自动归档入库"));
        
        System.out.println("14. 归档子流程结束，整个案件完美闭环结案并归档入库！");
        
        CaseInfo completedCase = caseInfoMapper.selectById(caseId);
        assertThat(completedCase.getCaseStatus()).isEqualTo("COMPLETED");
        System.out.println("=== 案件最终状态: " + completedCase.getCaseStatus() + " ===");
    }

    private void completeTask(Long caseId, String nodeCode, Map<String, Object> formData) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByDesc(CaseTask::getId)
                .last("LIMIT 1"));
        assertThat(task).withFailMessage("未找到或未激活的任务节点: " + nodeCode).isNotNull();

        WorkflowActionRequest request = new WorkflowActionRequest(
                task.getId(), ActionCode.APPROVE, "全链路集成测试流转", null, null, null,
                null, null, formData, null);
        workflowRuntimeService.completeTask(caseId, request, OPERATOR_ID, OPERATOR_NAME);
    }

    private synchronized void ensureOperatorHasAllJudicialRoles() {
        List<String> roles = new ArrayList<>(platformCatalogService.judicialCatalog().dedicatedRoles());
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员", "中心档案管理员"));
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
