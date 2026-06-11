package com.example.judicialappraisal.workflow.design.dto;

import java.time.LocalDateTime;

public record WorkflowVersionDto(
        Long id,
        String wfCode,
        String wfName,
        String wfType,
        String formCode,
        Integer versionNo,
        Integer enabled,
        String publishStatus,
        String remark,
        String definitionJson,
        Long sourceWfId,
        Long publishedBy,
        LocalDateTime publishedTime,
        Integer immutableFlag,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
