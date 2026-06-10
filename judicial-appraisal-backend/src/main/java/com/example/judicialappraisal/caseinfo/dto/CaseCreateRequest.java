package com.example.judicialappraisal.caseinfo.dto;

import jakarta.validation.constraints.NotBlank;

public record CaseCreateRequest(
        @NotBlank String caseTitle,
        String caseType,
        String entrustOrgName,
        Long acceptDeptId
) {
}
