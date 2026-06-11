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
                transition("DEPT_REVIEW", "REJECT_ACCEPTANCE", "APPROVE", "不予受理", "form.entrustAccepted == false", 1, 41),
                transition("PROJECT_DECISION", "ASSISTANT_NOTICE", "APPROVE", "告知项目辅助人", null, 0, 50),
                transition("PROJECT_DECISION", "MATERIAL_RECEIVE", "APPROVE", "同步收案员材料接收", "form.materialReceiveRequired == true", 1, 51),
                transition("PROJECT_DECISION", "PRELIMINARY_SURVEY", "APPROVE", "进入初步勘验", "form.preliminarySurveyRequired == true", 1, 52),
                transition("PROJECT_DECISION", "PAYMENT_NOTICE", "APPROVE", "进入发交费通知", "form.preliminarySurveyRequired == false", 1, 53),
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
        return new WorkflowTransitionRequest(from, to, actionCode, actionName,
                "RETURN".equals(actionCode) ? 1 : 0,
                1,
                condition,
                toJson(Map.of("archiveOnLeave", archiveOnLeave == null ? 0 : archiveOnLeave)),
                1,
                sortNo);
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
