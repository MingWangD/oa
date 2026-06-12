package com.example.judicialappraisal.contract.dto;

public record ContractQueryRequest(
        String keyword,
        String status,
        Long relatedCaseId,
        Integer pageNo,
        Integer pageSize
) {
}
