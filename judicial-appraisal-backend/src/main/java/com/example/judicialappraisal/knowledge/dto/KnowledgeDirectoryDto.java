package com.example.judicialappraisal.knowledge.dto;

public record KnowledgeDirectoryDto(
        Long id,
        Long parentId,
        String directoryCode,
        String directoryName,
        String directoryType,
        Long caseId,
        String path
) {
}
