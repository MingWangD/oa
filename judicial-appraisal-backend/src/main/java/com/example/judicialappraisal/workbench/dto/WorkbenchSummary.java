package com.example.judicialappraisal.workbench.dto;

public record WorkbenchSummary(
        long todoCount,
        long doneCount,
        long processingCount,
        long overdueCount
) {}