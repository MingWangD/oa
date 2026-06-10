package com.example.judicialappraisal.workflow.dto;

public record WorkflowActionResult(
        Long caseId,
        Long taskId,
        String actionCode,
        boolean success,
        String message) {
}
