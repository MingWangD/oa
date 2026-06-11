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
import java.util.List;
import java.util.Locale;
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
            String nodeCode = "N" + String.format(Locale.ROOT, "%02d", index + 1);
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
