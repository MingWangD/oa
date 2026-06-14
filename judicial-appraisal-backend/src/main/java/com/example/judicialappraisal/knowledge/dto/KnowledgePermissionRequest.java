package com.example.judicialappraisal.knowledge.dto;

public record KnowledgePermissionRequest(
        Long directoryId,
        Long documentId,
        String subjectType,
        Long subjectId,
        String permissionCode
) {
}
