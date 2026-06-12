package com.example.judicialappraisal.workflow.dto;

import com.example.judicialappraisal.common.enums.ActionCode;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public record WorkflowActionRequest(
        Long taskId,
        @NotNull ActionCode actionCode,
        String opinion,
        String reason,
        // Deprecated compatibility field: next assignee only, never current operator identity.
        Long assigneeId,
        // Deprecated compatibility field: next assignee only, never current operator identity.
        String assigneeName,
        Long nextAssigneeId,
        String nextAssigneeName,
        Map<String, Object> formData,
        List<Long> fileIds) {

    public WorkflowActionRequest(
            Long taskId,
            ActionCode actionCode,
            String opinion,
            String reason,
            Long assigneeId,
            String assigneeName,
            Map<String, Object> formData,
            List<Long> fileIds) {
        this(taskId, actionCode, opinion, reason, assigneeId, assigneeName, null, null, formData, fileIds);
    }

    public Long resolvedNextAssigneeId() {
        return nextAssigneeId != null ? nextAssigneeId : assigneeId;
    }

    public String resolvedNextAssigneeName() {
        return !isBlank(nextAssigneeName) ? nextAssigneeName : assigneeName;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
