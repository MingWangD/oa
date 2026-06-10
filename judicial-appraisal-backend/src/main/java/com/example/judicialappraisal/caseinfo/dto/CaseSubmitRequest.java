package com.example.judicialappraisal.caseinfo.dto;

import jakarta.validation.constraints.NotNull;

public record CaseSubmitRequest(
        @NotNull Long operatorId,
        String operatorName,
        String opinion
) {}