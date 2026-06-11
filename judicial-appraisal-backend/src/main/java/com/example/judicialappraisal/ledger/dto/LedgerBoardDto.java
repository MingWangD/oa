package com.example.judicialappraisal.ledger.dto;

import java.util.List;

public record LedgerBoardDto(
        String moduleCode,
        String moduleName,
        String description,
        String sourceType,
        List<String> statusOptions,
        List<LedgerMetricDto> metrics,
        List<LedgerRowDto> rows,
        List<String> nextActions
) {
    public LedgerBoardDto {
        statusOptions = statusOptions == null ? List.of() : List.copyOf(statusOptions);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        rows = rows == null ? List.of() : List.copyOf(rows);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}
