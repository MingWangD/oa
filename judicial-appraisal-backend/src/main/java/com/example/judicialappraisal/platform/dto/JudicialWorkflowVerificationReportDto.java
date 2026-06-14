package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record JudicialWorkflowVerificationReportDto(
        int expectedWorkflowCount,
        int checkedWorkflowCount,
        int passedWorkflowCount,
        int failedWorkflowCount,
        List<JudicialWorkflowVerificationDto> workflows
) {
    public JudicialWorkflowVerificationReportDto {
        workflows = workflows == null ? List.of() : List.copyOf(workflows);
    }
}
