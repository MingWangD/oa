package com.example.judicialappraisal.task.dto;

import java.time.LocalDateTime;

public record TaskSummaryResponse(
        Long id,
        Long caseId,
        Long subflowInstanceId,
        String taskTitle,
        String nodeCode,
        String nodeName,
        String status,
        Long assigneeId,
        String assigneeName,
        LocalDateTime deadlineTime) {
}
