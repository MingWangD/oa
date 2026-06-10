package com.example.judicialappraisal.caseinfo.dto;

public record CaseQueryRequest(
        String keyword,
        String caseStatus,
        Long acceptDeptId,
        Long currentHandlerId,
        Integer pageNo,
        Integer pageSize
) {
    public CaseQueryRequest {
        if (pageNo == null || pageNo < 1) pageNo = 1;
        if (pageSize == null || pageSize < 1) pageSize = 10;
    }
}