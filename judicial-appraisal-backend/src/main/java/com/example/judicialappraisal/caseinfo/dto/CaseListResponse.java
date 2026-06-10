package com.example.judicialappraisal.caseinfo.dto;

import java.time.LocalDateTime;

public record CaseListResponse(
        Long id,
        String caseNo,
        String caseTitle,
        String caseType,
        String caseStatus,
        String caseStatusName,
        String currentNodeCode,
        String currentNodeName,
        Long currentHandlerId,
        String currentHandlerName,
        Long acceptDeptId,
        String acceptDeptName,
        String entrustOrgName,
        LocalDateTime deadlineTime,
        Integer urgentFlag,
        LocalDateTime submittedTime,
        LocalDateTime completedTime,
        LocalDateTime createdTime
) {}