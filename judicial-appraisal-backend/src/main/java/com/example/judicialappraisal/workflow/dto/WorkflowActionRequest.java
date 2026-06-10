package com.example.judicialappraisal.workflow.dto;

import com.example.judicialappraisal.common.enums.ActionCode;
import jakarta.validation.constraints.NotNull;

public record WorkflowActionRequest(
        Long taskId,
        @NotNull ActionCode actionCode,
        String opinion,
        String reason,
        Long assigneeId,
        String assigneeName) {
}
