package com.example.judicialappraisal.file.dto;

public record FileContent(
        String originalName,
        String contentType,
        byte[] bytes
) {
}
