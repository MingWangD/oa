package com.example.judicialappraisal.caseinfo.dto;

public record CaseSubmitRequest(
        @Deprecated Long operatorId,
        @Deprecated
        String operatorName,
        String opinion
) {}
