package com.example.judicialappraisal.workflow.design.dto;

import jakarta.validation.constraints.NotBlank;

public record FormDesignRequest(
        @NotBlank String formCode,
        @NotBlank String formName,
        String category,
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
        String notesJson
) {
}
