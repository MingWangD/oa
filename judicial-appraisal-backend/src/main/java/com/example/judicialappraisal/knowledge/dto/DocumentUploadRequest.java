package com.example.judicialappraisal.knowledge.dto;

public record DocumentUploadRequest(
        Long directoryId,
        String title,
        Long fileId
) {
}
