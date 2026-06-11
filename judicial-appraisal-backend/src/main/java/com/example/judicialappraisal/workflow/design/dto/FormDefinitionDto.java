package com.example.judicialappraisal.workflow.design.dto;

import java.time.LocalDateTime;

public record FormDefinitionDto(
        Long id,
        String formCode,
        String formName,
        String category,
        Integer currentPublishedVersion,
        Integer enabled,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
