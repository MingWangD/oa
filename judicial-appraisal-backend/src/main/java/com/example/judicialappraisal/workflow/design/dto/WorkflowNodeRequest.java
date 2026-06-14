package com.example.judicialappraisal.workflow.design.dto;

public record WorkflowNodeRequest(
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
        Integer enabled
) {
}
