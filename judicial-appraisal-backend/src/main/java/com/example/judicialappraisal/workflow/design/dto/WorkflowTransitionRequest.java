package com.example.judicialappraisal.workflow.design.dto;

public record WorkflowTransitionRequest(
        String fromNodeCode,
        String toNodeCode,
        String actionCode,
        String actionName,
        Integer requireReason,
        Integer requireOpinion,
        String conditionExpression,
        String transitionConfigJson,
        Integer enabled,
        Integer sortNo
) {
}
