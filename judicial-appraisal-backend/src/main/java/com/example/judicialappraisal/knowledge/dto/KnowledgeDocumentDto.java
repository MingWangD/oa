package com.example.judicialappraisal.knowledge.dto;

import java.time.LocalDateTime;

import java.util.List;

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
        LocalDateTime updatedTime,
        List<DocumentAttachmentDto> attachments
) {
    public record DocumentAttachmentDto(
            Long fileId,
            String fileName,
            String fileExt
    ) {}
}
