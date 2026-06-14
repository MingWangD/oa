package com.example.judicialappraisal.workflow.design.dto;

import java.time.LocalDateTime;

public record WorkflowNodeDto(
        Long id,
        Long wfId,
        String nodeCode,
        String nodeName,
        String nodeType,
        String taskType,
        String caseStatus,
        String handlerDeptRule,
        String handlerPostRule,
        String handlerRoleRule,
        Integer allowManualAssign,
        Integer timeoutHours,
        String configJson,
        String assigneeRuleJson,
        String formRuleJson,
        String permissionJson,
        Integer sortNo,
        Integer enabled,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
