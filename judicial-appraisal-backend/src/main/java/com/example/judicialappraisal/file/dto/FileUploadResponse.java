package com.example.judicialappraisal.file.dto;

public record FileUploadResponse(
        Long fileId,
        String originalName,
        String contentType,
        Long fileSize,
        String md5,
        Integer versionNo
) {
}
