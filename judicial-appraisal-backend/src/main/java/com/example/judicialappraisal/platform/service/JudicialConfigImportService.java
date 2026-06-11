package com.example.judicialappraisal.platform.service;

import com.example.judicialappraisal.platform.dto.JudicialConfigImportResult;
import com.example.judicialappraisal.platform.dto.JudicialFormDefinitionDto;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowDefinitionDto;
import com.example.judicialappraisal.workflow.design.FormDesignService;
import com.example.judicialappraisal.workflow.design.WorkflowDesignService;
import com.example.judicialappraisal.workflow.design.dto.FormDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowNodeRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowTransitionRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JudicialConfigImportService {

    private final PlatformCatalogService platformCatalogService;
    private final FormDesignService formDesignService;
    private final WorkflowDesignService workflowDesignService;
    private final ObjectMapper objectMapper;

    public JudicialConfigImportService(PlatformCatalogService platformCatalogService,
                                       FormDesignService formDesignService,
                                       WorkflowDesignService workflowDesignService,
                                       ObjectMapper objectMapper) {
        this.platformCatalogService = platformCatalogService;
        this.formDesignService = formDesignService;
        this.workflowDesignService = workflowDesignService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public JudicialConfigImportResult importCatalog(boolean forceNewVersion) {
        int formsCreated = 0;
        int formsSkipped = 0;
        int workflowsCreated = 0;
        int workflowsSkipped = 0;
        List<String> messages = new ArrayList<>();

        for (JudicialFormDefinitionDto form : platformCatalogService.judicialCatalog().forms()) {
            if (!forceNewVersion && hasPublishedForm(form.code())) {
                formsSkipped++;
                continue;
            }
            formDesignService.saveDraft(toFormRequest(form));
            formDesignService.publish(form.code());
            formsCreated++;
            messages.add("表单已发布：" + form.name());
        }

        for (JudicialWorkflowDefinitionDto workflow : platformCatalogService.judicialCatalog().workflows()) {
            if (!forceNewVersion && hasPublishedWorkflow(workflow.code())) {
                workflowsSkipped++;
                continue;
            }
            workflowDesignService.saveDraft(toWorkflowRequest(workflow));
            workflowDesignService.publish(workflow.code());
            workflowsCreated++;
            messages.add("流程已发布：" + workflow.name());
        }

        return new JudicialConfigImportResult(formsCreated, formsSkipped, workflowsCreated, workflowsSkipped, messages);
    }

    private boolean hasPublishedForm(String formCode) {
        return formDesignService.listVersions(formCode).stream().anyMatch(version -> "published".equals(version.status()));
    }

    private boolean hasPublishedWorkflow(String wfCode) {
        return workflowDesignService.listVersions(wfCode).stream().anyMatch(version -> "published".equals(version.publishStatus()));
    }

    private FormDesignRequest toFormRequest(JudicialFormDefinitionDto form) {
        if ("received-entrust".equals(form.code())) {
            return receivedEntrustFormRequest(form);
        }
        if ("preliminary-survey".equals(form.code())) {
            return preliminarySurveyFormRequest(form);
        }
        if ("payment-notice".equals(form.code())) {
            return paymentNoticeFormRequest(form);
        }
        if ("quality-control".equals(form.code())) {
            return qualityControlFormRequest(form);
        }
        if ("field-survey".equals(form.code())) {
            return fieldSurveyFormRequest(form);
        }
        if ("reject-acceptance".equals(form.code())) {
            return rejectAcceptanceFormRequest(form);
        }
        if ("material-receive-return".equals(form.code())) {
            return materialReceiveReturnFormRequest(form);
        }
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(List.of(
                        Map.of("field", "caseNo", "label", "案件编号", "type", "text", "required", true),
                        Map.of("field", "handlerOpinion", "label", "办理意见", "type", "textarea", "required", false),
                        Map.of("field", "result", "label", "办理结果", "type", "select", "options", List.of("通过", "退回", "终止", "归档"))
                )),
                toJson(Map.of("layout", "two-column", "sections", List.of("基础信息", "输入文件", "输出文件", "办理意见"))),
                toJson(Map.of("requiredInputs", form.inputFiles(), "requiredOutputs", form.outputFiles())),
                toJson(Map.of("roles", platformCatalogService.judicialCatalog().dedicatedRoles(), "dataScope", "case")),
                toJson(Map.of("autoArchive", true, "fileVersioning", true)),
                toJson(Map.of("versionedArtifacts", form.versionedArtifacts())),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles())),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", form.alias() == null || form.alias().isBlank() ? form.name() : form.alias()),
                        Map.of("type", "archive", "text", "节点完成后按案件号自动归档，文件预览/下载写入审计日志")
                ))
        );
    }

    private WorkflowDesignRequest toWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        if ("received-entrust".equals(workflow.code())) {
            return receivedEntrustWorkflowRequest(workflow);
        }
        if ("preliminary-survey".equals(workflow.code())) {
            return preliminarySurveyWorkflowRequest(workflow);
        }
        if ("payment-notice".equals(workflow.code())) {
            return paymentNoticeWorkflowRequest(workflow);
        }
        if ("quality-control".equals(workflow.code())) {
            return qualityControlWorkflowRequest(workflow);
        }
        if ("field-survey".equals(workflow.code())) {
            return fieldSurveyWorkflowRequest(workflow);
        }
        if ("reject-acceptance".equals(workflow.code())) {
            return rejectAcceptanceWorkflowRequest(workflow);
        }
        if ("material-receive-return".equals(workflow.code())) {
            return materialReceiveReturnWorkflowRequest(workflow);
        }
        List<WorkflowNodeRequest> nodes = new ArrayList<>();
        List<WorkflowTransitionRequest> transitions = new ArrayList<>();
        nodes.add(new WorkflowNodeRequest("START", "开始", "start", "single", "PROCESSING",
                null, null, null, 0, 0,
                toJson(Map.of("entryMode", workflow.entryMode(), "archiveRequired", false)),
                "{}", "{}", "{}", 0, 1));

        String previous = "START";
        int sort = 10;
        List<String> roles = workflow.roles().isEmpty() ? List.of("项目负责人") : workflow.roles();
        for (int index = 0; index < roles.size(); index++) {
            String role = roles.get(index);
            String nodeCode = "N" + String.format(java.util.Locale.ROOT, "%02d", index + 1);
            nodes.add(new WorkflowNodeRequest(nodeCode, role + "办理", "task", "candidate", "PROCESSING",
                    null, null, role, 1, 48,
                    toJson(Map.of("archiveRequired", true, "inputFiles", List.of(), "outputFiles", List.of())),
                    toJson(Map.of("type", "role", "roleName", role)),
                    toJson(Map.of("formCode", workflow.formCode(), "required", true)),
                    toJson(Map.of("editable", true, "download", true, "preview", true)),
                    sort, 1));
            transitions.add(new WorkflowTransitionRequest(previous, nodeCode, "APPROVE", previous.equals("START") ? "发起" : "通过",
                    0, 0, null, toJson(Map.of("archiveOnLeave", !previous.equals("START"))), 1, sort));
            if (!previous.equals("START")) {
                transitions.add(new WorkflowTransitionRequest(nodeCode, previous, "RETURN", "退回上一节点",
                        1, 1, null, toJson(Map.of("returnPath", "previous", "preserveArchiveVersion", true)), 1, sort + 1));
            }
            previous = nodeCode;
            sort += 10;
        }

        nodes.add(new WorkflowNodeRequest("ARCHIVE", "归档", "task", "candidate", "ARCHIVED",
                null, null, "档案管理员", 1, 72,
                toJson(Map.of("archiveRequired", true, "finalArchive", true)),
                toJson(Map.of("type", "role", "roleName", "档案管理员")),
                toJson(Map.of("formCode", "archive", "required", true)),
                toJson(Map.of("editable", true, "download", true, "preview", true)),
                sort, 1));
        nodes.add(new WorkflowNodeRequest("END", "流程结束", "end", "single", "COMPLETED",
                null, null, null, 0, 0,
                toJson(Map.of("archiveRequired", false)), "{}", "{}", "{}", sort + 10, 1));
        transitions.add(new WorkflowTransitionRequest(previous, "ARCHIVE", "APPROVE", "进入归档",
                0, 1, null, toJson(Map.of("archiveOnLeave", true, "nextFlows", workflow.nextFlows())), 1, sort));
        transitions.add(new WorkflowTransitionRequest("ARCHIVE", "END", "APPROVE", "归档完成",
                0, 1, null, toJson(Map.of("archiveOnLeave", true, "caseCompleted", true)), 1, sort + 10));
        transitions.add(new WorkflowTransitionRequest("ARCHIVE", previous, "RETURN", "归档退回",
                1, 1, null, toJson(Map.of("returnPath", "previous", "preserveArchiveVersion", true)), 1, sort + 11));

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定需求目录导入：" + workflow.entryMode(),
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "roles", workflow.roles(),
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest receivedEntrustFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("serialNo", "流水号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("initiatorName", "发起人", "user", "流程基础", true, true),
                field("initiatedDate", "发起日期", "date", "流程基础", true, true),
                field("projectNo", "项目编号", "text", "流程基础", false, false),
                field("expressNo", "快递单号", "text", "委托信息", false, false),
                field("receivedDate", "收件日期", "date", "委托信息", true, false),
                field("filingDate", "立案日期", "date", "委托信息", false, false),
                field("clientName", "委托人", "text", "委托信息", true, false),
                field("caseNo", "案件号", "text", "委托信息", true, false),
                field("undertakingLegalPerson", "承办法人", "text", "委托信息", false, false),
                field("institutionSelectionMethod", "确定机构方式", "select", "委托信息", false, false,
                        List.of("随机", "指定", "协商", "其他")),
                field("institutionSelectionTime", "确定机构时间", "datetime", "委托信息", false, false),
                field("appraisalCategory", "鉴定类别", "select", "案件信息", true, false,
                        List.of("工程造价", "质量鉴定", "资产评估", "其他")),
                field("applicantName", "原告/申请人", "text", "案件信息", false, false),
                field("respondentName", "被告/被申请人", "text", "案件信息", false, false),
                field("urgencyLevel", "项目紧急程度", "select", "案件信息", true, false,
                        List.of("普通", "紧急", "特急")),
                field("caseChannel", "线上/线下", "select", "案件信息", true, false,
                        List.of("线上", "线下")),
                field("projectAmount", "项目金额", "number", "案件信息", false, false),
                field("appraisalMatter", "鉴定事项", "textarea", "案件信息", true, false),
                field("entrustAccepted", "委托审查是否受理", "boolean", "受理决策", true, false),
                field("preliminarySurveyRequired", "是否进行初步勘验", "boolean", "受理决策", true, false),
                field("materialReceiveRequired", "是否同步收案员材料接收", "boolean", "受理决策", false, false),
                field("departmentHeadId", "部门负责人", "user", "受理决策", true, false),
                field("projectLeaderId", "指定项目负责人", "user", "受理决策", true, false),
                field("projectAssistantId", "指定项目辅助人", "user", "受理决策", true, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "委托信息", "案件信息", "附件", "受理决策", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "duplicateAttachmentPolicy", "reject"
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "受理决策", Map.of("roles", List.of("部门负责人", "项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-${clientName}",
                        "branchFields", List.of("entrustAccepted", "preliminarySurveyRequired", "materialReceiveRequired")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-',clientName)",
                        "autoArchiveTitle", "concat(caseNo,'/收到委托书/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "reject")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "流程名称由案件号、委托人等字段动态生成"),
                        Map.of("type", "validation", "text", "转交前校验必填字段、委托附件和下一步条件；附件不得重复"),
                        Map.of("type", "archive", "text", "每个节点完成时表单、附件、意见和流程日志按案件号自动归档")
                ))
        );
    }

    private WorkflowDesignRequest receivedEntrustWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("INIT_FILL", "发起者填写委托信息", "task", "single", null, 1, 24, true, workflow.formCode(), 10),
                node("CLERK_REGISTER", "收案员登记", "task", "candidate", "收案员", 1, 24, true, workflow.formCode(), 20),
                node("DEPT_REVIEW", "部门负责人审阅", "task", "candidate", "部门负责人", 1, 48, true, workflow.formCode(), 30),
                node("PROJECT_DECISION", "项目负责人决策", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 40),
                node("ASSISTANT_NOTICE", "告知项目辅助人", "task", "candidate", "项目辅助人", 0, 24, true, workflow.formCode(), 50),
                node("MATERIAL_RECEIVE", "收案员材料接收", "task", "candidate", "收案员", 1, 24, true, workflow.formCode(), 60),
                node("PRELIMINARY_SURVEY", "进入初步勘验", "task", "candidate", "项目负责人", 1, 24, true, "preliminary-survey", 70),
                node("PAYMENT_NOTICE", "进入发交费通知书及相关函件", "task", "candidate", "项目负责人", 1, 24, true, "payment-notice", 80),
                node("REJECT_ACCEPTANCE", "进入不予受理", "task", "candidate", "项目辅助人", 1, 24, true, "reject-acceptance", 90),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 100)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "INIT_FILL", "APPROVE", "新建流程", null, 0, 10),
                transition("INIT_FILL", "CLERK_REGISTER", "APPROVE", "转交收案员登记", null, 1, 20),
                transition("CLERK_REGISTER", "DEPT_REVIEW", "APPROVE", "转交部门负责人审阅", null, 1, 30),
                transition("DEPT_REVIEW", "PROJECT_DECISION", "APPROVE", "受理并指定项目负责人", "form.entrustAccepted == true", 1, 40),
                transition("DEPT_REVIEW", "REJECT_ACCEPTANCE", "APPROVE", "不予受理", "form.entrustAccepted == false", 1, 41,
                        subflowConfig("reject-acceptance", "部门负责人确认委托审查不受理后自动关联发起")),
                transition("PROJECT_DECISION", "ASSISTANT_NOTICE", "APPROVE", "告知项目辅助人", null, 0, 50),
                transition("PROJECT_DECISION", "MATERIAL_RECEIVE", "APPROVE", "同步收案员材料接收", "form.materialReceiveRequired == true", 1, 51),
                transition("PROJECT_DECISION", "PRELIMINARY_SURVEY", "APPROVE", "进入初步勘验", "form.preliminarySurveyRequired == true", 1, 52,
                        subflowConfig("preliminary-survey", "项目负责人确认需要初步勘验后自动关联发起")),
                transition("PROJECT_DECISION", "PAYMENT_NOTICE", "APPROVE", "进入发交费通知", "form.preliminarySurveyRequired == false", 1, 53,
                        subflowConfig("payment-notice", "项目负责人确认无需初步勘验后自动关联发起")),
                transition("CLERK_REGISTER", "INIT_FILL", "RETURN", "退回发起者补正", null, 1, 60),
                transition("DEPT_REVIEW", "CLERK_REGISTER", "RETURN", "退回收案员登记", null, 1, 61),
                transition("PROJECT_DECISION", "DEPT_REVIEW", "RETURN", "退回部门负责人审阅", null, 1, 62),
                transition("ASSISTANT_NOTICE", "END", "COMPLETE", "通知完成", null, 1, 70),
                transition("MATERIAL_RECEIVE", "END", "COMPLETE", "材料接收完成", null, 1, 71),
                transition("PRELIMINARY_SURVEY", "END", "COMPLETE", "初步勘验子流程已触发", null, 1, 72),
                transition("PAYMENT_NOTICE", "END", "COMPLETE", "缴费通知子流程已触发", null, 1, 73),
                transition("REJECT_ACCEPTANCE", "END", "COMPLETE", "不予受理子流程已触发", null, 1, 74)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：收到委托书",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-${clientName}",
                        "parallelBranchNode", "PROJECT_DECISION",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest preliminarySurveyFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("surveyDate", "初步勘验日期", "date", "勘验安排", true, false),
                field("surveyLocation", "勘验地点", "text", "勘验安排", true, false),
                field("surveyPlanUploaded", "现场工作方案已上传", "boolean", "勘验安排", true, false),
                field("equipmentOutboundRecorded", "设备出入库记录已登记", "boolean", "设备记录", true, false),
                field("equipmentUsageRecorded", "设备使用记录已登记", "boolean", "设备记录", true, false),
                field("surveySummary", "初步勘验情况", "textarea", "勘验结论", true, false),
                field("appraisalConditionMet", "是否具备鉴定条件", "boolean", "勘验结论", true, false),
                field("nextRecommendation", "下一步建议", "select", "勘验结论", true, false,
                        List.of("发交费通知书及相关函件", "终止鉴定")),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "勘验安排", "设备记录", "勘验结论", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "appraisalConditionMet == true", "then", "nextRecommendation == '发交费通知书及相关函件'"),
                                Map.of("if", "appraisalConditionMet == false", "then", "nextRecommendation == '终止鉴定'")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "勘验结论", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-初步勘验",
                        "branchFields", List.of("appraisalConditionMet", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-初步勘验')",
                        "autoArchiveTitle", "concat(caseNo,'/初步勘验/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人上传现场工作方案并完成设备记录，项目负责人判断是否具备鉴定条件"),
                        Map.of("type", "validation", "text", "提交审核前必须补齐现场工作方案、设备出入库记录和设备使用记录"),
                        Map.of("type", "archive", "text", "勘验结论、设备记录和附件在节点完成后自动归档，为后续缴费或终止流程提供依据")
                ))
        );
    }

    private WorkflowDesignRequest preliminarySurveyWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_PREPARE", "项目辅助人上传方案与设备记录", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核勘验结论", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("PAYMENT_NOTICE", "进入发交费通知", "task", "candidate", "项目负责人", 1, 24, true, "payment-notice", 30),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 40),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 50)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_PREPARE", "APPROVE", "进入初步勘验", null, 0, 10),
                transition("ASSISTANT_PREPARE", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "PAYMENT_NOTICE", "APPROVE", "具备鉴定条件，转发交费通知", "form.appraisalConditionMet == true", 1, 30,
                        subflowConfig("payment-notice", "初步勘验确认具备鉴定条件后自动进入发交费通知书及相关函件")),
                transition("PROJECT_REVIEW", "TERMINATE_APPRAISAL", "APPROVE", "不具备鉴定条件，转终止鉴定", "form.appraisalConditionMet == false", 1, 31,
                        subflowConfig("terminate-appraisal", "初步勘验确认不具备鉴定条件后自动进入终止鉴定")),
                transition("PROJECT_REVIEW", "ASSISTANT_PREPARE", "RETURN", "退回项目辅助人补充方案与记录", null, 1, 40),
                transition("PAYMENT_NOTICE", "END", "COMPLETE", "缴费通知子流程已触发", null, 1, 50),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 51)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：初步勘验",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-初步勘验",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest paymentNoticeFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("letterDraftCompleted", "缴费函件草稿已编制", "boolean", "函件编制", true, false),
                field("letterType", "函件类型", "select", "函件编制", true, false,
                        List.of("交费通知书", "补充函件", "情况说明函", "其他")),
                field("letterSummary", "函件内容摘要", "textarea", "函件编制", true, false),
                field("sealRequired", "是否需要用章", "boolean", "审核与用章", true, false),
                field("sealedDocumentUploaded", "盖章函件已回传", "boolean", "审核与用章", true, false),
                field("sendDate", "函件寄送日期", "date", "寄送与确认", false, false),
                field("paymentReceived", "是否已缴费", "boolean", "寄送与确认", true, false),
                field("paymentConfirmedDate", "缴费确认日期", "date", "寄送与确认", false, false),
                field("nextRecommendation", "下一步建议", "select", "寄送与确认", true, false,
                        List.of("编制内部质量控制文件", "终止鉴定")),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "函件编制", "审核与用章", "寄送与确认", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "paymentReceived == true", "then", "nextRecommendation == '编制内部质量控制文件'"),
                                Map.of("if", "paymentReceived == false", "then", "nextRecommendation == '终止鉴定'"),
                                Map.of("if", "sealRequired == true", "then", "sealedDocumentUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "审核与用章", Map.of("roles", List.of("项目负责人", "档案管理员")),
                                "寄送与确认", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-发交费通知",
                        "branchFields", List.of("sealRequired", "paymentReceived", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-发交费通知')",
                        "autoArchiveTitle", "concat(caseNo,'/发交费通知/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制缴费函件，项目负责人审核后进入用章及盖章件回传，再确认是否缴费"),
                        Map.of("type", "validation", "text", "如选择需要用章，则提交缴费确认前必须回传盖章函件"),
                        Map.of("type", "archive", "text", "缴费函件草稿、盖章件、缴费确认和寄送记录在各节点完成后自动归档")
                ))
        );
    }

    private WorkflowDesignRequest paymentNoticeWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_DRAFT", "项目辅助人编制缴费函件", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核函件", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("SEAL_APPLICATION", "进入用章流程", "task", "candidate", "项目负责人", 1, 24, true, "seal-application", 30),
                node("ARCHIVE_UPLOAD", "档案管理员回传盖章件", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 40),
                node("PAYMENT_CONFIRM", "项目负责人确认是否缴费", "task", "candidate", "项目负责人", 1, 72, true, workflow.formCode(), 50),
                node("QUALITY_CONTROL", "进入编制内部质量控制文件", "task", "candidate", "项目负责人", 1, 24, true, "quality-control", 60),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 70),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 80)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_DRAFT", "APPROVE", "进入发交费通知", null, 0, 10),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "SEAL_APPLICATION", "APPROVE", "发起用章流程", "form.sealRequired == true", 1, 30,
                        subflowConfig("seal-application", "缴费函件审核通过且需要用章后自动进入用章流程")),
                transition("PROJECT_REVIEW", "ARCHIVE_UPLOAD", "APPROVE", "无需用章，直接回传函件", "form.sealRequired == false", 1, 31),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改函件", null, 1, 32),
                transition("SEAL_APPLICATION", "ARCHIVE_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 40),
                transition("ARCHIVE_UPLOAD", "PAYMENT_CONFIRM", "APPROVE", "转交项目负责人确认缴费", null, 1, 50),
                transition("PAYMENT_CONFIRM", "QUALITY_CONTROL", "APPROVE", "已缴费，进入编制内部质量控制文件", "form.paymentReceived == true", 1, 60,
                        subflowConfig("quality-control", "项目负责人确认已缴费后自动进入编制内部质量控制文件")),
                transition("PAYMENT_CONFIRM", "TERMINATE_APPRAISAL", "APPROVE", "未缴费，进入终止鉴定", "form.paymentReceived == false", 1, 61,
                        subflowConfig("terminate-appraisal", "项目负责人确认未缴费后自动进入终止鉴定")),
                transition("PAYMENT_CONFIRM", "ARCHIVE_UPLOAD", "RETURN", "退回档案管理员补充盖章件", null, 1, 62),
                transition("QUALITY_CONTROL", "END", "COMPLETE", "内部质量控制子流程已触发", null, 1, 70),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 71)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：发交费通知书及相关函件",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-发交费通知",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest qualityControlFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("qualityFileDraftCompleted", "内部质量控制文件草稿已编制", "boolean", "质量控制文件", true, false),
                field("qualityFileSummary", "内部质量控制文件摘要", "textarea", "质量控制文件", true, false),
                field("formatType", "格式类型", "select", "F类项目判断", true, false,
                        List.of("中心格式", "非中心格式")),
                field("contractAmount", "合同金额", "number", "F类项目判断", true, false),
                field("fClassProject", "是否F类项目", "boolean", "F类项目判断", true, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", true, false),
                field("projectReviewRoute", "项目负责人审核后流向", "select", "项目负责人审核", true, false,
                        List.of("部门负责人审核", "进入用章", "退回修改")),
                field("projectReviewOpinion", "项目负责人审核意见", "textarea", "项目负责人审核", false, false),
                field("departmentReviewPassed", "部门负责人审核通过", "boolean", "部门负责人审核", false, false),
                field("departmentReviewOpinion", "部门负责人审核意见", "textarea", "部门负责人审核", false, false),
                field("sealRequired", "是否需要用章", "boolean", "用章与回传", true, false),
                field("sealedQualityFileUploaded", "内部质量控制文件盖章件已上传", "boolean", "用章与回传", true, false),
                field("nextRecommendation", "下一步建议", "select", "后续流程", true, false,
                        List.of("现场勘验", "材料接收与返还", "鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定")),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "质量控制文件", "F类项目判断", "项目负责人审核", "部门负责人审核", "用章与回传", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "formatType == '中心格式' && contractAmount > 500000", "then", "fClassProject == true"),
                                Map.of("if", "formatType == '非中心格式' && contractAmount > 250000", "then", "fClassProject == true"),
                                Map.of("if", "fClassProject == true && projectReviewPassed == true", "then", "projectReviewRoute == '部门负责人审核'"),
                                Map.of("if", "fClassProject == false && projectReviewPassed == true", "then", "projectReviewRoute == '进入用章'"),
                                Map.of("if", "projectReviewPassed == false", "then", "projectReviewRoute == '退回修改'"),
                                Map.of("if", "sealRequired == true", "then", "sealedQualityFileUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "F类项目判断", Map.of("roles", List.of("项目负责人")),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "部门负责人审核", Map.of("roles", List.of("部门负责人")),
                                "用章与回传", Map.of("roles", List.of("档案管理员"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-编制内部质量控制文件",
                        "branchFields", List.of("fClassProject", "projectReviewPassed", "projectReviewRoute", "departmentReviewPassed", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-编制内部质量控制文件')",
                        "fClassProject", "(formatType == '中心格式' && contractAmount > 500000) || (formatType == '非中心格式' && contractAmount > 250000)",
                        "autoArchiveTitle", "concat(caseNo,'/编制内部质量控制文件/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制内部质量控制文件，项目负责人审核并判断中心格式、合同金额和F类项目"),
                        Map.of("type", "validation", "text", "中心格式金额大于50万或非中心格式金额大于25万时判定F类项目，F类必须经部门负责人审核"),
                        Map.of("type", "archive", "text", "质量控制文件草稿、审核意见、盖章件和后续流程选择在节点完成后自动归档")
                ))
        );
    }

    private WorkflowDesignRequest qualityControlWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_DRAFT", "项目辅助人编制内部质量控制文件", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核并判定F类", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("DEPARTMENT_REVIEW", "部门负责人审核F类项目", "task", "candidate", "部门负责人", 1, 48, true, workflow.formCode(), 30),
                node("SEAL_APPLICATION", "进入用章流程", "task", "candidate", "项目负责人", 1, 24, true, "seal-application", 40),
                node("SEALED_FILE_UPLOAD", "档案管理员回传盖章件", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 50),
                node("NEXT_FLOW_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 60),
                node("FIELD_SURVEY", "进入现场勘验", "task", "candidate", "项目负责人", 1, 24, true, "field-survey", 70),
                node("MATERIAL_RECEIVE_RETURN", "进入材料接收与返还", "task", "candidate", "项目负责人", 1, 24, true, "material-receive-return", 80),
                node("DRAFT_OPINION_REVIEW", "进入征求意见稿送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "draft-opinion-review", 90),
                node("FINAL_OPINION_REVIEW", "进入鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 100),
                node("REFUND", "进入退费", "task", "candidate", "项目负责人", 1, 24, true, "refund", 110),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 120),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 130)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_DRAFT", "APPROVE", "进入内部质量控制文件编制", null, 0, 10),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "DEPARTMENT_REVIEW", "APPROVE", "F类项目，转部门负责人审核", "form.projectReviewRoute == '部门负责人审核'", 1, 30),
                transition("PROJECT_REVIEW", "SEAL_APPLICATION", "APPROVE", "非F类项目，进入用章", "form.projectReviewRoute == '进入用章'", 1, 31,
                        subflowConfig("seal-application", "内部质量控制文件审核通过后自动进入用章流程")),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改质量控制文件", "form.projectReviewRoute == '退回修改'", 1, 32),
                transition("DEPARTMENT_REVIEW", "SEAL_APPLICATION", "APPROVE", "部门负责人审核通过，进入用章", "form.departmentReviewPassed == true", 1, 40,
                        subflowConfig("seal-application", "F类项目经部门负责人审核通过后自动进入用章流程")),
                transition("DEPARTMENT_REVIEW", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核F类判断", "form.departmentReviewPassed == false", 1, 41),
                transition("SEAL_APPLICATION", "SEALED_FILE_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 50),
                transition("SEALED_FILE_UPLOAD", "NEXT_FLOW_DECISION", "APPROVE", "转交项目负责人确认后续流程", null, 1, 60),
                transition("SEALED_FILE_UPLOAD", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核质量控制文件", null, 1, 61),
                transition("NEXT_FLOW_DECISION", "FIELD_SURVEY", "APPROVE", "进入现场勘验", "form.nextRecommendation == '现场勘验'", 1, 70,
                        subflowConfig("field-survey", "内部质量控制完成后选择进入现场勘验")),
                transition("NEXT_FLOW_DECISION", "MATERIAL_RECEIVE_RETURN", "APPROVE", "进入材料接收与返还", "form.nextRecommendation == '材料接收与返还'", 1, 71,
                        subflowConfig("material-receive-return", "内部质量控制完成后选择进入材料接收与返还")),
                transition("NEXT_FLOW_DECISION", "DRAFT_OPINION_REVIEW", "APPROVE", "进入征求意见稿送审稿编制", "form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", 1, 72,
                        subflowConfig("draft-opinion-review", "内部质量控制完成后选择进入征求意见稿送审稿编制")),
                transition("NEXT_FLOW_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "进入鉴定意见书送审稿编制", "form.nextRecommendation == '鉴定意见书送审稿编制'", 1, 73,
                        subflowConfig("final-opinion-review", "内部质量控制完成后选择进入鉴定意见书送审稿编制")),
                transition("NEXT_FLOW_DECISION", "REFUND", "APPROVE", "进入退费", "form.nextRecommendation == '退费'", 1, 74,
                        subflowConfig("refund", "内部质量控制完成后选择进入退费")),
                transition("NEXT_FLOW_DECISION", "TERMINATE_APPRAISAL", "APPROVE", "进入终止鉴定", "form.nextRecommendation == '终止鉴定'", 1, 75,
                        subflowConfig("terminate-appraisal", "内部质量控制完成后选择进入终止鉴定")),
                transition("FIELD_SURVEY", "END", "COMPLETE", "现场勘验子流程已触发", null, 1, 80),
                transition("MATERIAL_RECEIVE_RETURN", "END", "COMPLETE", "材料接收与返还子流程已触发", null, 1, 81),
                transition("DRAFT_OPINION_REVIEW", "END", "COMPLETE", "征求意见稿送审稿编制子流程已触发", null, 1, 82),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 83),
                transition("REFUND", "END", "COMPLETE", "退费子流程已触发", null, 1, 84),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 85)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：编制内部质量控制文件",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-编制内部质量控制文件",
                        "fClassRule", "中心格式且金额大于50万或非中心格式且金额大于25万判定F类，F类需部门负责人审核",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest fieldSurveyFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("technicalLeaderId", "技术负责人", "user", "流程基础", false, false),
                field("departmentHeadId", "部门负责人", "user", "流程基础", false, false),
                field("surveyDate", "现场勘验日期", "date", "勘验安排", true, false),
                field("surveyLocation", "勘验地点", "text", "勘验安排", true, false),
                field("surveyPlanUploaded", "现场工作方案已上传", "boolean", "勘验安排", true, false),
                field("fieldRecordUploaded", "勘验记录已上传", "boolean", "勘验记录", true, false),
                field("equipmentUsageRecorded", "设备使用记录已登记", "boolean", "设备记录", true, false),
                field("equipmentReturnRecorded", "设备归还记录已登记", "boolean", "设备记录", true, false),
                field("projectAmount", "项目金额", "number", "审核规则", true, false),
                field("majorAmountProject", "项目金额是否大于15万", "boolean", "审核规则", true, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", true, false),
                field("projectReviewRoute", "项目负责人审核后流向", "select", "项目负责人审核", true, false,
                        List.of("技术负责人审核", "确认后续流程", "退回修改")),
                field("technicalReviewPassed", "技术负责人审核通过", "boolean", "技术负责人审核", false, false),
                field("departmentReviewPassed", "部门负责人审核通过", "boolean", "部门负责人审核", false, false),
                field("nextRecommendation", "下一步建议", "select", "后续流程", true, false,
                        List.of("材料接收与返还", "鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定")),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "勘验安排", "勘验记录", "设备记录", "审核规则", "项目负责人审核", "技术负责人审核", "部门负责人审核", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "projectAmount > 150000", "then", "majorAmountProject == true"),
                                Map.of("if", "majorAmountProject == true && projectReviewPassed == true", "then", "projectReviewRoute == '技术负责人审核'"),
                                Map.of("if", "majorAmountProject == false && projectReviewPassed == true", "then", "projectReviewRoute == '确认后续流程'"),
                                Map.of("if", "projectReviewPassed == false", "then", "projectReviewRoute == '退回修改'"),
                                Map.of("if", "projectReviewPassed == true", "then", "fieldRecordUploaded == true"),
                                Map.of("if", "projectReviewPassed == true", "then", "equipmentUsageRecorded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "技术负责人审核", Map.of("roles", List.of("技术负责人")),
                                "部门负责人审核", Map.of("roles", List.of("部门负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-现场勘验",
                        "branchFields", List.of("majorAmountProject", "projectReviewPassed", "projectReviewRoute", "technicalReviewPassed", "departmentReviewPassed", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-现场勘验')",
                        "majorAmountProject", "projectAmount > 150000",
                        "autoArchiveTitle", "concat(caseNo,'/现场勘验/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人完成现场工作方案、勘验记录和设备记录，项目负责人审核现场勘验结论"),
                        Map.of("type", "validation", "text", "项目金额大于15万时必须经项目负责人、技术负责人、部门负责人逐级审核"),
                        Map.of("type", "archive", "text", "现场工作方案、勘验记录、设备记录和审核意见在节点完成后自动归档")
                ))
        );
    }

    private WorkflowDesignRequest fieldSurveyWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_SURVEY", "项目辅助人完成现场勘验记录", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核现场勘验", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("TECHNICAL_REVIEW", "技术负责人审核现场勘验", "task", "candidate", "技术负责人", 1, 48, true, workflow.formCode(), 30),
                node("DEPARTMENT_REVIEW", "部门负责人审核现场勘验", "task", "candidate", "部门负责人", 1, 48, true, workflow.formCode(), 40),
                node("NEXT_FLOW_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 50),
                node("MATERIAL_RECEIVE_RETURN", "进入材料接收与返还", "task", "candidate", "项目负责人", 1, 24, true, "material-receive-return", 60),
                node("DRAFT_OPINION_REVIEW", "进入征求意见稿送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "draft-opinion-review", 70),
                node("FINAL_OPINION_REVIEW", "进入鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 80),
                node("REFUND", "进入退费", "task", "candidate", "项目负责人", 1, 24, true, "refund", 90),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 100),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 110)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_SURVEY", "APPROVE", "进入现场勘验", null, 0, 10),
                transition("ASSISTANT_SURVEY", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "TECHNICAL_REVIEW", "APPROVE", "金额大于15万，转技术负责人审核", "form.projectReviewRoute == '技术负责人审核'", 1, 30),
                transition("PROJECT_REVIEW", "NEXT_FLOW_DECISION", "APPROVE", "金额不超过15万，确认后续流程", "form.projectReviewRoute == '确认后续流程'", 1, 31),
                transition("PROJECT_REVIEW", "ASSISTANT_SURVEY", "RETURN", "退回项目辅助人补充勘验记录", "form.projectReviewRoute == '退回修改'", 1, 32),
                transition("TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "APPROVE", "技术负责人审核通过，转部门负责人审核", "form.technicalReviewPassed == true", 1, 40),
                transition("TECHNICAL_REVIEW", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", "form.technicalReviewPassed == false", 1, 41),
                transition("DEPARTMENT_REVIEW", "NEXT_FLOW_DECISION", "APPROVE", "部门负责人审核通过，确认后续流程", "form.departmentReviewPassed == true", 1, 50),
                transition("DEPARTMENT_REVIEW", "TECHNICAL_REVIEW", "RETURN", "退回技术负责人复核", "form.departmentReviewPassed == false", 1, 51),
                transition("NEXT_FLOW_DECISION", "MATERIAL_RECEIVE_RETURN", "APPROVE", "进入材料接收与返还", "form.nextRecommendation == '材料接收与返还'", 1, 60,
                        subflowConfig("material-receive-return", "现场勘验完成后选择进入材料接收与返还")),
                transition("NEXT_FLOW_DECISION", "DRAFT_OPINION_REVIEW", "APPROVE", "进入征求意见稿送审稿编制", "form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", 1, 61,
                        subflowConfig("draft-opinion-review", "现场勘验完成后选择进入征求意见稿送审稿编制")),
                transition("NEXT_FLOW_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "进入鉴定意见书送审稿编制", "form.nextRecommendation == '鉴定意见书送审稿编制'", 1, 62,
                        subflowConfig("final-opinion-review", "现场勘验完成后选择进入鉴定意见书送审稿编制")),
                transition("NEXT_FLOW_DECISION", "REFUND", "APPROVE", "进入退费", "form.nextRecommendation == '退费'", 1, 63,
                        subflowConfig("refund", "现场勘验完成后选择进入退费")),
                transition("NEXT_FLOW_DECISION", "TERMINATE_APPRAISAL", "APPROVE", "进入终止鉴定", "form.nextRecommendation == '终止鉴定'", 1, 64,
                        subflowConfig("terminate-appraisal", "现场勘验完成后选择进入终止鉴定")),
                transition("MATERIAL_RECEIVE_RETURN", "END", "COMPLETE", "材料接收与返还子流程已触发", null, 1, 70),
                transition("DRAFT_OPINION_REVIEW", "END", "COMPLETE", "征求意见稿送审稿编制子流程已触发", null, 1, 71),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 72),
                transition("REFUND", "END", "COMPLETE", "退费子流程已触发", null, 1, 73),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 74)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：现场勘验",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-现场勘验",
                        "amountReviewRule", "项目金额大于15万需项目负责人、技术负责人、部门负责人逐级审核",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest rejectAcceptanceFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("rejectionReason", "不予受理原因", "textarea", "通知编制", true, false),
                field("noticeDraftCompleted", "不予受理通知书已编制", "boolean", "通知编制", true, false),
                field("noticeSummary", "通知书内容摘要", "textarea", "通知编制", true, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", true, false),
                field("reviewOpinion", "审核意见", "textarea", "项目负责人审核", false, false),
                field("sealRequired", "是否需要用章", "boolean", "用章与回传", true, false),
                field("sealedNoticeUploaded", "盖章通知书扫描件已上传", "boolean", "用章与回传", true, false),
                field("deliveryMethod", "送达方式", "select", "送达与归档", true, false,
                        List.of("邮寄", "现场领取", "电子送达", "其他")),
                field("deliveryDate", "送达日期", "date", "送达与归档", false, false),
                field("archiveConfirmed", "归档材料已确认", "boolean", "送达与归档", true, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "通知编制", "项目负责人审核", "用章与回传", "送达与归档", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "projectReviewPassed == true", "then", "noticeDraftCompleted == true"),
                                Map.of("if", "sealRequired == true", "then", "sealedNoticeUploaded == true"),
                                Map.of("if", "archiveConfirmed == true", "then", "sealedNoticeUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "用章与回传", Map.of("roles", List.of("档案管理员", "综合业务部")),
                                "送达与归档", Map.of("roles", List.of("档案管理员"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-不予受理",
                        "branchFields", List.of("projectReviewPassed", "sealRequired", "archiveConfirmed")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-不予受理')",
                        "autoArchiveTitle", "concat(caseNo,'/不予受理/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制不予受理通知书，项目负责人审核后进入用章、回传、送达和归档"),
                        Map.of("type", "validation", "text", "审核通过前必须完成通知书草稿；需要用章时必须上传盖章通知书扫描件"),
                        Map.of("type", "archive", "text", "不予受理原因、通知书扫描件、送达记录和归档确认在节点完成后自动归档")
                ))
        );
    }

    private WorkflowDesignRequest rejectAcceptanceWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_DRAFT", "项目辅助人编制不予受理通知书", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核不予受理通知书", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("SEAL_APPLICATION", "进入用章流程", "task", "candidate", "综合业务部", 1, 24, true, "seal-application", 30),
                node("SEALED_NOTICE_UPLOAD", "档案管理员回传盖章通知书", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 40),
                node("DELIVERY_ARCHIVE", "档案管理员送达并归档", "task", "candidate", "档案管理员", 1, 72, true, workflow.formCode(), 50),
                node("ARCHIVE", "进入归档", "task", "candidate", "档案管理员", 1, 24, true, "archive", 60),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 70)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_DRAFT", "APPROVE", "进入不予受理", null, 0, 10),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "SEAL_APPLICATION", "APPROVE", "审核通过，发起用章", "form.projectReviewPassed == true", 1, 30,
                        subflowConfig("seal-application", "不予受理通知书审核通过且需要用章后自动进入用章流程")),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改通知书", "form.projectReviewPassed == false", 1, 31),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人补充材料", null, 1, 32),
                transition("SEAL_APPLICATION", "SEALED_NOTICE_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 40),
                transition("SEALED_NOTICE_UPLOAD", "DELIVERY_ARCHIVE", "APPROVE", "转交送达与归档确认", null, 1, 50),
                transition("SEALED_NOTICE_UPLOAD", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", null, 1, 51),
                transition("DELIVERY_ARCHIVE", "ARCHIVE", "APPROVE", "送达完成，进入归档", "form.archiveConfirmed == true", 1, 60,
                        subflowConfig("archive", "不予受理通知书送达并确认归档材料后自动进入归档流程")),
                transition("DELIVERY_ARCHIVE", "SEALED_NOTICE_UPLOAD", "RETURN", "退回补充盖章通知书或送达记录", null, 1, 61),
                transition("ARCHIVE", "END", "COMPLETE", "归档子流程已触发", null, 1, 70)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：不予受理",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-不予受理",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest materialReceiveReturnFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("materialSource", "材料来源", "text", "材料接收", true, false),
                field("requireSupplementaryMaterial", "是否补充材料", "boolean", "材料接收", true, false),
                field("supplementaryNotice", "补材通知", "textarea", "材料接收", false, false),
                field("materialDetails", "材料名称/数量/介质", "textarea", "材料接收", true, false),
                field("receiveDate", "接收时间", "date", "材料接收", true, false),
                field("storageLocation", "存放地址", "text", "材料保管", true, false),
                field("requireReturn", "是否返还", "boolean", "材料保管", true, false),
                field("storageStatus", "保管状态", "select", "材料保管", true, false,
                        List.of("正常", "损毁", "灭失")),
                field("returnReceiver", "返还接收人", "text", "材料返还", false, false),
                field("returnDate", "返还时间", "date", "材料返还", false, false),
                field("nextRecommendation", "下一步建议", "select", "后续流程", true, false,
                        List.of("鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定", "归档")),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(),
                form.name(),
                "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of(
                        "layout", "grouped",
                        "groups", List.of("流程基础", "材料接收", "材料保管", "材料返还", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "requireReturn == true", "then", "returnReceiver != null"),
                                Map.of("if", "requireReturn == true", "then", "returnDate != null")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "材料接收", Map.of("roles", List.of("项目负责人", "项目辅助人")),
                                "材料保管", Map.of("roles", List.of("档案管理员")),
                                "材料返还", Map.of("roles", List.of("档案管理员")),
                                "后续流程", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-材料接收与返还",
                        "branchFields", List.of("requireSupplementaryMaterial", "requireReturn", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-材料接收与返还')",
                        "autoArchiveTitle", "concat(caseNo,'/材料接收与返还/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目负责人确认材料需求，项目辅助人登记材料，档案管理员负责接收、保管与返还"),
                        Map.of("type", "validation", "text", "如果需要返还，则必须填写返还接收人和返还时间"),
                        Map.of("type", "archive", "text", "材料来源、明细、补材通知、保管状态及返还记录在各节点完成后自动归档")
                ))
        );
    }

    private WorkflowDesignRequest materialReceiveReturnWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_CONFIRM", "项目负责人确认材料需求", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 10),
                node("ASSISTANT_REGISTER", "项目辅助人登记材料", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 20),
                node("ARCHIVIST_HANDLE", "档案管理员接收保管与返还", "task", "candidate", "档案管理员", 1, 48, true, workflow.formCode(), 30),
                node("PROJECT_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 40),
                node("DRAFT_OPINION_REVIEW", "进入征求意见稿送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "draft-opinion-review", 50),
                node("FINAL_OPINION_REVIEW", "进入鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 60),
                node("REFUND", "进入退费", "task", "candidate", "项目负责人", 1, 24, true, "refund", 70),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 80),
                node("ARCHIVE", "进入归档", "task", "candidate", "档案管理员", 1, 24, true, "archive", 90),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 100)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_CONFIRM", "APPROVE", "进入材料接收与返还", null, 0, 10),
                transition("PROJECT_CONFIRM", "ASSISTANT_REGISTER", "APPROVE", "转交项目辅助人登记材料", null, 1, 20),
                transition("ASSISTANT_REGISTER", "ARCHIVIST_HANDLE", "APPROVE", "转交档案管理员接收与保管", null, 1, 30),
                transition("ASSISTANT_REGISTER", "PROJECT_CONFIRM", "RETURN", "退回项目负责人复核材料需求", null, 1, 31),
                transition("ARCHIVIST_HANDLE", "PROJECT_DECISION", "APPROVE", "转交项目负责人确认后续流程", null, 1, 40),
                transition("ARCHIVIST_HANDLE", "ASSISTANT_REGISTER", "RETURN", "退回项目辅助人重新登记材料", null, 1, 41),
                transition("PROJECT_DECISION", "DRAFT_OPINION_REVIEW", "APPROVE", "进入征求意见稿送审稿编制", "form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", 1, 50,
                        subflowConfig("draft-opinion-review", "材料处理完成后选择进入征求意见稿送审稿编制")),
                transition("PROJECT_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "进入鉴定意见书送审稿编制", "form.nextRecommendation == '鉴定意见书送审稿编制'", 1, 51,
                        subflowConfig("final-opinion-review", "材料处理完成后选择进入鉴定意见书送审稿编制")),
                transition("PROJECT_DECISION", "REFUND", "APPROVE", "进入退费", "form.nextRecommendation == '退费'", 1, 52,
                        subflowConfig("refund", "材料处理完成后选择进入退费")),
                transition("PROJECT_DECISION", "TERMINATE_APPRAISAL", "APPROVE", "进入终止鉴定", "form.nextRecommendation == '终止鉴定'", 1, 53,
                        subflowConfig("terminate-appraisal", "材料处理完成后选择进入终止鉴定")),
                transition("PROJECT_DECISION", "ARCHIVE", "APPROVE", "进入归档", "form.nextRecommendation == '归档'", 1, 54,
                        subflowConfig("archive", "材料处理完成后选择进入归档")),
                transition("PROJECT_DECISION", "ARCHIVIST_HANDLE", "RETURN", "退回档案管理员处理材料", null, 1, 55),
                transition("DRAFT_OPINION_REVIEW", "END", "COMPLETE", "征求意见稿送审稿编制子流程已触发", null, 1, 60),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 61),
                transition("REFUND", "END", "COMPLETE", "退费子流程已触发", null, 1, 62),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 63),
                transition("ARCHIVE", "END", "COMPLETE", "归档子流程已触发", null, 1, 64)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：材料接收与返还",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-材料接收与返还",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private Map<String, Object> field(String field, String label, String type, String group, boolean required, boolean readOnly) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("label", label);
        result.put("type", type);
        result.put("group", group);
        result.put("required", required);
        result.put("readOnly", readOnly);
        return result;
    }

    private Map<String, Object> field(String field, String label, String type, String group, boolean required, boolean readOnly, List<String> options) {
        Map<String, Object> result = field(field, label, type, group, required, readOnly);
        result.put("options", options);
        return result;
    }

    private WorkflowNodeRequest node(String code, String name, String type, String taskType, String role,
                                     Integer manualAssign, Integer timeoutHours, boolean archiveRequired,
                                     String formCode, int sortNo) {
        return new WorkflowNodeRequest(code, name, type, taskType, "PROCESSING",
                null, null, role, manualAssign, timeoutHours,
                toJson(Map.of("archiveRequired", archiveRequired)),
                role == null ? "{}" : toJson(Map.of("type", "role", "roleName", role)),
                formCode == null ? "{}" : toJson(Map.of("formCode", formCode, "required", true)),
                toJson(Map.of("editable", !"end".equals(type), "download", true, "preview", true)),
                sortNo, 1);
    }

    private WorkflowTransitionRequest transition(String from, String to, String actionCode, String actionName,
                                                 String condition, Integer archiveOnLeave, int sortNo) {
        return transition(from, to, actionCode, actionName, condition, archiveOnLeave, sortNo,
                Map.of("archiveOnLeave", archiveOnLeave == null ? 0 : archiveOnLeave));
    }

    private WorkflowTransitionRequest transition(String from, String to, String actionCode, String actionName,
                                                 String condition, Integer archiveOnLeave, int sortNo,
                                                 Map<String, Object> transitionConfig) {
        return new WorkflowTransitionRequest(from, to, actionCode, actionName,
                "RETURN".equals(actionCode) ? 1 : 0,
                1,
                condition,
                toJson(transitionConfig),
                1,
                sortNo);
    }

    private Map<String, Object> subflowConfig(String subflowCode, String reason) {
        return Map.of(
                "archiveOnLeave", 1,
                "launchSubflow", true,
                "subflowCode", subflowCode,
                "reason", reason
        );
    }

    private List<Map<String, Object>> toFileRules(List<String> names, String direction) {
        return names.stream()
                .map(name -> Map.<String, Object>of(
                        "code", slug(name),
                        "name", name,
                        "direction", direction,
                        "required", true,
                        "versioned", true,
                        "archive", true
                ))
                .toList();
    }

    private String slug(String text) {
        return "f_" + Integer.toHexString(text.hashCode());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
