package com.example.judicialappraisal.file.dto;

import java.time.LocalDateTime;

public record FileVersionDto(
        Long id,
        String bizType,
        Long bizId,
        Long caseId,
        String artifactCode,
        String artifactName,
        Integer versionNo,
        Long fileId,
        String changeNote,
        LocalDateTime createdTime
) {
}
