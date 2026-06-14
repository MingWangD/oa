package com.example.judicialappraisal.ledger.dto;

import java.util.List;

public record ReportChartDto(
        String code,
        String title,
        String type,
        List<ReportChartItemDto> items
) {
    public ReportChartDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
