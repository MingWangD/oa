package com.example.judicialappraisal.workflow.design;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.workflow.design.dto.FormDefinitionDto;
import com.example.judicialappraisal.workflow.design.dto.FormDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.FormVersionDto;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDefinitionDto;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowVersionDto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/designer")
public class DesignerController {

    private final FormDesignService formDesignService;
    private final WorkflowDesignService workflowDesignService;

    public DesignerController(FormDesignService formDesignService,
                              WorkflowDesignService workflowDesignService) {
        this.formDesignService = formDesignService;
        this.workflowDesignService = workflowDesignService;
    }

    @GetMapping("/forms")
    @PreAuthorize("hasAuthority('workflow:form-design') or hasRole('ADMIN')")
    public ApiResponse<List<FormDefinitionDto>> forms() {
        return ApiResponse.success(formDesignService.listDefinitions());
    }

    @PostMapping("/forms/drafts")
    @PreAuthorize("hasAuthority('workflow:form-design') or hasRole('ADMIN')")
    public ApiResponse<FormVersionDto> saveFormDraft(@Valid @RequestBody FormDesignRequest request) {
        return ApiResponse.success(formDesignService.saveDraft(request));
    }

    @PutMapping("/forms/{formCode}/draft")
    @PreAuthorize("hasAuthority('workflow:form-design') or hasRole('ADMIN')")
    public ApiResponse<FormVersionDto> updateFormDraft(@PathVariable String formCode,
                                                       @Valid @RequestBody FormDesignRequest request) {
        if (!formCode.equals(request.formCode())) {
            throw new com.example.judicialappraisal.common.exception.BusinessException("表单编码不一致");
        }
        return ApiResponse.success(formDesignService.saveDraft(request));
    }

    @GetMapping("/forms/{formCode}/draft")
    @PreAuthorize("hasAuthority('workflow:form-design') or hasRole('ADMIN')")
    public ApiResponse<FormVersionDto> formDraft(@PathVariable String formCode) {
        return ApiResponse.success(formDesignService.getDraft(formCode));
    }

    @GetMapping("/forms/{formCode}/preview")
    @PreAuthorize("hasAuthority('workflow:form-design') or hasRole('ADMIN')")
    public ApiResponse<FormVersionDto> formPreview(@PathVariable String formCode) {
        return ApiResponse.success(formDesignService.preview(formCode));
    }

    @GetMapping("/forms/{formCode}/versions")
    @PreAuthorize("hasAuthority('workflow:form-design') or hasRole('ADMIN')")
    public ApiResponse<List<FormVersionDto>> formVersions(@PathVariable String formCode) {
        return ApiResponse.success(formDesignService.listVersions(formCode));
    }

    @PostMapping("/forms/{formCode}/publish")
    @PreAuthorize("hasAuthority('workflow:form-publish') or hasRole('ADMIN')")
    public ApiResponse<FormVersionDto> publishForm(@PathVariable String formCode) {
        return ApiResponse.success(formDesignService.publish(formCode));
    }

    @PostMapping("/forms/{formCode}/versions/{versionNo}/restore")
    @PreAuthorize("hasAuthority('workflow:form-publish') or hasRole('ADMIN')")
    public ApiResponse<FormVersionDto> restoreForm(@PathVariable String formCode, @PathVariable Integer versionNo) {
        return ApiResponse.success(formDesignService.restore(formCode, versionNo));
    }

    @GetMapping("/workflows")
    @PreAuthorize("hasAuthority('workflow:process-design') or hasRole('ADMIN')")
    public ApiResponse<List<WorkflowDefinitionDto>> workflows() {
        return ApiResponse.success(workflowDesignService.listDefinitions());
    }

    @PostMapping("/workflows/drafts")
    @PreAuthorize("hasAuthority('workflow:process-design') or hasRole('ADMIN')")
    public ApiResponse<WorkflowDefinitionDto> saveWorkflowDraft(@Valid @RequestBody WorkflowDesignRequest request) {
        return ApiResponse.success(workflowDesignService.saveDraft(request));
    }

    @PutMapping("/workflows/{wfCode}/draft")
    @PreAuthorize("hasAuthority('workflow:process-design') or hasRole('ADMIN')")
    public ApiResponse<WorkflowDefinitionDto> updateWorkflowDraft(@PathVariable String wfCode,
                                                                  @Valid @RequestBody WorkflowDesignRequest request) {
        if (!wfCode.equals(request.wfCode())) {
            throw new com.example.judicialappraisal.common.exception.BusinessException("流程编码不一致");
        }
        return ApiResponse.success(workflowDesignService.saveDraft(request));
    }

    @GetMapping("/workflows/{wfCode}/draft")
    @PreAuthorize("hasAuthority('workflow:process-design') or hasRole('ADMIN')")
    public ApiResponse<WorkflowDefinitionDto> workflowDraft(@PathVariable String wfCode) {
        return ApiResponse.success(workflowDesignService.getDraft(wfCode));
    }

    @GetMapping("/workflows/{wfCode}/preview")
    @PreAuthorize("hasAuthority('workflow:process-design') or hasRole('ADMIN')")
    public ApiResponse<WorkflowDefinitionDto> workflowPreview(@PathVariable String wfCode) {
        return ApiResponse.success(workflowDesignService.preview(wfCode));
    }

    @GetMapping("/workflows/{wfCode}/versions")
    @PreAuthorize("hasAuthority('workflow:process-design') or hasRole('ADMIN')")
    public ApiResponse<List<WorkflowVersionDto>> workflowVersions(@PathVariable String wfCode) {
        return ApiResponse.success(workflowDesignService.listVersions(wfCode));
    }

    @PostMapping("/workflows/{wfCode}/publish")
    @PreAuthorize("hasAuthority('workflow:process-publish') or hasRole('ADMIN')")
    public ApiResponse<WorkflowDefinitionDto> publishWorkflow(@PathVariable String wfCode) {
        return ApiResponse.success(workflowDesignService.publish(wfCode));
    }

    @PostMapping("/workflows/{wfCode}/versions/{versionNo}/restore")
    @PreAuthorize("hasAuthority('workflow:process-publish') or hasRole('ADMIN')")
    public ApiResponse<WorkflowDefinitionDto> restoreWorkflow(@PathVariable String wfCode,
                                                              @PathVariable Integer versionNo) {
        return ApiResponse.success(workflowDesignService.restore(wfCode, versionNo));
    }
}
