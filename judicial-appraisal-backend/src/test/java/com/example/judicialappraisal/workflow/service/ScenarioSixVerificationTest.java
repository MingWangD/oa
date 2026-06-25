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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScenarioSixVerificationTest {

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
    private com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper wfDefinitionMapper;

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
    @org.springframework.transaction.annotation.Transactional
    void runScenarioSix_A_SuspendAndResume() {
        System.out.println(">>> 开始运行场景六 A：案件暂停与恢复办理 E2E 测试 <<<");

        // 1. 创建并推进主案件到现场勘验节点
        Long caseId = createAndProgressCaseToFieldSurvey("场景6暂停恢复案件_" + System.currentTimeMillis());

        // 2. 找到现场勘验的主任务 ASSISTANT_SURVEY，作为暂停的 parentTask
        CaseTask parentTask = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, "ASSISTANT_SURVEY")
                .eq(CaseTask::getStatus, "pending")
                .last("LIMIT 1"));
        assertThat(parentTask).isNotNull();

        CaseWfInstance wfInstance = caseWfInstanceMapper.selectOne(new LambdaQueryWrapper<CaseWfInstance>()
                .eq(CaseWfInstance::getCaseId, caseId)
                .eq(CaseWfInstance::getStatus, "running")
                .orderByDesc(CaseWfInstance::getId)
                .last("LIMIT 1"));
        assertThat(wfInstance).isNotNull();

        // 3. 手动触发 案件暂停 (case-suspension) 子流程
        CaseSubflowInstance subflow = triggerSuspensionSubflow(caseId, wfInstance, parentTask);

        // 4. 推进 case-suspension 子流程
        // START -> PROJECT_APPLY
        completeTask(caseId, "START", Map.of());

        // PROJECT_APPLY -> AUTH_REVIEW (负责人重新提交测试在此处简化，直接提交)
        completeTask(caseId, "PROJECT_APPLY", Map.of(
                "suspensionReason", "等待原告补充测绘数据",
                "expectedResumeDate", "2026-07-22",
                "draftCompleted", true
        ));

        // AUTH_REVIEW 审批通过并恢复：suspensionDecision == '恢复办理'
        completeTask(caseId, "AUTH_REVIEW", Map.of(
                "suspensionDecision", "恢复办理",
                "approverOpinion", "核实无误，予以恢复办理"
        ));

        // RESUME -> END (恢复办理完成)
        completeTask(caseId, "RESUME", Map.of());

        System.out.println("-> 案件暂停子流程已恢复办理并结束");

        // 打印所有任务状态以供诊断
        System.out.println("=== 诊断：打印所有任务 ===");
        List<CaseTask> allTasks = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .orderByAsc(CaseTask::getId));
        for (CaseTask t : allTasks) {
            System.out.println(String.format("Task ID=%d, Node=%s (%s), Status=%s, SubflowInstanceID=%s, WfInstanceID=%d",
                    t.getId(), t.getNodeCode(), t.getNodeName(), t.getStatus(), t.getSubflowInstanceId(), t.getWfInstanceId()));
        }
        System.out.println("=========================");

        // 5. 验证：子流程结束，parentTask (ASSISTANT_SURVEY) 自动回到 pending 状态
        CaseTask updatedParent = caseTaskMapper.selectById(parentTask.getId());
        assertThat(updatedParent.getStatus()).isEqualTo("pending");

        // 现在手动完成 ASSISTANT_SURVEY，推进到 PROJECT_REVIEW
        completeTask(caseId, "ASSISTANT_SURVEY", Map.ofEntries(
                Map.entry("surveyDate", "2026-06-23"),
                Map.entry("surveyLocation", "深圳现场"),
                Map.entry("surveyPlanUploaded", true),
                Map.entry("fieldRecordUploaded", true),
                Map.entry("equipmentOutboundRecorded", true),
                Map.entry("equipmentUsageRecorded", true),
                Map.entry("equipmentReturnRecorded", true),
                Map.entry("projectAmount", 120000),
                Map.entry("majorAmountProject", false),
                Map.entry("projectReviewPassed", true),
                Map.entry("projectReviewRoute", "确认后续流程"),
                Map.entry("technicalReviewPassed", true),
                Map.entry("departmentReviewPassed", true),
                Map.entry("projectMaterialReviewPassed", true),
                Map.entry("nextRecommendation", "鉴定意见书送审稿编制")
        ));

        // 验证主流程已推进至现场勘验审核节点 (PROJECT_REVIEW)
        CaseTask nextMainTask = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, "PROJECT_REVIEW")
                .eq(CaseTask::getStatus, "pending")
                .last("LIMIT 1"));
        assertThat(nextMainTask).isNotNull();
        System.out.println(">>> 场景六 A 测试顺利通过：暂停恢复后成功回到主流程推进！ <<<");
    }

    @Test
    void runScenarioSix_B_SuspendAndTerminate() {
        System.out.println(">>> 开始运行场景六 B：案件暂停并决定终止鉴定 E2E 测试 <<<");

        // 1. 创建并推进主案件到现场勘验节点
        Long caseId = createAndProgressCaseToFieldSurvey("场景6：案件暂停与恢复/终止-" + System.currentTimeMillis());

        // 2. 找到现场勘验的主任务 ASSISTANT_SURVEY
        CaseTask parentTask = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, "ASSISTANT_SURVEY")
                .eq(CaseTask::getStatus, "pending")
                .last("LIMIT 1"));
        assertThat(parentTask).isNotNull();

        CaseWfInstance wfInstance = caseWfInstanceMapper.selectOne(new LambdaQueryWrapper<CaseWfInstance>()
                .eq(CaseWfInstance::getCaseId, caseId)
                .eq(CaseWfInstance::getStatus, "running")
                .orderByDesc(CaseWfInstance::getId)
                .last("LIMIT 1"));
        assertThat(wfInstance).isNotNull();

        // 3. 手动触发 案件暂停 (case-suspension) 子流程
        CaseSubflowInstance subflow = triggerSuspensionSubflow(caseId, wfInstance, parentTask);

        // 4. 推进 case-suspension 子流程
        completeTask(caseId, "START", Map.of());

        completeTask(caseId, "PROJECT_APPLY", Map.of(
                "suspensionReason", "双方达成和解，无需继续鉴定",
                "draftCompleted", true
        ));

        // AUTH_REVIEW 审批并决定终止：suspensionDecision == '终止鉴定'
        // 这将自动触发子流程 terminate-appraisal
        completeTask(caseId, "AUTH_REVIEW", Map.of(
                "suspensionDecision", "终止鉴定",
                "approverOpinion", "同意终止鉴定"
        ));
        System.out.println("-> 暂停审批通过并决定终止，自动进入“终止鉴定”子流程");

        // 5. 推进终止鉴定子流程
        // ASSISTANT_DRAFT 编制终止文书
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "terminationType", "鉴定终止函",
                "terminationReason", "因案件暂停后决定终止鉴定",
                "draftCompleted", true
        ));

        // PROJECT_REVIEW 审核终止文书且需要用章
        completeTask(caseId, "PROJECT_REVIEW", Map.of(
                "projectReviewPassed", true,
                "sealRequired", true
        ));

        // 用章申请推进
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "暂停终止文书用章",
                "sealMode", "物理印章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));

        // 回传盖章终止文书并确认归档 (SEALED_UPLOAD)
        completeTask(caseId, "SEALED_UPLOAD", Map.of(
                "sealedTerminationUploaded", true,
                "archiveConfirmed", true
        ));

        // 6. 推进归档子流程 (直接中心审核)
        completeTask(caseId, "ARCHIVIST_PREPARE", Map.of(
                "projectArchiveUploaded", true,
                "paperScansUploaded", true,
                "deliveryRoute", "直接中心审核"
        ));
        completeTask(caseId, "CENTRAL_REVIEW", Map.of(
                "centralArchiveApproved", true,
                "archiveRoomLocation", "南山档案库-D区"
        ));

        System.out.println("-> 终止与归档流程办理完毕");

        // 打印所有任务状态以供诊断
        System.out.println("=== 诊断：打印所有任务 ===");
        List<CaseTask> allTasks = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .orderByAsc(CaseTask::getId));
        for (CaseTask t : allTasks) {
            System.out.println(String.format("Task ID=%d, Node=%s (%s), Status=%s, SubflowInstanceID=%s, WfInstanceID=%d",
                    t.getId(), t.getNodeCode(), t.getNodeName(), t.getStatus(), t.getSubflowInstanceId(), t.getWfInstanceId()));
        }
        System.out.println("=========================");

        // 7. 验证最终案件归档结案状态
        CaseInfo finalCase = caseInfoMapper.selectById(caseId);
        assertThat(finalCase.getCaseStatus()).isEqualTo(CaseStatus.COMPLETED.name());
        System.out.println(">>> 场景六 B 测试顺利通过：暂停并终止后案件成功归档结案！ <<<");
    }

    private Long createAndProgressCaseToFieldSurvey(String title) {
        CaseInfo caseInfo = caseInfoService.createDraft(new CaseCreateRequest(title, "工程造价", "深圳市南山区人民法院", 1L));
        Long caseId = caseInfo.getId();
        caseInfo.setCaseNo(title + "-" + System.currentTimeMillis());
        caseInfoMapper.updateById(caseInfo);

        CaseSubmitRequest submitRequest = new CaseSubmitRequest(OPERATOR_ID, OPERATOR_NAME, "发起流程");
        caseInfoService.submitCase(caseId, submitRequest, OPERATOR_ID, OPERATOR_NAME);

        Map<String, Object> initFormData = Map.ofEntries(
                Map.entry("clientName", "深圳市南山区人民法院"),
                Map.entry("serialNo", "SC-6-123456"),
                Map.entry("initiatorName", "管理员"),
                Map.entry("initiatedDate", "2026-06-23"),
                Map.entry("receivedDate", "2026-06-23"),
                Map.entry("caseNo", title),
                Map.entry("appraisalCategory", "工程造价"),
                Map.entry("urgencyLevel", "普通"),
                Map.entry("caseChannel", "线下"),
                Map.entry("projectAmount", 120000),
                Map.entry("appraisalMatter", "暂停恢复测试鉴定"),
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

        // 发交费通知
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "letterDraftCompleted", true,
                "letterType", "交费通知书",
                "letterSummary", "12万交费通知",
                "sealRequired", false,
                "sealedDocumentUploaded", true,
                "paymentReceived", true,
                "nextRecommendation", "编制内部质量控制文件"
        ));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("sealRequired", false));
        completeTask(caseId, "ARCHIVE_UPLOAD", Map.of("sealedDocumentUploaded", true));
        completeTask(caseId, "PAYMENT_CONFIRM", Map.of("paymentReceived", true, "nextRecommendation", "编制内部质量控制文件"));

        // 编制内部质量控制文件
        completeTask(caseId, "ASSISTANT_DRAFT", Map.of(
                "qualityFileDraftCompleted", true,
                "qualityFileSummary", "常规质控文件草稿",
                "formatType", "非中心格式",
                "contractAmount", 120000,
                "fClassProject", false,
                "projectReviewPassed", true,
                "projectReviewRoute", "进入用章",
                "sealedQualityFileUploaded", true,
                "nextRecommendation", "现场勘验"
        ));
        completeTask(caseId, "PROJECT_REVIEW", Map.of("projectReviewPassed", true, "projectReviewRoute", "进入用章"));
        
        // 质控用章
        completeTask(caseId, "APPLICANT_SUBMIT", Map.of(
                "applicationReason", "质控用章",
                "sealMode", "线下盖章",
                "applicationFilesPrepared", true
        ));
        completeTask(caseId, "ARCHIVIST_REVIEW", Map.of("archivistReviewed", true));
        completeTask(caseId, "SEAL_OPERATOR", Map.of("sealCompleted", true));
        completeTask(caseId, "ARCHIVIST_UPLOAD", Map.of("sealedScanUploaded", true));

        // 回传质控文件并流转到 现场勘验
        completeTask(caseId, "SEALED_FILE_UPLOAD", Map.of("sealedQualityFileUploaded", true));
        completeTask(caseId, "NEXT_FLOW_DECISION", Map.of("nextRecommendation", "现场勘验"));

        return caseId;
    }

    private CaseSubflowInstance triggerSuspensionSubflow(Long caseId, CaseWfInstance wfInstance, CaseTask parentTask) {
        com.example.judicialappraisal.workflow.entity.WfDefinition subflowDef = wfDefinitionMapper.selectOne(
                new LambdaQueryWrapper<com.example.judicialappraisal.workflow.entity.WfDefinition>()
                        .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getWfCode, "case-suspension")
                        .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getPublishStatus, "published")
                        .eq(com.example.judicialappraisal.workflow.entity.WfDefinition::getDeleted, 0)
                        .orderByDesc(com.example.judicialappraisal.workflow.entity.WfDefinition::getVersionNo)
                        .last("LIMIT 1"));

        // 更新父任务状态为 subflow_running
        parentTask.setStatus("subflow_running");
        caseTaskMapper.updateById(parentTask);

        // 插入子流程实例
        CaseSubflowInstance subflow = new CaseSubflowInstance();
        subflow.setCaseId(caseId);
        subflow.setParentWfInstanceId(wfInstance.getId());
        subflow.setParentTaskId(parentTask.getId());
        subflow.setParentNodeCode(parentTask.getNodeCode());
        subflow.setWfId(subflowDef.getId());
        subflow.setWfCode(subflowDef.getWfCode());
        subflow.setWfName(subflowDef.getWfName());
        subflow.setSubflowType(subflowDef.getWfCode());
        subflow.setStatus("running");
        subflow.setStartedBy(OPERATOR_ID);
        subflow.setStartedTime(LocalDateTime.now());
        caseSubflowInstanceMapper.insert(subflow);

        // 插入子流程的 START 任务
        CaseTask startTask = new CaseTask();
        startTask.setCaseId(caseId);
        startTask.setWfInstanceId(wfInstance.getId());
        startTask.setSubflowInstanceId(subflow.getId());
        startTask.setNodeInstanceId(1L);
        startTask.setTaskType("single");
        startTask.setTaskTitle("开始任务");
        startTask.setNodeCode("START");
        startTask.setNodeName("开始");
        startTask.setStatus("pending");
        startTask.setAssigneeId(OPERATOR_ID);
        startTask.setAssigneeName(OPERATOR_NAME);
        startTask.setStartedTime(LocalDateTime.now());
        startTask.setOvertimeFlag(0);
        caseTaskMapper.insert(startTask);

        System.out.println("-> 手动挂载发起 案件暂停 (case-suspension) 子流程成功");
        return subflow;
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
        roles.addAll(List.of("发起者", "申请人", "盖章经办人", "邮寄人员", "财务", "质量控制人", "中心档案管理员", "授权审批人"));
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
