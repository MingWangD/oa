package com.example.judicialappraisal.workflow.design.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record WorkflowDesignRequest(
        @NotBlank String wfCode,
        @NotBlank String wfName,
        @NotBlank String wfType,
        String formCode,
        String remark,
        String definitionJson,
        @Valid @NotEmpty List<WorkflowNodeRequest> nodes,
        @Valid @NotEmpty List<WorkflowTransitionRequest> transitions
) {
}
