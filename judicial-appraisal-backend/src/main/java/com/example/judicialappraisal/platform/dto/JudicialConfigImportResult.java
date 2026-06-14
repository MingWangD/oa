package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record JudicialConfigImportResult(
        int formsCreated,
        int formsSkipped,
        int workflowsCreated,
        int workflowsSkipped,
        List<String> messages
) {
}
