package com.example.judicialappraisal.workflow.dto;

import java.time.LocalDateTime;

public record CaseSubflowSummaryResponse(
        Long id,
        Long caseId,
        Long parentWfInstanceId,
        Long parentTaskId,
        String parentNodeCode,
        Long wfId,
        String wfCode,
        String wfName,
        String subflowType,
        String status,
        String reason,
        Long startedBy,
        LocalDateTime startedTime,
        LocalDateTime completedTime
) {
}
