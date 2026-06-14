package com.example.judicialappraisal.ledger.dto;

import java.util.List;

public record ReportCenterDto(
        String moduleCode,
        String moduleName,
        String description,
        String sourceType,
        List<String> statusOptions,
        List<LedgerMetricDto> metrics,
        List<ReportChartDto> charts,
        List<LedgerRowDto> rows,
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<String> nextActions
) {
    public ReportCenterDto {
        statusOptions = statusOptions == null ? List.of() : List.copyOf(statusOptions);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        charts = charts == null ? List.of() : List.copyOf(charts);
        rows = rows == null ? List.of() : List.copyOf(rows);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}
