package com.example.judicialappraisal.workflow.design.dto;

import java.time.LocalDateTime;

public record FormVersionDto(
        Long id,
        Long formId,
        String formCode,
        String formName,
        Integer versionNo,
        String status,
        String inputFilesJson,
        String outputFilesJson,
        String versionedArtifactsJson,
        String fieldSchemaJson,
        String layoutSchemaJson,
        String validationSchemaJson,
        String permissionSchemaJson,
        String linkageSchemaJson,
        String calculationSchemaJson,
        String attachmentSchemaJson,
        String subtableSchemaJson,
        String notesJson,
        Long sourceVersionId,
        Long publishedBy,
        LocalDateTime publishedTime,
        Integer immutableFlag,
        LocalDateTime createdTime,
        LocalDateTime updatedTime
) {
}
