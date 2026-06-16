package com.example.judicialappraisal.task.dto;

import java.time.LocalDateTime;

public record TaskDetailResponse(
        Long id,
        Long caseId,
        Long subflowInstanceId,
        String caseNo,
        String caseTitle,
        String wfName,
        Long wfInstanceId,
        Long nodeInstanceId,
        String taskType,
        String taskTitle,
        String nodeCode,
        String nodeName,
        String status,
        Long assigneeId,
        String assigneeName,
        LocalDateTime startedTime,
        LocalDateTime completedTime,
        LocalDateTime deadlineTime,
        Integer overtimeFlag,
        String resultAction,
        String resultOpinion,
        String formCode
) {}
