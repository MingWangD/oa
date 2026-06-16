package com.example.judicialappraisal.caseinfo.dto;

import java.util.Map;

public record CaseFormDataSaveRequest(
        Map<String, Object> formData,
        String opinion
) {
}
