package com.example.judicialappraisal.workflow.design.dto;

import java.time.LocalDateTime;

public record WorkflowTransitionDto(
        Long id,
        Long wfId,
        String fromNodeCode,
        String toNodeCode,
        String actionCode,
        String actionName,
        Integer requireReason,
        Integer requireOpinion,
        String conditionExpression,
        String transitionConfigJson,
        Integer enabled,
        Integer sortNo,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
