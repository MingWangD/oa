package com.example.judicialappraisal.knowledge.dto;

import java.time.LocalDateTime;

public record KnowledgeDocumentDto(
        Long id,
        Long directoryId,
        Long caseId,
        String title,
        String artifactCode,
        String sourceType,
        String nodeCode,
        String nodeName,
        Long taskId,
        Long currentFileId,
        Integer currentVersionNo,
        String status,
        LocalDateTime updatedTime
) {
}
