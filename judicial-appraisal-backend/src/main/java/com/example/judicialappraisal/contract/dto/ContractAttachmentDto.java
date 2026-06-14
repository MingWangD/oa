package com.example.judicialappraisal.contract.dto;

import java.time.LocalDateTime;

public record ContractAttachmentDto(
        Long id,
        Long fileId,
        String fileName,
        String artifactCode,
        LocalDateTime createdAt
) {
}
