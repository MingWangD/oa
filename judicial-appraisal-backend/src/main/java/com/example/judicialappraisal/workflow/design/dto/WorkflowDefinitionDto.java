package com.example.judicialappraisal.workflow.design.dto;

import java.time.LocalDateTime;
import java.util.List;

public record WorkflowDefinitionDto(
        Long id,
        String wfCode,
        String wfName,
        String wfType,
        String formCode,
        Integer versionNo,
        Integer enabled,
        String publishStatus,
        String remark,
        String definitionJson,
        Long sourceWfId,
        Long publishedBy,
        LocalDateTime publishedTime,
        Integer immutableFlag,
        LocalDateTime createdTime,
        LocalDateTime updatedTime,
        List<WorkflowNodeDto> nodes,
        List<WorkflowTransitionDto> transitions
) {
    public WorkflowDefinitionDto {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
    }
}
