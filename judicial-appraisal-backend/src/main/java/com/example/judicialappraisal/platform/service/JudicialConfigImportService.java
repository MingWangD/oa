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
        if ("draft-opinion-review".equals(form.code())) {
            return draftOpinionReviewFormRequest(form);
        }
        if ("final-opinion-review".equals(form.code())) {
            return finalOpinionReviewFormRequest(form);
        }
        if ("issue-opinion".equals(form.code())) {
            return issueOpinionFormRequest(form);
        }
        if ("issue-draft-opinion".equals(form.code())) {
            return issueDraftOpinionFormRequest(form);
        }
        if ("court-letter".equals(form.code())) {
            return courtLetterFormRequest(form);
        }
        if ("court-appearance".equals(form.code())) {
            return courtAppearanceFormRequest(form);
        }
        if ("withdraw-case-letter".equals(form.code())) {
            return withdrawCaseLetterFormRequest(form);
        }
        if ("refund".equals(form.code())) {
            return refundFormRequest(form);
        }
        if ("terminate-appraisal".equals(form.code())) {
            return terminateAppraisalFormRequest(form);
        }
        if ("archive".equals(form.code())) {
            return archiveFormRequest(form);
        }
        if ("seal-application".equals(form.code())) {
            return sealApplicationFormRequest(form);
        }
        if ("expense-reimbursement".equals(form.code())) {
            return expenseReimbursementFormRequest(form);
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
        if ("draft-opinion-review".equals(workflow.code())) {
            return draftOpinionReviewWorkflowRequest(workflow);
        }
        if ("final-opinion-review".equals(workflow.code())) {
            return finalOpinionReviewWorkflowRequest(workflow);
        }
        if ("issue-opinion".equals(workflow.code())) {
            return issueOpinionWorkflowRequest(workflow);
        }
        if ("issue-draft-opinion".equals(workflow.code())) {
            return issueDraftOpinionWorkflowRequest(workflow);
        }
        if ("court-letter".equals(workflow.code())) {
            return courtLetterWorkflowRequest(workflow);
        }
        if ("court-appearance".equals(workflow.code())) {
            return courtAppearanceWorkflowRequest(workflow);
        }
        if ("withdraw-case-letter".equals(workflow.code())) {
            return withdrawCaseLetterWorkflowRequest(workflow);
        }
        if ("refund".equals(workflow.code())) {
            return refundWorkflowRequest(workflow);
        }
        if ("terminate-appraisal".equals(workflow.code())) {
            return terminateAppraisalWorkflowRequest(workflow);
        }
        if ("archive".equals(workflow.code())) {
            return archiveWorkflowRequest(workflow);
        }
        if ("seal-application".equals(workflow.code())) {
            return sealApplicationWorkflowRequest(workflow);
        }
        if ("expense-reimbursement".equals(workflow.code())) {
            return expenseReimbursementWorkflowRequest(workflow);
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
                field("serialNo", "流水号", "text", "流程基础", false, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("initiatorName", "业务人员", "user", "流程基础", false, true),
                field("initiatedDate", "发起日期", "date", "流程基础", false, true),
                field("projectNo", "项目编号", "text", "流程基础", false, false),
                field("expressNo", "快递单号", "text", "委托信息", false, false),
                field("receivedDate", "收件日期", "date", "委托信息", false, false),
                field("filingDate", "立案日期", "date", "委托信息", false, false),
                field("clientName", "委托人", "text", "委托信息", false, false),
                field("caseNo", "案件号", "text", "委托信息", false, false),
                field("undertakingLegalPerson", "承办法人", "text", "委托信息", false, false),
                field("institutionSelectionMethod", "确定机构方式", "select", "委托信息", false, false,
                        List.of("随机", "指定", "协商", "其他")),
                field("institutionSelectionTime", "确定机构时间", "datetime", "委托信息", false, false),
                field("appraisalCategory", "鉴定类别", "select", "案件信息", false, false,
                        List.of("工程造价", "质量鉴定", "资产评估", "其他")),
                field("applicantName", "原告/申请人", "text", "案件信息", false, false),
                field("respondentName", "被告/被申请人", "text", "案件信息", false, false),
                field("urgencyLevel", "项目紧急程度", "select", "案件信息", false, false,
                        List.of("普通", "紧急", "特急")),
                field("caseChannel", "线上/线下", "select", "案件信息", false, false,
                        List.of("线上", "线下")),
                field("projectAmount", "项目金额", "number", "案件信息", false, false),
                field("appraisalMatter", "鉴定事项", "textarea", "案件信息", false, false),
                field("entrustAccepted", "委托审查是否受理", "boolean", "受理决策", false, false),
                field("preliminarySurveyRequired", "是否进行初步勘验", "boolean", "受理决策", false, false),
                field("materialReceiveRequired", "是否同步收案员材料接收", "boolean", "受理决策", false, false),
                field("departmentHeadId", "部门负责人", "user", "受理决策", false, false),
                field("projectLeaderId", "指定项目负责人", "user", "受理决策", false, false),
                field("projectAssistantId", "指定项目辅助人", "user", "受理决策", false, false),
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
                node("INIT_FILL", "发起者填写委托信息", "task", "candidate", "收案员", 1, 24, true, workflow.formCode(), 10),
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
                field("surveyDate", "初步勘验日期", "date", "勘验安排", false, false),
                field("surveyLocation", "勘验地点", "text", "勘验安排", false, false),
                field("surveyPlanUploaded", "现场工作方案已上传", "boolean", "勘验安排", false, false),
                field("equipmentOutboundRecorded", "设备出入库记录已登记", "boolean", "设备记录", false, false),
                field("equipmentUsageRecorded", "设备使用记录已登记", "boolean", "设备记录", false, false),
                field("surveySummary", "初步勘验情况", "textarea", "勘验结论", false, false),
                field("appraisalConditionMet", "是否具备鉴定条件", "boolean", "勘验结论", false, false),
                field("nextRecommendation", "下一步建议", "select", "勘验结论", false, false,
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
                field("equipmentOutboundRecorded", "设备出入库记录已登记", "boolean", "设备记录", true, false),
                field("equipmentUsageRecorded", "设备使用记录已登记", "boolean", "设备记录", true, false),
                field("equipmentReturnRecorded", "设备归还记录已登记", "boolean", "设备记录", true, false),
                field("projectAmount", "项目金额", "number", "审核规则", true, false),
                field("majorAmountProject", "项目金额是否大于15万", "boolean", "审核规则", true, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", true, false),
                field("projectReviewRoute", "项目负责人审核后流向", "select", "项目负责人审核", true, false,
                        List.of("技术负责人审核", "确认后续流程", "退回修改")),
                field("technicalReviewPassed", "技术负责人审核通过", "boolean", "技术负责人审核", false, false),
                field("departmentReviewPassed", "部门负责人审核通过", "boolean", "部门负责人审核", false, false),
                field("projectMaterialReviewPassed", "项目负责人材料审核通过", "boolean", "项目负责人审核材料", true, false),
                field("materialReviewOpinion", "材料审核意见", "textarea", "项目负责人审核材料", false, false),
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
                        "groups", List.of("流程基础", "勘验安排", "勘验记录", "设备记录", "审核规则", "项目负责人审核", "技术负责人审核", "部门负责人审核", "项目负责人审核材料", "后续流程", "办理意见")
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
                                Map.of("if", "projectMaterialReviewPassed == true", "then", "equipmentOutboundRecorded == true"),
                                Map.of("if", "projectReviewPassed == true", "then", "equipmentUsageRecorded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "技术负责人审核", Map.of("roles", List.of("技术负责人")),
                                "部门负责人审核", Map.of("roles", List.of("部门负责人")),
                                "项目负责人审核材料", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-现场勘验",
                        "branchFields", List.of("majorAmountProject", "projectReviewPassed", "projectReviewRoute", "technicalReviewPassed", "departmentReviewPassed", "projectMaterialReviewPassed", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-现场勘验')",
                        "majorAmountProject", "projectAmount > 150000",
                        "autoArchiveTitle", "concat(caseNo,'/现场勘验/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人完成现场工作方案，金额阈值审核通过后由项目负责人转交项目辅助人补充设备出入库和使用记录，再由项目负责人审核材料"),
                        Map.of("type", "validation", "text", "项目金额大于15万时必须经项目负责人、技术负责人、部门负责人逐级审核；技术负责人和部门负责人发现问题均退回项目辅助人"),
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
                node("PROJECT_TO_EQUIPMENT", "项目负责人转交设备记录", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 50),
                node("ASSISTANT_EQUIPMENT", "项目辅助人填写仪器设备相关表", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 60),
                node("PROJECT_MATERIAL_REVIEW", "项目负责人审核上传材料", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 70),
                node("NEXT_FLOW_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 80),
                node("MATERIAL_RECEIVE_RETURN", "进入材料接收与返还", "task", "candidate", "项目负责人", 1, 24, true, "material-receive-return", 90),
                node("DRAFT_OPINION_REVIEW", "进入征求意见稿送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "draft-opinion-review", 100),
                node("FINAL_OPINION_REVIEW", "进入鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 110),
                node("REFUND", "进入退费", "task", "candidate", "项目负责人", 1, 24, true, "refund", 120),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 130),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 140)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_SURVEY", "APPROVE", "进入现场勘验", null, 0, 10),
                transition("ASSISTANT_SURVEY", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "TECHNICAL_REVIEW", "APPROVE", "金额大于15万，转技术负责人审核", "form.projectReviewRoute == '技术负责人审核'", 1, 30),
                transition("PROJECT_REVIEW", "PROJECT_TO_EQUIPMENT", "APPROVE", "金额不超过15万，转交设备记录", "form.projectReviewRoute == '确认后续流程'", 1, 31),
                transition("PROJECT_REVIEW", "ASSISTANT_SURVEY", "RETURN", "退回项目辅助人补充勘验记录", "form.projectReviewRoute == '退回修改'", 1, 32),
                transition("TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "APPROVE", "技术负责人审核通过，转部门负责人审核", "form.technicalReviewPassed == true", 1, 40),
                transition("TECHNICAL_REVIEW", "ASSISTANT_SURVEY", "RETURN", "退回项目辅助人补充现场勘验材料", "form.technicalReviewPassed == false", 1, 41),
                transition("DEPARTMENT_REVIEW", "PROJECT_TO_EQUIPMENT", "APPROVE", "部门负责人审核通过，转项目负责人", "form.departmentReviewPassed == true", 1, 50),
                transition("DEPARTMENT_REVIEW", "ASSISTANT_SURVEY", "RETURN", "退回项目辅助人补充现场勘验材料", "form.departmentReviewPassed == false", 1, 51),
                transition("PROJECT_TO_EQUIPMENT", "ASSISTANT_EQUIPMENT", "APPROVE", "转交项目辅助人填写仪器设备相关表", null, 1, 60),
                transition("ASSISTANT_EQUIPMENT", "PROJECT_MATERIAL_REVIEW", "APPROVE", "转交项目负责人审核上传材料", null, 1, 70),
                transition("PROJECT_MATERIAL_REVIEW", "NEXT_FLOW_DECISION", "APPROVE", "材料审核通过，确认后续流程", "form.projectMaterialReviewPassed == true", 1, 80),
                transition("PROJECT_MATERIAL_REVIEW", "ASSISTANT_EQUIPMENT", "RETURN", "退回项目辅助人补充设备记录", "form.projectMaterialReviewPassed == false", 1, 81),
                transition("NEXT_FLOW_DECISION", "MATERIAL_RECEIVE_RETURN", "APPROVE", "进入材料接收与返还", "form.nextRecommendation == '材料接收与返还'", 1, 90,
                        subflowConfig("material-receive-return", "现场勘验完成后选择进入材料接收与返还")),
                transition("NEXT_FLOW_DECISION", "DRAFT_OPINION_REVIEW", "APPROVE", "进入征求意见稿送审稿编制", "form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", 1, 91,
                        subflowConfig("draft-opinion-review", "现场勘验完成后选择进入征求意见稿送审稿编制")),
                transition("NEXT_FLOW_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "进入鉴定意见书送审稿编制", "form.nextRecommendation == '鉴定意见书送审稿编制'", 1, 92,
                        subflowConfig("final-opinion-review", "现场勘验完成后选择进入鉴定意见书送审稿编制")),
                transition("NEXT_FLOW_DECISION", "REFUND", "APPROVE", "进入退费", "form.nextRecommendation == '退费'", 1, 93,
                        subflowConfig("refund", "现场勘验完成后选择进入退费")),
                transition("NEXT_FLOW_DECISION", "TERMINATE_APPRAISAL", "APPROVE", "进入终止鉴定", "form.nextRecommendation == '终止鉴定'", 1, 94,
                        subflowConfig("terminate-appraisal", "现场勘验完成后选择进入终止鉴定")),
                transition("MATERIAL_RECEIVE_RETURN", "END", "COMPLETE", "材料接收与返还子流程已触发", null, 1, 100),
                transition("DRAFT_OPINION_REVIEW", "END", "COMPLETE", "征求意见稿送审稿编制子流程已触发", null, 1, 101),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 102),
                transition("REFUND", "END", "COMPLETE", "退费子流程已触发", null, 1, 103),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 104)
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
                field("rejectionReason", "不予受理原因", "textarea", "通知编制", false, false),
                field("noticeDraftCompleted", "不予受理通知书已编制", "boolean", "通知编制", false, false),
                field("noticeSummary", "通知书内容摘要", "textarea", "通知编制", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", false, false),
                field("reviewOpinion", "审核意见", "textarea", "项目负责人审核", false, false),
                field("sealRequired", "是否需要用章", "boolean", "用章与回传", false, false),
                field("sealedNoticeUploaded", "盖章通知书扫描件已上传", "boolean", "用章与回传", false, false),
                field("deliveryMethod", "送达方式", "select", "送达与归档", false, false,
                        List.of("邮寄", "现场领取", "电子送达", "其他")),
                field("deliveryDate", "送达日期", "date", "送达与归档", false, false),
                field("archiveConfirmed", "归档材料已确认", "boolean", "送达与归档", false, false),
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
                                "用章与回传", Map.of("roles", List.of("档案管理员", "业务人员")),
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
                node("ARCHIVIST_CONFIRM", "档案管理员同意盖章", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 30),
                node("SEAL_APPLICATION", "档案管理员提交用章申请", "task", "candidate", "档案管理员", 1, 24, true, "seal-application", 40),
                node("SEALED_NOTICE_UPLOAD", "项目辅助人上传盖章扫描版", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 50),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 60),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 70)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_DRAFT", "APPROVE", "进入不予受理", null, 0, 10),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "ARCHIVIST_CONFIRM", "APPROVE", "审核通过，转档案管理员盖章", "form.projectReviewPassed == true", 1, 30),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改通知书", "form.projectReviewPassed == false", 1, 31),
                transition("ARCHIVIST_CONFIRM", "SEAL_APPLICATION", "APPROVE", "同意盖章并提交申请", "form.sealRequired == true", 1, 40,
                        subflowConfig("seal-application", "审核通过后自动进入用章流程")),
                transition("ARCHIVIST_CONFIRM", "SEALED_NOTICE_UPLOAD", "APPROVE", "无需盖章直接上传", "form.sealRequired == false", 1, 41),
                transition("ARCHIVIST_CONFIRM", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", null, 1, 42),
                transition("SEAL_APPLICATION", "SEALED_NOTICE_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 50),
                transition("SEALED_NOTICE_UPLOAD", "ARCHIVE_SUBFLOW", "APPROVE", "上传扫描版，进入归档", null, 1, 60,
                        subflowConfig("archive", "不予受理通知书扫描版上传后自动进入归档流程")),
                transition("SEALED_NOTICE_UPLOAD", "ARCHIVIST_CONFIRM", "RETURN", "退回档案管理员重新确认盖章", null, 1, 61),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 70)
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
                field("materialReceiveType", "材料接收与返还类型", "select", "材料接收", true, false,
                        List.of("委托方直接提供", "需要补充材料")),
                field("materialUploaderId", "材料上传主办人", "user", "材料接收", false, false),
                field("materialSource", "材料来源", "text", "材料接收", true, false),
                field("requireSupplementaryMaterial", "是否补充材料", "boolean", "材料接收", true, false),
                field("supplementaryNotice", "补材通知", "textarea", "材料接收", false, false),
                field("supplementaryNoticeUploaded", "补充鉴定材料通知书已上传", "boolean", "材料接收", false, false),
                field("materialsUploaded", "材料已上传", "boolean", "材料接收", false, false),
                field("projectMaterialConfirmed", "项目负责人已确认材料", "boolean", "材料接收", false, false),
                field("materialDetails", "材料名称/数量/介质", "textarea", "材料接收", true, false),
                field("receiveDate", "接收时间", "date", "材料接收", true, false),
                field("materialMediaType", "材料介质类别", "text", "材料保管", true, false),
                field("storageLocation", "存放地址", "text", "材料保管", true, false),
                field("requireReturn", "是否返还", "boolean", "材料保管", true, false),
                field("storageStatus", "保管状态", "select", "材料保管", true, false,
                        List.of("正常", "损毁", "灭失")),
                field("returnRegistrationCompleted", "鉴定材料接收及返还登记表已填写", "boolean", "材料返还", false, false),
                field("returnReceiver", "返还接收人", "text", "材料返还", false, false),
                field("returnDate", "返还时间", "date", "材料返还", false, false),
                field("nextRecommendation", "下一步建议", "select", "后续流程", true, false,
                        List.of("鉴定意见书征求意见稿送审稿编制", "鉴定意见书送审稿编制", "退费", "终止鉴定")),
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
                                Map.of("if", "requireSupplementaryMaterial == true", "then", "supplementaryNoticeUploaded == true"),
                                Map.of("if", "materialReceiveType == '委托方直接提供'", "then", "materialUploaderId != null"),
                                Map.of("if", "materialReceiveType == '委托方直接提供'", "then", "materialsUploaded == true"),
                                Map.of("if", "projectMaterialConfirmed == false", "then", "materialsUploaded == true"),
                                Map.of("if", "requireReturn == true", "then", "returnReceiver != null"),
                                Map.of("if", "requireReturn == true", "then", "returnDate != null"),
                                Map.of("if", "requireReturn == true", "then", "returnRegistrationCompleted == true")
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
                        "branchFields", List.of("materialReceiveType", "requireSupplementaryMaterial", "projectMaterialConfirmed", "requireReturn", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-材料接收与返还')",
                        "autoArchiveTitle", "concat(caseNo,'/材料接收与返还/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目负责人确认材料需求，委托方直接提供时指定同事上传材料，需要补充材料时上传补材通知并进入发交费通知书及相关函件"),
                        Map.of("type", "validation", "text", "直接提供材料必须指定上传主办人并上传材料；需要返还时必须填写返还登记、接收人和返还时间"),
                        Map.of("type", "archive", "text", "材料来源、明细、补材通知、保管状态及返还记录在各节点完成后自动归档")
                ))
        );
    }

    private WorkflowDesignRequest materialReceiveReturnWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_CONFIRM", "项目负责人确认材料需求", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 10),
                node("MATERIAL_UPLOAD", "材料上传主办人上传材料", "task", "single", null, 1, 24, true, workflow.formCode(), 20),
                node("PROJECT_MATERIAL_CONFIRM", "项目负责人确认材料", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 30),
                node("PAYMENT_NOTICE", "进入发交费通知书及相关函件", "task", "candidate", "项目负责人", 1, 24, true, "payment-notice", 40),
                node("ASSISTANT_REGISTER", "项目辅助人登记材料", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 50),
                node("ARCHIVIST_HANDLE", "档案管理员接收保管", "task", "candidate", "档案管理员", 1, 48, true, workflow.formCode(), 60),
                node("ASSISTANT_RETURN", "项目辅助人返还材料", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 70),
                node("PARALLEL_GATEWAY_SPLIT", "材料处理后并行分支", "gateway", "parallel", null, 0, 0, false, null, 80),
                node("ARCHIVE", "进入归档", "task", "candidate", "档案管理员", 1, 24, true, "archive", 90),
                node("PROJECT_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 100),
                node("DRAFT_OPINION_REVIEW", "进入征求意见稿送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "draft-opinion-review", 110),
                node("FINAL_OPINION_REVIEW", "进入鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 120),
                node("REFUND", "进入退费", "task", "candidate", "项目负责人", 1, 24, true, "refund", 130),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 140),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 150)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_CONFIRM", "APPROVE", "进入材料接收与返还", null, 0, 10),
                transition("PROJECT_CONFIRM", "MATERIAL_UPLOAD", "APPROVE", "委托方直接提供，转交主办人上传材料", "form.materialReceiveType == '委托方直接提供'", 1, 20),
                transition("PROJECT_CONFIRM", "PAYMENT_NOTICE", "APPROVE", "需要补充材料，进入发交费通知书及相关函件", "form.requireSupplementaryMaterial == true", 1, 21,
                        subflowConfig("payment-notice", "材料接收与返还中需要补充材料，进入发交费通知书及相关函件")),
                transition("MATERIAL_UPLOAD", "PROJECT_MATERIAL_CONFIRM", "APPROVE", "材料上传后转项目负责人确认", null, 1, 30),
                transition("MATERIAL_UPLOAD", "PROJECT_CONFIRM", "RETURN", "退回项目负责人复核材料接收类型", null, 1, 31),
                transition("PROJECT_MATERIAL_CONFIRM", "ASSISTANT_REGISTER", "APPROVE", "项目负责人确认材料，转项目辅助人登记", "form.projectMaterialConfirmed == true", 1, 40),
                transition("PROJECT_MATERIAL_CONFIRM", "MATERIAL_UPLOAD", "RETURN", "退回材料上传主办人补充材料", "form.projectMaterialConfirmed == false", 1, 41),
                transition("ASSISTANT_REGISTER", "ASSISTANT_RETURN", "APPROVE", "材料需要返还，转项目辅助人返还材料", "form.requireReturn == true", 1, 50),
                transition("ASSISTANT_REGISTER", "ARCHIVIST_HANDLE", "APPROVE", "材料无需返还，交档案管理员保管", "form.requireReturn == false", 1, 51),
                transition("ASSISTANT_REGISTER", "PROJECT_MATERIAL_CONFIRM", "RETURN", "退回项目负责人重新确认材料", null, 1, 52),
                transition("ASSISTANT_RETURN", "PARALLEL_GATEWAY_SPLIT", "APPROVE", "材料返还完成，进入归档和后续判断", null, 1, 60),
                transition("ASSISTANT_RETURN", "ASSISTANT_REGISTER", "RETURN", "退回项目辅助人补充接收登记", null, 1, 61),
                transition("ARCHIVIST_HANDLE", "PARALLEL_GATEWAY_SPLIT", "APPROVE", "材料保管完成，进入归档和后续判断", null, 1, 70),
                transition("ARCHIVIST_HANDLE", "ASSISTANT_REGISTER", "RETURN", "退回项目辅助人重新登记材料", null, 1, 71),
                transition("PARALLEL_GATEWAY_SPLIT", "ARCHIVE", "APPROVE", "材料返还或保管后进入归档", null, 1, 80,
                        subflowConfig("archive", "材料接收与返还完成后进入归档")),
                transition("PARALLEL_GATEWAY_SPLIT", "PROJECT_DECISION", "APPROVE", "同时转项目负责人确认报告编制条件", null, 1, 81),
                transition("PROJECT_DECISION", "DRAFT_OPINION_REVIEW", "APPROVE", "进入征求意见稿送审稿编制", "form.nextRecommendation == '鉴定意见书征求意见稿送审稿编制'", 1, 90,
                        subflowConfig("draft-opinion-review", "材料处理完成后选择进入征求意见稿送审稿编制")),
                transition("PROJECT_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "进入鉴定意见书送审稿编制", "form.nextRecommendation == '鉴定意见书送审稿编制'", 1, 91,
                        subflowConfig("final-opinion-review", "材料处理完成后选择进入鉴定意见书送审稿编制")),
                transition("PROJECT_DECISION", "REFUND", "APPROVE", "进入退费", "form.nextRecommendation == '退费'", 1, 92,
                        subflowConfig("refund", "材料处理完成后选择进入退费")),
                transition("PROJECT_DECISION", "TERMINATE_APPRAISAL", "APPROVE", "进入终止鉴定", "form.nextRecommendation == '终止鉴定'", 1, 93,
                        subflowConfig("terminate-appraisal", "材料处理完成后选择进入终止鉴定")),
                transition("PROJECT_DECISION", "ARCHIVIST_HANDLE", "RETURN", "退回档案管理员处理材料", null, 1, 94),
                transition("PAYMENT_NOTICE", "END", "COMPLETE", "发交费通知子流程已触发", null, 1, 100),
                transition("ARCHIVE", "END", "COMPLETE", "归档子流程已触发", null, 1, 101),
                transition("DRAFT_OPINION_REVIEW", "END", "COMPLETE", "征求意见稿送审稿编制子流程已触发", null, 1, 102),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 103),
                transition("REFUND", "END", "COMPLETE", "退费子流程已触发", null, 1, 104),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 105)
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

    private FormDesignRequest draftOpinionReviewFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("technicalLeaderId", "技术负责人", "user", "流程基础", true, true),
                field("departmentHeadId", "部门负责人", "user", "流程基础", true, true),
                field("draftOpinionUploaded", "征求意见稿初稿已上传", "boolean", "初稿编制", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", false, false),
                field("technicalReviewPassed", "技术负责人审核通过", "boolean", "技术负责人审核", false, false),
                field("departmentReviewPassed", "部门负责人审核通过", "boolean", "部门负责人审核", false, false),
                field("finalDraftUploaded", "征求意见稿送审稿定稿已上传", "boolean", "定稿上传", false, false),
                field("nextRecommendation", "下一步建议", "select", "后续流程", false, false,
                        List.of("出具征求意见稿")),
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
                        "groups", List.of("流程基础", "初稿编制", "项目负责人审核", "技术负责人审核", "部门负责人审核", "定稿上传", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles()
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "初稿编制", Map.of("roles", List.of("项目辅助人")),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "技术负责人审核", Map.of("roles", List.of("技术负责人")),
                                "部门负责人审核", Map.of("roles", List.of("部门负责人")),
                                "定稿上传", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-征求意见稿送审稿编制",
                        "branchFields", List.of("projectReviewPassed", "technicalReviewPassed", "departmentReviewPassed", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-征求意见稿送审稿编制')",
                        "autoArchiveTitle", "concat(caseNo,'/征求意见稿送审稿编制/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制初稿，经项目负责人、技术负责人、部门负责人三级审核后上传定稿"),
                        Map.of("type", "validation", "text", "定稿必须由项目负责人确认上传，三级审核不可合并"),
                        Map.of("type", "archive", "text", "初稿、项目负责人审核稿、技术负责人审核稿、部门负责人审核稿等版本过程自动归档保留")
                ))
        );
    }

    private WorkflowDesignRequest draftOpinionReviewWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_ASSIGN", "项目负责人转交项目辅助人编制初稿", "task", "candidate", "项目负责人", 1, 24, true, null, 10),
                node("ASSISTANT_DRAFT", "项目辅助人编制初稿", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 20),
                node("PROJECT_REVIEW", "项目负责人审核", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 30),
                node("TECHNICAL_REVIEW", "技术负责人审核", "task", "candidate", "技术负责人", 1, 48, true, workflow.formCode(), 40),
                node("DEPARTMENT_REVIEW", "部门负责人审核", "task", "candidate", "部门负责人", 1, 48, true, workflow.formCode(), 50),
                node("PROJECT_FINAL_UPLOAD", "项目负责人上传定稿", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 60),
                node("ISSUE_DRAFT_OPINION", "进入出具征求意见稿", "task", "candidate", "项目负责人", 1, 24, true, "issue-draft-opinion", 70),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 80)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_ASSIGN", "APPROVE", "进入征求意见稿送审稿编制", null, 0, 10),
                transition("PROJECT_ASSIGN", "ASSISTANT_DRAFT", "APPROVE", "转交项目辅助人编制初稿", null, 1, 20),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 30),
                transition("PROJECT_REVIEW", "TECHNICAL_REVIEW", "APPROVE", "项目负责人审核通过，转技术负责人", "form.projectReviewPassed == true", 1, 40),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改初稿", "form.projectReviewPassed == false", 1, 41),
                transition("TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "APPROVE", "技术负责人审核通过，转部门负责人", "form.technicalReviewPassed == true", 1, 50),
                transition("TECHNICAL_REVIEW", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", "form.technicalReviewPassed == false", 1, 51),
                transition("DEPARTMENT_REVIEW", "PROJECT_FINAL_UPLOAD", "APPROVE", "部门负责人审核通过，转项目负责人定稿", "form.departmentReviewPassed == true", 1, 60),
                transition("DEPARTMENT_REVIEW", "TECHNICAL_REVIEW", "RETURN", "退回技术负责人复核", "form.departmentReviewPassed == false", 1, 61),
                transition("PROJECT_FINAL_UPLOAD", "ISSUE_DRAFT_OPINION", "APPROVE", "进入出具征求意见稿", "form.nextRecommendation == '出具征求意见稿'", 1, 70,
                        subflowConfig("issue-draft-opinion", "征求意见稿送审稿编制完成后选择进入出具征求意见稿")),
                transition("PROJECT_FINAL_UPLOAD", "DEPARTMENT_REVIEW", "RETURN", "退回部门负责人复核", null, 1, 71),
                transition("ISSUE_DRAFT_OPINION", "END", "COMPLETE", "出具征求意见稿子流程已触发", null, 1, 80)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：鉴定意见书征求意见稿送审稿编制",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-征求意见稿送审稿编制",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest finalOpinionReviewFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("technicalLeaderId", "技术负责人", "user", "流程基础", true, true),
                field("departmentHeadId", "部门负责人", "user", "流程基础", true, true),
                field("opinionDraftUploaded", "初稿已上传", "boolean", "初稿编制", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", false, false),
                field("versionAUploaded", "版本A已上传", "boolean", "项目负责人审核", false, false),
                field("technicalReviewPassed", "技术负责人审核通过", "boolean", "技术负责人审核", false, false),
                field("versionABUploaded", "版本A-B已上传", "boolean", "技术负责人审核", false, false),
                field("departmentReviewPassed", "部门负责人审核通过", "boolean", "部门负责人审核", false, false),
                field("versionABCUploaded", "版本A-B-C已上传", "boolean", "部门负责人审核", false, false),
                field("finalDraftUploaded", "最终送审稿已上传", "boolean", "定稿上传", false, false),
                field("nextRecommendation", "下一步建议", "select", "后续流程", false, false,
                        List.of("出具鉴定意见书")),
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
                        "groups", List.of("流程基础", "初稿编制", "项目负责人审核", "技术负责人审核", "部门负责人审核", "定稿上传", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "projectReviewPassed == true", "then", "versionAUploaded == true"),
                                Map.of("if", "technicalReviewPassed == true", "then", "versionABUploaded == true"),
                                Map.of("if", "departmentReviewPassed == true", "then", "versionABCUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "初稿编制", Map.of("roles", List.of("项目辅助人")),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "技术负责人审核", Map.of("roles", List.of("技术负责人")),
                                "部门负责人审核", Map.of("roles", List.of("部门负责人")),
                                "定稿上传", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-送审稿编制",
                        "branchFields", List.of("projectReviewPassed", "technicalReviewPassed", "departmentReviewPassed", "nextRecommendation")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-送审稿编制')",
                        "autoArchiveTitle", "concat(caseNo,'/送审稿编制/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制初稿，经项目负责人、技术负责人、部门负责人三级审核并分别产生A、A-B、A-B-C版本，最后上传最终送审稿"),
                        Map.of("type", "validation", "text", "每次审核通过时必须上传对应的审核版本附件；三级审核不可合并"),
                        Map.of("type", "archive", "text", "初稿、A版、A-B版、A-B-C版及最终送审稿在节点完成后自动归档保留")
                ))
        );
    }

    private WorkflowDesignRequest finalOpinionReviewWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_ASSIGN", "项目负责人确认并转交项目辅助人编写", "task", "candidate", "项目负责人", 1, 24, true, null, 10),
                node("ASSISTANT_DRAFT", "项目辅助人编制初稿", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 20),
                node("PROJECT_REVIEW", "项目负责人审核并上传A版", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 30),
                node("TECHNICAL_REVIEW", "技术负责人审核并上传A-B版", "task", "candidate", "技术负责人", 1, 48, true, workflow.formCode(), 40),
                node("DEPARTMENT_REVIEW", "部门负责人审核并上传A-B-C版", "task", "candidate", "部门负责人", 1, 48, true, workflow.formCode(), 50),
                node("PROJECT_FINAL_UPLOAD", "项目负责人上传最终送审稿", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 60),
                node("ISSUE_OPINION", "进入出具鉴定意见书", "task", "candidate", "项目负责人", 1, 24, true, "issue-opinion", 70),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 80)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_ASSIGN", "APPROVE", "进入送审稿编制", null, 0, 10),
                transition("PROJECT_ASSIGN", "ASSISTANT_DRAFT", "APPROVE", "确认编写内容并转交项目辅助人", null, 1, 20),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转交项目负责人审核", null, 1, 30),
                transition("PROJECT_REVIEW", "TECHNICAL_REVIEW", "APPROVE", "项目负责人审核通过(A)，转技术负责人", "form.projectReviewPassed == true", 1, 40),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改初稿", "form.projectReviewPassed == false", 1, 41),
                transition("TECHNICAL_REVIEW", "DEPARTMENT_REVIEW", "APPROVE", "技术负责人审核通过(A-B)，转部门负责人", "form.technicalReviewPassed == true", 1, 50),
                transition("TECHNICAL_REVIEW", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", "form.technicalReviewPassed == false", 1, 51),
                transition("DEPARTMENT_REVIEW", "PROJECT_FINAL_UPLOAD", "APPROVE", "部门负责人审核通过(A-B-C)，转项目负责人定稿", "form.departmentReviewPassed == true", 1, 60),
                transition("DEPARTMENT_REVIEW", "TECHNICAL_REVIEW", "RETURN", "退回技术负责人复核", "form.departmentReviewPassed == false", 1, 61),
                transition("PROJECT_FINAL_UPLOAD", "ISSUE_OPINION", "APPROVE", "进入出具鉴定意见书", "form.nextRecommendation == '出具鉴定意见书'", 1, 70,
                        subflowConfig("issue-opinion", "鉴定意见书送审稿编制完成后选择进入出具鉴定意见书")),
                transition("PROJECT_FINAL_UPLOAD", "DEPARTMENT_REVIEW", "RETURN", "退回部门负责人复核", null, 1, 71),
                transition("ISSUE_OPINION", "END", "COMPLETE", "出具鉴定意见书子流程已触发", null, 1, 80)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：鉴定意见书送审稿编制",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-送审稿编制",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest issueOpinionFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("financeId", "财务人员", "user", "流程基础", true, true),
                field("opinionModified", "鉴定意见书已修改确认", "boolean", "意见书处理", false, false),
                field("commitmentDrafted", "鉴定人承诺书已上传", "boolean", "材料补充", false, false),
                field("reviewOpinionDrafted", "司法鉴定复核意见已上传", "boolean", "材料补充", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目审核", false, false),
                field("sealRequired", "是否需要用章", "boolean", "盖章流程", false, false),
                field("systemRegistrationUploaded", "中电投系统编号登记表扫描件已上传", "boolean", "盖章流程", false, false),
                field("sealedOpinionUploaded", "鉴定意见书盖章扫描件已上传", "boolean", "盖章流程", false, false),
                field("invoiceRequired", "是否开具发票", "boolean", "开票流程", false, false),
                field("invoiceIssued", "发票已开具并回传", "boolean", "开票流程", false, false),
                field("deliveryMethod", "送达方式", "select", "送达", true, false,
                        List.of("邮寄", "现场领取", "电子送达", "其他")),
                field("deliveryDate", "送达日期", "date", "送达", false, false),
                field("archiveConfirmed", "归档材料已确认", "boolean", "归档", false, false),
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
                        "groups", List.of("流程基础", "意见书处理", "材料补充", "项目审核", "盖章流程", "开票流程", "送达", "归档", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "sealRequired == true", "then", "sealedOpinionUploaded == true"),
                                Map.of("if", "invoiceRequired == true", "then", "invoiceIssued == true"),
                                Map.of("if", "archiveConfirmed == true", "then", "sealedOpinionUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "意见书处理", Map.of("roles", List.of("项目负责人")),
                                "材料补充", Map.of("roles", List.of("项目辅助人")),
                                "项目审核", Map.of("roles", List.of("项目负责人")),
                                "盖章流程", Map.of("roles", List.of("项目负责人", "项目辅助人", "档案管理员")),
                                "开票流程", Map.of("roles", List.of("财务人员")),
                                "送达", Map.of("roles", List.of("档案管理员")),
                                "归档", Map.of("roles", List.of("档案管理员"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-出具鉴定意见书",
                        "branchFields", List.of("sealRequired", "invoiceRequired", "archiveConfirmed")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-出具鉴定意见书')",
                        "autoArchiveTitle", "concat(caseNo,'/出具鉴定意见书/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目负责人确认意见书后转项目辅助人上传承诺书和复核意见，项目负责人审核通过后进入盖章和开票并行，最后由档案管理员送达并归档"),
                        Map.of("type", "validation", "text", "选择需要用章时必须回传意见书盖章件，选择需要发票时必须回传发票凭证"),
                        Map.of("type", "archive", "text", "承诺书、复核意见、意见书盖章件、发票记录、送达单和归档确认均在节点完成时自动归档保存")
                ))
        );
    }

    private WorkflowDesignRequest issueOpinionWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_MODIFY", "项目负责人修改鉴定意见书", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 10),
                node("ASSISTANT_UPLOAD", "项目辅助人上传承诺书与复核意见", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 20),
                node("PROJECT_REVIEW", "项目负责人审核承诺书与复核意见", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 30),
                node("ARCHIVIST_CONFIRM", "档案管理员确认盖章申请并登记编号", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 40),
                node("PARALLEL_GATEWAY_SPLIT", "盖章开票并行分支", "gateway", "parallel", null, 0, 0, false, null, 50),
                node("SEAL_APPLICATION", "发起用章申请", "task", "candidate", "档案管理员", 1, 24, true, "seal-application", 60),
                node("SEALED_UPLOAD", "项目辅助人上传盖章扫描件", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 70),
                node("FINANCE_INVOICE", "财务开具发票", "task", "candidate", "财务人员", 1, 48, true, workflow.formCode(), 80),
                node("PARALLEL_GATEWAY_JOIN", "盖章开票并行汇聚", "gateway", "inclusive", null, 0, 0, false, null, 90),
                node("DELIVERY_ARCHIVE", "档案管理员送达并确认归档", "task", "candidate", "档案管理员", 1, 72, true, workflow.formCode(), 100),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 110),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 120)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_MODIFY", "APPROVE", "进入出具鉴定意见书", null, 0, 10),
                transition("PROJECT_MODIFY", "ASSISTANT_UPLOAD", "APPROVE", "转项目辅助人上传承诺书与复核意见", null, 1, 20),
                transition("ASSISTANT_UPLOAD", "PROJECT_REVIEW", "APPROVE", "转项目负责人审核", null, 1, 30),
                transition("PROJECT_REVIEW", "ARCHIVIST_CONFIRM", "APPROVE", "审核通过，转档案管理员申请盖章和开票", "form.projectReviewPassed == true", 1, 40),
                transition("PROJECT_REVIEW", "ASSISTANT_UPLOAD", "RETURN", "审核不通过，退回项目辅助人补充材料", "form.projectReviewPassed == false", 1, 41),
                transition("ARCHIVIST_CONFIRM", "PARALLEL_GATEWAY_SPLIT", "APPROVE", "盖章申请确认并进入并行分支", null, 1, 50),
                transition("PARALLEL_GATEWAY_SPLIT", "SEAL_APPLICATION", "APPROVE", "进入用章分支", "form.sealRequired == true", 1, 60,
                        subflowConfig("seal-application", "自动进入用章流程以盖鉴定意见书章")),
                transition("PARALLEL_GATEWAY_SPLIT", "SEALED_UPLOAD", "APPROVE", "无需用章，直接上传最终扫描件", "form.sealRequired == false", 1, 61),
                transition("SEAL_APPLICATION", "SEALED_UPLOAD", "COMPLETE", "用章流程完成，转项目辅助人线下盖章并上传扫描件", null, 1, 70),
                transition("SEALED_UPLOAD", "PARALLEL_GATEWAY_JOIN", "APPROVE", "盖章扫描件上传完成汇聚", null, 1, 80),
                transition("SEALED_UPLOAD", "ASSISTANT_UPLOAD", "RETURN", "退回项目辅助人补充承诺书或复核意见", null, 1, 81),
                transition("PARALLEL_GATEWAY_SPLIT", "FINANCE_INVOICE", "APPROVE", "进入开票分支", "form.invoiceRequired == true", 1, 90),
                transition("PARALLEL_GATEWAY_SPLIT", "PARALLEL_GATEWAY_JOIN", "APPROVE", "无需开票分支汇聚", "form.invoiceRequired == false", 1, 91),
                transition("FINANCE_INVOICE", "PARALLEL_GATEWAY_JOIN", "APPROVE", "开票完成汇聚", null, 1, 100),
                transition("PARALLEL_GATEWAY_JOIN", "DELIVERY_ARCHIVE", "APPROVE", "合并后转档案管理员送达与归档", null, 1, 110),
                transition("DELIVERY_ARCHIVE", "ARCHIVE_SUBFLOW", "APPROVE", "送达完成，触发归档", "form.archiveConfirmed == true", 1, 120,
                        subflowConfig("archive", "出具鉴定意见书送达并确认归档材料后自动进入归档流程")),
                transition("DELIVERY_ARCHIVE", "SEALED_UPLOAD", "RETURN", "退回补充盖章扫描件", null, 1, 121),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 130)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：出具鉴定意见书",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-出具鉴定意见书",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest issueDraftOpinionFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("explainLetterDrafted", "鉴定说明函已编制并上传", "boolean", "材料补充", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目审核", false, false),
                field("sealRequired", "是否需要用章", "boolean", "盖章流程", false, false),
                field("draftOpinionUploaded", "鉴定意见书征求意见稿已上传", "boolean", "盖章流程", false, false),
                field("sealedDraftOpinionUploaded", "征求意见稿盖章扫描件已上传", "boolean", "盖章流程", false, false),
                field("deliveryMethod", "送达方式", "select", "送达", true, false,
                        List.of("邮寄", "现场领取", "电子送达", "其他")),
                field("trackingNo", "快递单号", "text", "送达", false, false),
                field("deliveryDate", "寄出日期", "date", "送达", false, false),
                field("archiveConfirmed", "归档已触发并确认", "boolean", "归档", false, false),
                field("feedbackReceived", "是否收到反馈", "boolean", "反馈与异议", false, false),
                field("feedbackHasObjection", "是否提出异议", "boolean", "反馈与异议", false, false),
                field("feedbackDecision", "反馈处理结论", "select", "反馈与异议", false, false,
                        List.of("收到异议", "无异议或未反馈")),
                field("objectionReason", "异议内容简述", "textarea", "反馈与异议", false, false),
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
                        "groups", List.of("流程基础", "材料补充", "项目审核", "盖章流程", "送达", "归档", "反馈与异议", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "sealRequired == true", "then", "sealedDraftOpinionUploaded == true"),
                                Map.of("if", "feedbackDecision == '收到异议'", "then", "feedbackReceived == true"),
                                Map.of("if", "feedbackDecision == '收到异议'", "then", "feedbackHasObjection == true"),
                                Map.of("if", "feedbackDecision == '收到异议'", "then", "objectionReason != null")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "材料补充", Map.of("roles", List.of("项目辅助人")),
                                "项目审核", Map.of("roles", List.of("项目负责人")),
                                "盖章流程", Map.of("roles", List.of("项目辅助人", "档案管理员")),
                                "送达", Map.of("roles", List.of("档案管理员")),
                                "归档", Map.of("roles", List.of("档案管理员")),
                                "反馈与异议", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-出具征求意见稿",
                        "branchFields", List.of("projectReviewPassed", "sealRequired", "feedbackReceived", "feedbackHasObjection", "feedbackDecision")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-出具征求意见稿')",
                        "autoArchiveTitle", "concat(caseNo,'/出具征求意见稿/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制鉴定说明函，项目负责人审核后转档案管理员申请盖章，项目辅助人上传盖章扫描件后同时进入归档和材料寄出，最后项目负责人登记反馈与异议状态"),
                        Map.of("type", "validation", "text", "选择需要用章时必须回传征求意见稿盖章件，收到异议时必须简述异议内容"),
                        Map.of("type", "archive", "text", "说明函、盖章件、送达记录和异议反馈在节点完成时自动归档保存")
                ))
        );
    }

    private WorkflowDesignRequest issueDraftOpinionWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_SUPPLEMENT", "项目辅助人编制并上传鉴定说明函", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核鉴定说明函", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 20),
                node("ARCHIVIST_CONFIRM", "档案管理员确认盖章并上传征求意见稿", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 30),
                node("SEAL_APPLICATION", "档案管理员提交用章申请", "task", "candidate", "档案管理员", 1, 24, true, "seal-application", 40),
                node("SEALED_UPLOAD", "项目辅助人上传盖章后扫描件", "task", "candidate", "项目辅助人", 1, 24, true, workflow.formCode(), 50),
                node("PARALLEL_GATEWAY_SPLIT", "归档和材料寄出并行分支", "gateway", "parallel", null, 0, 0, false, null, 60),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 70),
                node("DELIVERY", "档案管理员送达寄出", "task", "candidate", "档案管理员", 1, 72, true, workflow.formCode(), 80),
                node("WAIT_FEEDBACK", "项目负责人等待并登记反馈", "task", "candidate", "项目负责人", 1, 240, true, workflow.formCode(), 90),
                node("COURT_LETTER", "进入收到法院其他函件(含异议)", "task", "candidate", "项目负责人", 1, 24, true, "court-letter", 100),
                node("FINAL_OPINION_REVIEW", "无异议直接进入送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 110),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 120)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_SUPPLEMENT", "APPROVE", "进入出具征求意见稿", null, 0, 10),
                transition("ASSISTANT_SUPPLEMENT", "PROJECT_REVIEW", "APPROVE", "转项目负责人审核鉴定说明函", null, 1, 20),
                transition("PROJECT_REVIEW", "ARCHIVIST_CONFIRM", "APPROVE", "审核通过，转档案管理员", "form.projectReviewPassed == true", 1, 30),
                transition("PROJECT_REVIEW", "ASSISTANT_SUPPLEMENT", "RETURN", "审核不通过，退回项目辅助人修改说明函", "form.projectReviewPassed == false", 1, 31),
                transition("ARCHIVIST_CONFIRM", "SEAL_APPLICATION", "APPROVE", "同意盖章并提交用章申请", "form.sealRequired == true", 1, 40,
                        subflowConfig("seal-application", "自动进入用章流程以盖征求意见稿及说明函章")),
                transition("ARCHIVIST_CONFIRM", "SEALED_UPLOAD", "APPROVE", "无需用章，直接转项目辅助人上传扫描件", "form.sealRequired == false", 1, 41),
                transition("ARCHIVIST_CONFIRM", "ASSISTANT_SUPPLEMENT", "RETURN", "退回项目辅助人补充说明函或征求意见稿", null, 1, 42),
                transition("SEAL_APPLICATION", "SEALED_UPLOAD", "COMPLETE", "用章流程完成，转项目辅助人线下盖章并上传扫描件", null, 1, 50),
                transition("SEALED_UPLOAD", "PARALLEL_GATEWAY_SPLIT", "APPROVE", "盖章扫描件上传后并行归档和寄出", null, 1, 60),
                transition("SEALED_UPLOAD", "ARCHIVIST_CONFIRM", "RETURN", "退回档案管理员补充盖章或征求意见稿", null, 1, 61),
                transition("PARALLEL_GATEWAY_SPLIT", "ARCHIVE_SUBFLOW", "APPROVE", "同时进入归档", null, 1, 70,
                        subflowConfig("archive", "出具征求意见稿盖章扫描件上传后自动进入归档流程")),
                transition("PARALLEL_GATEWAY_SPLIT", "DELIVERY", "APPROVE", "同时进入材料寄出", null, 1, 71),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 80),
                transition("DELIVERY", "WAIT_FEEDBACK", "APPROVE", "寄出后等待反馈", null, 1, 90),
                transition("DELIVERY", "SEALED_UPLOAD", "RETURN", "退回补充盖章扫描件", null, 1, 91),
                transition("WAIT_FEEDBACK", "COURT_LETTER", "APPROVE", "收到异议反馈", "form.feedbackDecision == '收到异议'", 1, 100,
                        subflowConfig("court-letter", "收到异议后自动进入法院函件流程")),
                transition("WAIT_FEEDBACK", "FINAL_OPINION_REVIEW", "APPROVE", "收到反馈但无异议/超时无反馈", "form.feedbackDecision == '无异议或未反馈'", 1, 101,
                        subflowConfig("final-opinion-review", "无异议后自动进入鉴定意见书送审稿编制")),
                transition("COURT_LETTER", "END", "COMPLETE", "异议函处理流程已触发", null, 1, 110),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "送审稿编制流程已触发", null, 1, 111)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：出具征求意见稿",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-出具征求意见稿",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest courtLetterFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("linkedWorkflowCode", "关联原流程", "text", "流程基础", true, false),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("receivedLetterUploaded", "法院函件已上传", "boolean", "函件登记", true, false),
                field("projectLeaderAssigned", "已选择项目负责人主办", "boolean", "函件登记", true, false),
                field("letterType", "函件类型", "select", "函件登记", true, false,
                        List.of("异议函", "补充函", "催办函", "其他函件")),
                field("letterReceivedDate", "收函日期", "date", "函件登记", true, false),
                field("letterSummary", "函件内容摘要", "textarea", "函件登记", true, false),
                field("objectionAccepted", "是否按异议处理", "boolean", "异议判断", false, false),
                field("replyRequired", "非异议函是否需要回复", "boolean", "异议判断", false, false),
                field("replyDraftCompleted", "回复函草稿已完成", "boolean", "回复函编制", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", false, false),
                field("sealRequired", "档案管理员同意盖章", "boolean", "用章与寄送", false, false),
                field("sealedReplyUploaded", "回复函盖章件已上传", "boolean", "用章与寄送", false, false),
                field("deliveryMethod", "寄送方式", "select", "用章与寄送", false, false,
                        List.of("邮寄", "现场领取", "电子送达", "其他")),
                field("trackingNo", "快递单号", "text", "用章与寄送", false, false),
                field("deliveryDate", "寄送日期", "date", "用章与寄送", false, false),
                field("nextRecommendation", "后续处理", "select", "后续流程", false, false,
                        List.of("返回鉴定意见书送审稿编制", "进入出具鉴定意见书", "归档")),
                field("archiveConfirmed", "归档材料已确认", "boolean", "后续流程", false, false),
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
                        "groups", List.of("流程基础", "函件登记", "异议判断", "回复函编制", "项目负责人审核", "用章与寄送", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "letterType == '异议函'", "then", "objectionAccepted == true"),
                                Map.of("if", "objectionAccepted == false", "then", "replyRequired != null"),
                                Map.of("if", "projectReviewPassed == true", "then", "replyDraftCompleted == true"),
                                Map.of("if", "sealRequired == true", "then", "sealedReplyUploaded == true"),
                                Map.of("if", "archiveConfirmed == true", "then", "deliveryDate != null")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "函件登记", Map.of("roles", List.of("档案管理员", "项目负责人")),
                                "异议判断", Map.of("roles", List.of("项目负责人")),
                                "回复函编制", Map.of("roles", List.of("项目辅助人")),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "用章与寄送", Map.of("roles", List.of("档案管理员", "项目辅助人")),
                                "后续流程", Map.of("roles", List.of("项目负责人", "档案管理员"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-收到法院其他函件",
                        "branchFields", List.of("objectionAccepted", "replyRequired", "projectReviewPassed", "sealRequired", "nextRecommendation", "archiveConfirmed")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-收到法院其他函件')",
                        "autoArchiveTitle", "concat(caseNo,'/收到法院其他函件/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "法院函件先上传并选择项目负责人，项目负责人判断是否异议或是否需要回复，项目辅助人编制回复函，档案管理员确认用章后进入归档、发函和后续流程"),
                        Map.of("type", "validation", "text", "审核通过前必须完成回复函草稿；需要用章时必须上传盖章件；归档前需确认寄送记录"),
                        Map.of("type", "archive", "text", "法院函件原件、回复函、盖章件、寄送记录和后续流转信息在节点完成时自动归档保存")
                ))
        );
    }

    private WorkflowDesignRequest courtLetterWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("LETTER_UPLOAD", "上传法院函件并选择项目负责人", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_REGISTER", "项目负责人判断函件类型和处理方式", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 20),
                node("ASSISTANT_REPLY", "项目辅助人编制回复函", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 30),
                node("PROJECT_REVIEW", "项目负责人审核回复函", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 40),
                node("ARCHIVIST_CONFIRM", "档案管理员确认盖章", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 50),
                node("SEAL_APPLICATION", "档案管理员提交用章申请", "task", "candidate", "档案管理员", 1, 24, true, "seal-application", 60),
                node("SEALED_REPLY_UPLOAD", "项目辅助人上传盖章回复函", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 70),
                node("PARALLEL_GATEWAY_SPLIT", "归档和发函并行分支", "gateway", "parallel", null, 0, 0, false, null, 80),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 90),
                node("DELIVERY_RELATED_LETTER", "发相关函件", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 100),
                node("NEXT_FLOW_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 110),
                node("FINAL_OPINION_REVIEW", "返回鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 120),
                node("ISSUE_OPINION", "进入出具鉴定意见书", "task", "candidate", "项目负责人", 1, 24, true, "issue-opinion", 130),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 140)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "LETTER_UPLOAD", "APPROVE", "进入收到法院其他函件", null, 0, 10),
                transition("LETTER_UPLOAD", "PROJECT_REGISTER", "APPROVE", "函件上传完成，转项目负责人", null, 1, 20),
                transition("PROJECT_REGISTER", "ASSISTANT_REPLY", "APPROVE", "异议函，转项目辅助人编制异议回复函", "form.objectionAccepted == true", 1, 30),
                transition("PROJECT_REGISTER", "ASSISTANT_REPLY", "APPROVE", "非异议函但需要回复，转项目辅助人编制函件", "form.replyRequired == true", 1, 31),
                transition("PROJECT_REGISTER", "ARCHIVE_SUBFLOW", "APPROVE", "非异议函且无需回复，进入归档", "form.replyRequired == false", 1, 32,
                        subflowConfig("archive", "非异议函无需回复时进入归档")),
                transition("ASSISTANT_REPLY", "PROJECT_REVIEW", "APPROVE", "转项目负责人审核回复函", null, 1, 30),
                transition("ASSISTANT_REPLY", "PROJECT_REGISTER", "RETURN", "退回重新登记函件信息", null, 1, 31),
                transition("PROJECT_REVIEW", "ARCHIVIST_CONFIRM", "APPROVE", "项目负责人审核通过，转档案管理员确认盖章", "form.projectReviewPassed == true", 1, 40),
                transition("PROJECT_REVIEW", "ASSISTANT_REPLY", "RETURN", "退回项目辅助人修改回复函", "form.projectReviewPassed == false", 1, 41),
                transition("ARCHIVIST_CONFIRM", "SEAL_APPLICATION", "APPROVE", "档案管理员同意盖章，发起用章流程", "form.sealRequired == true", 1, 50,
                        subflowConfig("seal-application", "法院函件回复函审核通过后自动进入用章流程")),
                transition("ARCHIVIST_CONFIRM", "SEALED_REPLY_UPLOAD", "APPROVE", "无需用章，转项目辅助人上传回复函", "form.sealRequired == false", 1, 51),
                transition("ARCHIVIST_CONFIRM", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", null, 1, 52),
                transition("SEAL_APPLICATION", "SEALED_REPLY_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 60),
                transition("SEALED_REPLY_UPLOAD", "PARALLEL_GATEWAY_SPLIT", "APPROVE", "盖章回复函上传完成，进入归档和发函", null, 1, 70),
                transition("SEALED_REPLY_UPLOAD", "ARCHIVIST_CONFIRM", "RETURN", "退回档案管理员确认", null, 1, 71),
                transition("PARALLEL_GATEWAY_SPLIT", "ARCHIVE_SUBFLOW", "APPROVE", "同步进入归档", null, 0, 80,
                        subflowConfig("archive", "法院函件回复完成后进入归档")),
                transition("PARALLEL_GATEWAY_SPLIT", "DELIVERY_RELATED_LETTER", "APPROVE", "同步发相关函件", null, 0, 81),
                transition("DELIVERY_RELATED_LETTER", "NEXT_FLOW_DECISION", "APPROVE", "相关函件发出，确认后续流程", null, 1, 90),
                transition("DELIVERY_RELATED_LETTER", "SEALED_REPLY_UPLOAD", "RETURN", "退回补充盖章回复函", null, 1, 91),
                transition("NEXT_FLOW_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "寄送完成，返回鉴定意见书送审稿编制", "form.nextRecommendation == '返回鉴定意见书送审稿编制'", 1, 100,
                        subflowConfig("final-opinion-review", "异议处理完成后返回鉴定意见书送审稿编制")),
                transition("NEXT_FLOW_DECISION", "ISSUE_OPINION", "APPROVE", "寄送完成，进入出具鉴定意见书", "form.nextRecommendation == '进入出具鉴定意见书'", 1, 101,
                        subflowConfig("issue-opinion", "法院函件处理完成后进入出具鉴定意见书")),
                transition("NEXT_FLOW_DECISION", "END", "COMPLETE", "法院函件处理完成并归档", "form.nextRecommendation == '归档'", 1, 102),
                transition("NEXT_FLOW_DECISION", "DELIVERY_RELATED_LETTER", "RETURN", "退回补充发函记录", null, 1, 103),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 110),
                transition("ISSUE_OPINION", "END", "COMPLETE", "出具鉴定意见书子流程已触发", null, 1, 111),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 112)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：收到法院其他函件（含异议函）",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-收到法院其他函件",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest courtAppearanceFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("linkedWorkflowCode", "关联原流程", "text", "流程基础", true, false),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("financeId", "财务人员", "user", "流程基础", true, true),
                field("courtNoticeUploaded", "出庭通知已上传", "boolean", "出庭通知", true, false),
                field("courtName", "法院名称", "text", "出庭通知", true, false),
                field("noticeReceivedDate", "收到出庭通知日期", "date", "出庭通知", true, false),
                field("appearanceDate", "出庭日期", "date", "出庭通知", true, false),
                field("appearanceLocation", "出庭地点", "text", "出庭通知", true, false),
                field("projectNoConfirmed", "项目编号已确认", "boolean", "出庭通知", true, false),
                field("appearanceFeeRequired", "是否需要出庭费通知", "boolean", "出庭费通知", true, false),
                field("feeNoticeIssued", "出庭费通知已发出", "boolean", "出庭费通知", false, false),
                field("archiveRetrievalRequired", "是否需要调档", "boolean", "调档", true, false),
                field("archiveBorrowRegisterUploaded", "档案借阅登记表已上传", "boolean", "调档", false, false),
                field("storedInCenterArchive", "是否存放至中心档案室", "boolean", "调档", false, false),
                field("centerArchiveHandled", "中心档案室线下调档已留痕", "boolean", "调档", false, false),
                field("archiveRetrieved", "档案已调取", "boolean", "调档", false, false),
                field("appearancePreparationCompleted", "出庭准备已完成", "boolean", "出庭准备", false, false),
                field("appearancePreparationFilesUploaded", "出庭准备文件已上传", "boolean", "出庭准备", false, false),
                field("appearanceStatusEntered", "已进入出庭状态", "boolean", "出庭登记", false, false),
                field("appearanceCompleted", "已完成出庭", "boolean", "出庭登记", false, false),
                field("appearanceSummary", "出庭情况摘要", "textarea", "出庭登记", false, false),
                field("postAppearanceMaterialsUploaded", "出庭后材料已整理上传", "boolean", "出庭后整理", false, false),
                field("nextRecommendation", "后续处理", "select", "后续流程", false, false,
                        List.of("返回鉴定意见书送审稿编制", "进入出具鉴定意见书", "归档")),
                field("archiveConfirmed", "归档材料已确认", "boolean", "后续流程", false, false),
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
                        "groups", List.of("流程基础", "出庭通知", "出庭费通知", "调档", "出庭准备", "出庭登记", "出庭后整理", "后续流程", "办理意见")
                )),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "appearanceFeeRequired == true", "then", "feeNoticeIssued == true"),
                                Map.of("if", "archiveRetrievalRequired == true", "then", "archiveBorrowRegisterUploaded == true"),
                                Map.of("if", "storedInCenterArchive == true", "then", "centerArchiveHandled == true"),
                                Map.of("if", "appearanceCompleted == true", "then", "postAppearanceMaterialsUploaded == true"),
                                Map.of("if", "archiveConfirmed == true", "then", "postAppearanceMaterialsUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "出庭通知", Map.of("roles", List.of("项目负责人", "项目辅助人")),
                                "出庭费通知", Map.of("roles", List.of("财务人员")),
                                "调档", Map.of("roles", List.of("档案管理员")),
                                "出庭准备", Map.of("roles", List.of("项目负责人", "项目辅助人")),
                                "出庭登记", Map.of("roles", List.of("项目负责人")),
                                "出庭后整理", Map.of("roles", List.of("项目辅助人")),
                                "后续流程", Map.of("roles", List.of("项目负责人", "档案管理员"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-收到出庭通知",
                        "branchFields", List.of("appearanceFeeRequired", "archiveRetrievalRequired", "appearanceCompleted", "nextRecommendation", "archiveConfirmed")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-收到出庭通知')",
                        "autoArchiveTitle", "concat(caseNo,'/收到出庭通知/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "收到出庭通知后，由项目负责人登记通知，财务处理出庭费通知，档案管理员调档，项目组准备出庭并在出庭后整理材料，再决定回主链或归档"),
                        Map.of("type", "validation", "text", "需要出庭费通知时必须完成通知；需要调档时必须完成调档；完成出庭后必须整理并上传出庭后材料"),
                        Map.of("type", "archive", "text", "出庭通知、出庭费通知、调档记录、出庭登记及出庭后材料在节点完成时自动归档保存")
                ))
        );
    }

    private WorkflowDesignRequest courtAppearanceWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_REGISTER", "项目负责人确认出庭通知和项目编号", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 10),
                node("PAYMENT_NOTICE", "发送出庭费缴费通知书", "task", "candidate", "档案管理员", 1, 24, true, "payment-notice", 20),
                node("ARCHIVE_RETRIEVAL", "档案管理员上传借阅登记并调档", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 30),
                node("CENTER_ARCHIVE_RETRIEVAL", "中心档案室线下调档留痕", "task", "candidate", "中心档案管理员", 1, 24, true, workflow.formCode(), 35),
                node("APPEARANCE_PREPARE", "项目负责人等待并上传出庭准备文件", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 40),
                node("COURT_APPEARANCE", "项目负责人进入出庭状态并登记出庭情况", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 50),
                node("POST_APPEARANCE", "项目负责人整理出庭后材料", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 60),
                node("NEXT_FLOW_DECISION", "项目负责人确认后续流程", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 70),
                node("FINAL_OPINION_REVIEW", "返回鉴定意见书送审稿编制", "task", "candidate", "项目负责人", 1, 24, true, "final-opinion-review", 80),
                node("ISSUE_OPINION", "进入出具鉴定意见书", "task", "candidate", "项目负责人", 1, 24, true, "issue-opinion", 90),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 100),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 110)
        );

        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_REGISTER", "APPROVE", "进入收到出庭通知", null, 0, 10),
                transition("PROJECT_REGISTER", "PAYMENT_NOTICE", "APPROVE", "需要出庭费通知，发起交费通知流程", "form.appearanceFeeRequired == true", 1, 20,
                        subflowConfig("payment-notice", "出庭费通知复用交费通知书及相关函件流程")),
                transition("PROJECT_REGISTER", "ARCHIVE_RETRIEVAL", "APPROVE", "无需出庭费通知，直接调档", "form.appearanceFeeRequired == false", 1, 21),
                transition("PAYMENT_NOTICE", "ARCHIVE_RETRIEVAL", "COMPLETE", "出庭费通知完成，转档案管理员调档", null, 1, 30),
                transition("ARCHIVE_RETRIEVAL", "CENTER_ARCHIVE_RETRIEVAL", "APPROVE", "档案存放至中心档案室，线下调档留痕", "form.storedInCenterArchive == true", 1, 40),
                transition("ARCHIVE_RETRIEVAL", "APPEARANCE_PREPARE", "APPROVE", "非中心档案或无需调档，转项目负责人准备出庭", "form.storedInCenterArchive == false", 1, 41),
                transition("ARCHIVE_RETRIEVAL", "PROJECT_REGISTER", "RETURN", "退回项目负责人重新确认出庭安排", null, 1, 42),
                transition("CENTER_ARCHIVE_RETRIEVAL", "APPEARANCE_PREPARE", "APPROVE", "中心档案室调档完成，转项目负责人准备出庭", null, 1, 45),
                transition("CENTER_ARCHIVE_RETRIEVAL", "ARCHIVE_RETRIEVAL", "RETURN", "退回补充借阅登记", null, 1, 46),
                transition("APPEARANCE_PREPARE", "COURT_APPEARANCE", "APPROVE", "出庭材料准备完成", "form.appearancePreparationCompleted == true", 1, 50),
                transition("APPEARANCE_PREPARE", "ARCHIVE_RETRIEVAL", "RETURN", "退回重新调档", null, 1, 51),
                transition("COURT_APPEARANCE", "POST_APPEARANCE", "APPROVE", "完成出庭并整理后续材料", "form.appearanceCompleted == true", 1, 60),
                transition("COURT_APPEARANCE", "APPEARANCE_PREPARE", "RETURN", "退回重新准备出庭材料", "form.appearanceCompleted == false", 1, 61),
                transition("POST_APPEARANCE", "NEXT_FLOW_DECISION", "APPROVE", "出庭后材料整理完成", null, 1, 70),
                transition("POST_APPEARANCE", "COURT_APPEARANCE", "RETURN", "退回补充出庭登记信息", null, 1, 71),
                transition("NEXT_FLOW_DECISION", "FINAL_OPINION_REVIEW", "APPROVE", "返回鉴定意见书送审稿编制", "form.nextRecommendation == '返回鉴定意见书送审稿编制'", 1, 80,
                        subflowConfig("final-opinion-review", "出庭完成后返回鉴定意见书送审稿编制")),
                transition("NEXT_FLOW_DECISION", "ISSUE_OPINION", "APPROVE", "进入出具鉴定意见书", "form.nextRecommendation == '进入出具鉴定意见书'", 1, 81,
                        subflowConfig("issue-opinion", "出庭完成后进入出具鉴定意见书")),
                transition("NEXT_FLOW_DECISION", "ARCHIVE_SUBFLOW", "APPROVE", "进入归档", "form.nextRecommendation == '归档'", 1, 82,
                        subflowConfig("archive", "出庭完成后进入归档")),
                transition("NEXT_FLOW_DECISION", "POST_APPEARANCE", "RETURN", "退回补充出庭后材料", null, 1, 83),
                transition("FINAL_OPINION_REVIEW", "END", "COMPLETE", "鉴定意见书送审稿编制子流程已触发", null, 1, 90),
                transition("ISSUE_OPINION", "END", "COMPLETE", "出具鉴定意见书子流程已触发", null, 1, 91),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 92)
        );

        return new WorkflowDesignRequest(
                workflow.code(),
                workflow.name(),
                "judicial",
                workflow.formCode(),
                "由司法鉴定使用手册高保真校准：收到出庭通知",
                toJson(Map.of(
                        "entryMode", workflow.entryMode(),
                        "highFidelity", true,
                        "flowNameTemplate", "${caseNo}-收到出庭通知",
                        "keyRules", workflow.keyRules(),
                        "nextFlows", workflow.nextFlows(),
                        "autoArchive", true,
                        "preserveVersions", true
                )),
                nodes,
                transitions
        );
    }

    private FormDesignRequest withdrawCaseLetterFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("linkedWorkflowCode", "关联原流程", "text", "流程基础", false, false),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("withdrawLetterReceivedDate", "撤案函收函日期", "date", "撤案登记", false, false),
                field("withdrawReason", "撤案原因", "textarea", "撤案登记", false, false),
                field("refundRequired", "是否需要退费", "boolean", "项目负责人判断", false, false),
                field("decisionSummary", "处理结论说明", "textarea", "项目负责人判断", false, false),
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
                toJson(Map.of("layout", "grouped", "groups", List.of("流程基础", "撤案登记", "项目负责人判断", "办理意见"))),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles()
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "撤案登记", Map.of("roles", List.of("收案员", "项目负责人")),
                                "项目负责人判断", Map.of("roles", List.of("项目负责人"))
                        )
                )),
                toJson(Map.of(
                        "flowNameTemplate", "${caseNo}-收到撤案函",
                        "branchFields", List.of("refundRequired")
                )),
                toJson(Map.of(
                        "flowName", "concat(caseNo,'-收到撤案函')",
                        "autoArchiveTitle", "concat(caseNo,'/收到撤案函/',nodeName)"
                )),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "收到撤案函后登记收函信息，由项目负责人判断进入退费或直接终止鉴定"),
                        Map.of("type", "archive", "text", "撤案函、处理结论和后续流转记录在节点完成时自动归档")
                ))
        );
    }

    private WorkflowDesignRequest withdrawCaseLetterWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("LETTER_REGISTER", "登记撤案函", "task", "candidate", "收案员", 1, 24, true, workflow.formCode(), 10),
                node("PROJECT_DECISION", "项目负责人判断是否退费", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 20),
                node("REFUND", "进入退费", "task", "candidate", "项目负责人", 1, 24, true, "refund", 30),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 40),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 50)
        );
        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "LETTER_REGISTER", "APPROVE", "进入收到撤案函", null, 0, 10),
                transition("LETTER_REGISTER", "PROJECT_DECISION", "APPROVE", "转项目负责人判断", null, 1, 20),
                transition("PROJECT_DECISION", "REFUND", "APPROVE", "需要退费，进入退费", "form.refundRequired == true", 1, 30,
                        subflowConfig("refund", "撤案后需要退费，自动进入退费流程")),
                transition("PROJECT_DECISION", "TERMINATE_APPRAISAL", "APPROVE", "无需退费，进入终止鉴定", "form.refundRequired == false", 1, 31,
                        subflowConfig("terminate-appraisal", "撤案后无需退费，自动进入终止鉴定")),
                transition("PROJECT_DECISION", "LETTER_REGISTER", "RETURN", "退回补充撤案函登记", null, 1, 32),
                transition("REFUND", "END", "COMPLETE", "退费子流程已触发", null, 1, 40),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 41)
        );
        return new WorkflowDesignRequest(
                workflow.code(), workflow.name(), "judicial", workflow.formCode(),
                "由司法鉴定使用手册高保真校准：收到撤案函",
                toJson(Map.of("entryMode", workflow.entryMode(), "highFidelity", true, "flowNameTemplate", "${caseNo}-收到撤案函",
                        "keyRules", workflow.keyRules(), "nextFlows", workflow.nextFlows(), "autoArchive", true, "preserveVersions", true)),
                nodes, transitions
        );
    }

    private FormDesignRequest refundFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("financeId", "财务人员", "user", "流程基础", true, true),
                field("contractChangeCompleted", "合同变更已完成", "boolean", "退费准备", false, false),
                field("revenueConfirmed", "收入确认已完成", "boolean", "退费准备", false, false),
                field("refundApplicationSubmitted", "退费申请已提交", "boolean", "退费申请", false, false),
                field("paymentCompleted", "财务打款已完成", "boolean", "财务打款", false, false),
                field("paymentDate", "打款日期", "date", "财务打款", false, false),
                field("paymentVoucherUploaded", "打款结果已回传", "boolean", "财务打款", false, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(), form.name(), "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of("layout", "grouped", "groups", List.of("流程基础", "退费准备", "退费申请", "财务打款", "办理意见"))),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "paymentCompleted == true", "then", "paymentVoucherUploaded == true"),
                                Map.of("if", "paymentCompleted == true", "then", "paymentDate != null")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "退费准备", Map.of("roles", List.of("项目负责人")),
                                "退费申请", Map.of("roles", List.of("档案管理员", "项目负责人")),
                                "财务打款", Map.of("roles", List.of("财务人员"))
                        )
                )),
                toJson(Map.of("flowNameTemplate", "${caseNo}-退费", "branchFields", List.of("paymentCompleted"))),
                toJson(Map.of("flowName", "concat(caseNo,'-退费')", "autoArchiveTitle", "concat(caseNo,'/退费/',nodeName)")),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目负责人完成合同变更和收入确认，提交退费申请后由财务打款，完成后进入终止鉴定"),
                        Map.of("type", "archive", "text", "退费申请、合同变更、打款记录和后续触发记录在节点完成时自动归档")
                ))
        );
    }

    private WorkflowDesignRequest refundWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("PROJECT_PREPARE", "项目负责人完成合同变更与收入确认", "task", "candidate", "项目负责人", 1, 48, true, workflow.formCode(), 10),
                node("ARCHIVIST_APPLY", "档案管理员登记退费申请", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 20),
                node("FINANCE_PAYMENT", "财务打款并回传结果", "task", "candidate", "财务人员", 1, 48, true, workflow.formCode(), 30),
                node("TERMINATE_APPRAISAL", "进入终止鉴定", "task", "candidate", "项目负责人", 1, 24, true, "terminate-appraisal", 40),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 50)
        );
        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "PROJECT_PREPARE", "APPROVE", "进入退费", null, 0, 10),
                transition("PROJECT_PREPARE", "ARCHIVIST_APPLY", "APPROVE", "转档案管理员登记退费申请", null, 1, 20),
                transition("ARCHIVIST_APPLY", "FINANCE_PAYMENT", "APPROVE", "转财务打款", null, 1, 30),
                transition("ARCHIVIST_APPLY", "PROJECT_PREPARE", "RETURN", "退回项目负责人补充退费准备材料", null, 1, 31),
                transition("FINANCE_PAYMENT", "TERMINATE_APPRAISAL", "APPROVE", "打款完成，进入终止鉴定", "form.paymentCompleted == true", 1, 40,
                        subflowConfig("terminate-appraisal", "退费打款完成后自动进入终止鉴定")),
                transition("FINANCE_PAYMENT", "ARCHIVIST_APPLY", "RETURN", "退回补充退费申请或打款信息", "form.paymentCompleted == false", 1, 41),
                transition("TERMINATE_APPRAISAL", "END", "COMPLETE", "终止鉴定子流程已触发", null, 1, 50)
        );
        return new WorkflowDesignRequest(
                workflow.code(), workflow.name(), "judicial", workflow.formCode(),
                "由司法鉴定使用手册高保真校准：退费",
                toJson(Map.of("entryMode", workflow.entryMode(), "highFidelity", true, "flowNameTemplate", "${caseNo}-退费",
                        "keyRules", workflow.keyRules(), "nextFlows", workflow.nextFlows(), "autoArchive", true, "preserveVersions", true)),
                nodes, transitions
        );
    }

    private FormDesignRequest terminateAppraisalFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("projectLeaderId", "项目负责人", "user", "流程基础", true, true),
                field("projectAssistantId", "项目辅助人", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("terminationType", "终止文书类型", "select", "终止文书", false, false, List.of("鉴定终止函", "鉴定终止确认函")),
                field("terminationReason", "终止原因", "textarea", "终止文书", false, false),
                field("draftCompleted", "终止文书草稿已完成", "boolean", "终止文书", false, false),
                field("projectReviewPassed", "项目负责人审核通过", "boolean", "项目负责人审核", false, false),
                field("sealRequired", "是否需要用章", "boolean", "用章与回传", false, false),
                field("sealedTerminationUploaded", "终止文书盖章件已上传", "boolean", "用章与回传", false, false),
                field("archiveConfirmed", "归档材料已确认", "boolean", "归档", false, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(), form.name(), "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of("layout", "grouped", "groups", List.of("流程基础", "终止文书", "项目负责人审核", "用章与回传", "归档", "办理意见"))),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "projectReviewPassed == true", "then", "draftCompleted == true"),
                                Map.of("if", "sealRequired == true", "then", "sealedTerminationUploaded == true"),
                                Map.of("if", "archiveConfirmed == true", "then", "sealedTerminationUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "终止文书", Map.of("roles", List.of("项目辅助人")),
                                "项目负责人审核", Map.of("roles", List.of("项目负责人")),
                                "用章与回传", Map.of("roles", List.of("项目负责人", "档案管理员")),
                                "归档", Map.of("roles", List.of("档案管理员"))
                        )
                )),
                toJson(Map.of("flowNameTemplate", "${caseNo}-终止鉴定", "branchFields", List.of("projectReviewPassed", "sealRequired", "archiveConfirmed"))),
                toJson(Map.of("flowName", "concat(caseNo,'-终止鉴定')", "autoArchiveTitle", "concat(caseNo,'/终止鉴定/',nodeName)")),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "项目辅助人编制终止文书，项目负责人审核后进入用章、回传并归档"),
                        Map.of("type", "archive", "text", "终止原因、终止文书、盖章件和归档确认在节点完成时自动归档")
                ))
        );
    }

    private WorkflowDesignRequest terminateAppraisalWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ASSISTANT_DRAFT", "项目辅助人编制终止文书", "task", "candidate", "项目辅助人", 1, 48, true, workflow.formCode(), 10),
                node("PROJECT_REVIEW", "项目负责人审核终止文书", "task", "candidate", "项目负责人", 1, 24, true, workflow.formCode(), 20),
                node("SEAL_APPLICATION", "发起用章申请", "task", "candidate", "项目负责人", 1, 24, true, "seal-application", 30),
                node("SEALED_UPLOAD", "档案管理员回传终止文书盖章件", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 40),
                node("ARCHIVE_SUBFLOW", "进入归档子流程", "task", "candidate", "档案管理员", 1, 24, true, "archive", 50),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 60)
        );
        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ASSISTANT_DRAFT", "APPROVE", "进入终止鉴定", null, 0, 10),
                transition("ASSISTANT_DRAFT", "PROJECT_REVIEW", "APPROVE", "转项目负责人审核", null, 1, 20),
                transition("PROJECT_REVIEW", "SEAL_APPLICATION", "APPROVE", "审核通过且需要用章", "form.projectReviewPassed == true", 1, 30,
                        subflowConfig("seal-application", "终止文书审核通过后自动进入用章流程")),
                transition("PROJECT_REVIEW", "ASSISTANT_DRAFT", "RETURN", "退回项目辅助人修改终止文书", "form.projectReviewPassed == false", 1, 31),
                transition("SEAL_APPLICATION", "SEALED_UPLOAD", "COMPLETE", "用章流程完成", null, 1, 40),
                transition("SEALED_UPLOAD", "ARCHIVE_SUBFLOW", "APPROVE", "终止文书回传完成，进入归档", "form.archiveConfirmed == true", 1, 50,
                        subflowConfig("archive", "终止文书回传并确认归档材料后自动进入归档流程")),
                transition("SEALED_UPLOAD", "PROJECT_REVIEW", "RETURN", "退回项目负责人复核", null, 1, 51),
                transition("ARCHIVE_SUBFLOW", "END", "COMPLETE", "归档子流程已触发", null, 1, 60)
        );
        return new WorkflowDesignRequest(
                workflow.code(), workflow.name(), "judicial", workflow.formCode(),
                "由司法鉴定使用手册高保真校准：终止鉴定",
                toJson(Map.of("entryMode", workflow.entryMode(), "highFidelity", true, "flowNameTemplate", "${caseNo}-终止鉴定",
                        "keyRules", workflow.keyRules(), "nextFlows", workflow.nextFlows(), "autoArchive", true, "preserveVersions", true)),
                nodes, transitions
        );
    }

    private FormDesignRequest archiveFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("centralArchivistId", "中心档案管理员", "user", "流程基础", true, true),
                field("mailerId", "邮寄人员", "user", "流程基础", true, true),
                field("projectArchiveUploaded", "项目档案已上传", "boolean", "归档整理", false, false),
                field("paperScansUploaded", "纸质扫描件已上传", "boolean", "归档整理", false, false),
                field("electronicArchiveLocation", "电子归档地址", "text", "归档整理", false, false),
                field("deliveryRoute", "入库方式", "select", "移交与入库", false, false, List.of("邮寄入库", "直接中心审核")),
                field("mailTrackingNo", "邮寄单号", "text", "移交与入库", false, false),
                field("centralArchiveApproved", "中心档案管理员审核通过", "boolean", "中心审核", false, false),
                field("archiveRoomLocation", "档案室入库位置", "text", "中心审核", false, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(), form.name(), "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of("layout", "grouped", "groups", List.of("流程基础", "归档整理", "移交与入库", "中心审核", "办理意见"))),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "deliveryRoute == '邮寄入库'", "then", "mailTrackingNo != null"),
                                Map.of("if", "centralArchiveApproved == true", "then", "archiveRoomLocation != null")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "归档整理", Map.of("roles", List.of("档案管理员")),
                                "移交与入库", Map.of("roles", List.of("档案管理员", "邮寄人员")),
                                "中心审核", Map.of("roles", List.of("中心档案管理员"))
                        )
                )),
                toJson(Map.of("flowNameTemplate", "${caseNo}-归档", "branchFields", List.of("deliveryRoute", "centralArchiveApproved"))),
                toJson(Map.of("flowName", "concat(caseNo,'-归档')", "autoArchiveTitle", "concat(caseNo,'/归档/',nodeName)")),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "档案管理员整理项目档案和纸质扫描件，按入库方式移交，中心档案管理员审核后完成入库"),
                        Map.of("type", "archive", "text", "归档整理记录、电子归档地址、邮寄记录和中心入库结果自动留痕")
                ))
        );
    }

    private WorkflowDesignRequest archiveWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("ARCHIVIST_PREPARE", "档案管理员整理项目档案", "task", "candidate", "档案管理员", 1, 48, true, workflow.formCode(), 10),
                node("MAIL_TRANSFER", "邮寄人员移交档案", "task", "candidate", "邮寄人员", 1, 24, true, workflow.formCode(), 20),
                node("CENTRAL_REVIEW", "中心档案管理员审核并入库", "task", "candidate", "中心档案管理员", 1, 48, true, workflow.formCode(), 30),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 40)
        );
        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "ARCHIVIST_PREPARE", "APPROVE", "进入归档", null, 0, 10),
                transition("ARCHIVIST_PREPARE", "MAIL_TRANSFER", "APPROVE", "通过邮寄入库", "form.deliveryRoute == '邮寄入库'", 1, 20),
                transition("ARCHIVIST_PREPARE", "CENTRAL_REVIEW", "APPROVE", "直接进入中心审核", "form.deliveryRoute == '直接中心审核'", 1, 21),
                transition("MAIL_TRANSFER", "CENTRAL_REVIEW", "APPROVE", "邮寄移交完成，转中心审核", null, 1, 30),
                transition("MAIL_TRANSFER", "ARCHIVIST_PREPARE", "RETURN", "退回重新整理归档材料", null, 1, 31),
                transition("CENTRAL_REVIEW", "END", "APPROVE", "中心档案管理员审核通过并入库", "form.centralArchiveApproved == true", 1, 40),
                transition("CENTRAL_REVIEW", "ARCHIVIST_PREPARE", "RETURN", "退回补充归档材料", "form.centralArchiveApproved == false", 1, 41)
        );
        return new WorkflowDesignRequest(
                workflow.code(), workflow.name(), "judicial", workflow.formCode(),
                "由司法鉴定使用手册高保真校准：归档",
                toJson(Map.of("entryMode", workflow.entryMode(), "highFidelity", true, "flowNameTemplate", "${caseNo}-归档",
                        "keyRules", workflow.keyRules(), "nextFlows", workflow.nextFlows(), "autoArchive", true, "preserveVersions", true)),
                nodes, transitions
        );
    }

    private FormDesignRequest sealApplicationFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("applicantId", "业务人员", "user", "流程基础", true, true),
                field("archivistId", "档案管理员", "user", "流程基础", true, true),
                field("sealOperatorId", "盖章经办人", "user", "流程基础", true, true),
                field("applicationReason", "用章原因", "textarea", "申请信息", false, false),
                field("sealMode", "盖章方式", "select", "申请信息", false, false, List.of("线下盖章", "电子盖章")),
                field("applicationFilesPrepared", "申请文件已备齐", "boolean", "申请信息", false, false),
                field("archivistReviewed", "档案管理员已审核", "boolean", "档案审核", false, false),
                field("sealCompleted", "已完成盖章", "boolean", "盖章处理", false, false),
                field("sealedScanUploaded", "盖章扫描件已上传", "boolean", "盖章处理", false, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(), form.name(), "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of("layout", "grouped", "groups", List.of("流程基础", "申请信息", "档案审核", "盖章处理", "办理意见"))),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "sealCompleted == true", "then", "sealedScanUploaded == true")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "申请信息", Map.of("roles", List.of("业务人员")),
                                "档案审核", Map.of("roles", List.of("档案管理员")),
                                "盖章处理", Map.of("roles", List.of("业务人员", "档案管理员"))
                        )
                )),
                toJson(Map.of("flowNameTemplate", "${caseNo}-用章申请", "branchFields", List.of("archivistReviewed", "sealCompleted"))),
                toJson(Map.of("flowName", "concat(caseNo,'-用章申请')", "autoArchiveTitle", "concat(caseNo,'/用章流程/',nodeName)")),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "申请人提交盖章文件，档案管理员审核后由盖章经办人完成线下或电子盖章，并回传扫描件"),
                        Map.of("type", "archive", "text", "申请文件、审核记录和盖章扫描件在节点完成时自动归档")
                ))
        );
    }

    private WorkflowDesignRequest sealApplicationWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("APPLICANT_SUBMIT", "申请人提交用章申请", "task", "candidate", "业务人员", 1, 24, true, workflow.formCode(), 10),
                node("ARCHIVIST_REVIEW", "档案管理员审核申请材料", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 20),
                node("SEAL_OPERATOR", "盖章经办人完成盖章", "task", "candidate", "业务人员", 1, 24, true, workflow.formCode(), 30),
                node("ARCHIVIST_UPLOAD", "档案管理员回传盖章扫描件", "task", "candidate", "档案管理员", 1, 24, true, workflow.formCode(), 40),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 50)
        );
        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "APPLICANT_SUBMIT", "APPROVE", "进入用章流程", null, 0, 10),
                transition("APPLICANT_SUBMIT", "ARCHIVIST_REVIEW", "APPROVE", "转档案管理员审核", null, 1, 20),
                transition("ARCHIVIST_REVIEW", "SEAL_OPERATOR", "APPROVE", "审核通过，转盖章经办人", "form.archivistReviewed == true", 1, 30),
                transition("ARCHIVIST_REVIEW", "APPLICANT_SUBMIT", "RETURN", "退回申请人补充材料", "form.archivistReviewed == false", 1, 31),
                transition("SEAL_OPERATOR", "ARCHIVIST_UPLOAD", "APPROVE", "盖章完成，回传扫描件", "form.sealCompleted == true", 1, 40),
                transition("SEAL_OPERATOR", "ARCHIVIST_REVIEW", "RETURN", "退回重新审核或重新盖章", "form.sealCompleted == false", 1, 41),
                transition("ARCHIVIST_UPLOAD", "END", "APPROVE", "盖章扫描件回传完成", null, 1, 50)
        );
        return new WorkflowDesignRequest(
                workflow.code(), workflow.name(), "judicial", workflow.formCode(),
                "由司法鉴定使用手册高保真校准：用章流程",
                toJson(Map.of("entryMode", workflow.entryMode(), "highFidelity", true, "flowNameTemplate", "${caseNo}-用章申请",
                        "keyRules", workflow.keyRules(), "nextFlows", workflow.nextFlows(), "autoArchive", true, "preserveVersions", true)),
                nodes, transitions
        );
    }

    private FormDesignRequest expenseReimbursementFormRequest(JudicialFormDefinitionDto form) {
        List<Map<String, Object>> fields = List.of(
                field("caseNo", "案件号", "text", "流程基础", true, true),
                field("flowName", "流程名称", "text", "流程基础", false, true),
                field("initiatorId", "业务人员", "user", "流程基础", true, true),
                field("financeId", "财务人员", "user", "流程基础", true, true),
                field("expenseSummary", "报销事项", "textarea", "报销申请", false, false),
                field("expenseAmount", "报销金额", "number", "报销申请", false, false),
                field("invoiceSummary", "发票汇总", "textarea", "报销申请", false, false),
                field("financeProcessed", "财务已处理", "boolean", "财务处理", false, false),
                field("financeResult", "财务处理结果", "select", "财务处理", false, false, List.of("已报销", "退回补充")),
                field("paymentDate", "实际支付时间", "date", "财务处理", false, false),
                field("handlerOpinion", "办理意见", "textarea", "办理意见", false, false)
        );
        return new FormDesignRequest(
                form.code(), form.name(), "司法鉴定",
                toJson(toFileRules(form.inputFiles(), "input")),
                toJson(toFileRules(form.outputFiles(), "output")),
                toJson(form.versionedArtifacts()),
                toJson(fields),
                toJson(Map.of("layout", "grouped", "groups", List.of("流程基础", "报销申请", "财务处理", "办理意见"))),
                toJson(Map.of(
                        "requiredFields", fields.stream().filter(item -> Boolean.TRUE.equals(item.get("required"))).map(item -> item.get("field")).toList(),
                        "requiredInputs", form.inputFiles(),
                        "requiredOutputs", form.outputFiles(),
                        "crossFieldRules", List.of(
                                Map.of("if", "financeResult == '已报销'", "then", "paymentDate != null")
                        )
                )),
                toJson(Map.of(
                        "groups", Map.of(
                                "流程基础", Map.of("readOnly", true),
                                "报销申请", Map.of("roles", List.of("业务人员")),
                                "财务处理", Map.of("roles", List.of("财务人员"))
                        )
                )),
                toJson(Map.of("flowNameTemplate", "${caseNo}-财务报销", "branchFields", List.of("financeResult"))),
                toJson(Map.of("flowName", "concat(caseNo,'-财务报销')", "autoArchiveTitle", "concat(caseNo,'/财务报销/',nodeName)")),
                toJson(Map.of("enabled", true, "inputFiles", form.inputFiles(), "outputFiles", form.outputFiles(), "duplicatePolicy", "warn")),
                "[]",
                toJson(List.of(
                        Map.of("type", "business", "text", "发起人提交报销材料后由财务处理并登记实际支付结果"),
                        Map.of("type", "archive", "text", "报销材料、发票汇总和财务处理结果在节点完成时自动归档")
                ))
        );
    }

    private WorkflowDesignRequest expenseReimbursementWorkflowRequest(JudicialWorkflowDefinitionDto workflow) {
        List<WorkflowNodeRequest> nodes = List.of(
                node("START", "开始", "start", "single", null, 0, 0, false, null, 0),
                node("INITIATOR_SUBMIT", "发起人提交报销材料", "task", "candidate", "业务人员", 1, 24, true, workflow.formCode(), 10),
                node("FINANCE_PROCESS", "财务处理报销", "task", "candidate", "财务人员", 1, 48, true, workflow.formCode(), 20),
                node("END", "流程结束", "end", "single", null, 0, 0, false, null, 30)
        );
        List<WorkflowTransitionRequest> transitions = List.of(
                transition("START", "INITIATOR_SUBMIT", "APPROVE", "进入财务报销", null, 0, 10),
                transition("INITIATOR_SUBMIT", "FINANCE_PROCESS", "APPROVE", "转财务处理", null, 1, 20),
                transition("FINANCE_PROCESS", "END", "APPROVE", "财务处理完成", "form.financeResult == '已报销'", 1, 30),
                transition("FINANCE_PROCESS", "INITIATOR_SUBMIT", "RETURN", "退回补充报销材料", "form.financeResult == '退回补充'", 1, 31)
        );
        return new WorkflowDesignRequest(
                workflow.code(), workflow.name(), "judicial", workflow.formCode(),
                "由司法鉴定使用手册高保真校准：财务报销",
                toJson(Map.of("entryMode", workflow.entryMode(), "highFidelity", true, "flowNameTemplate", "${caseNo}-财务报销",
                        "keyRules", workflow.keyRules(), "nextFlows", workflow.nextFlows(), "autoArchive", true, "preserveVersions", true)),
                nodes, transitions
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
