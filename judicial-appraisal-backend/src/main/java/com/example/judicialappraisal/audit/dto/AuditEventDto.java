package com.example.judicialappraisal.audit.dto;

import java.time.LocalDateTime;

public record AuditEventDto(
        Long id,
        String actionCode,
        String actionName,
        String bizType,
        Long bizId,
        Long caseId,
        Long operatorId,
        String operatorName,
        String resultStatus,
        String detailJson,
        LocalDateTime operatedTime
) {
}
