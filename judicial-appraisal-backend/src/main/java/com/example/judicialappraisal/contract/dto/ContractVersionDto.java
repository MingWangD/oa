package com.example.judicialappraisal.contract.dto;

import java.time.LocalDateTime;

public record ContractVersionDto(
        Long id,
        Integer versionNo,
        String title,
        String content,
        String changeNote,
        LocalDateTime createdAt
) {
}
