package com.example.judicialappraisal.organization.dto;

public record OrganizationDeptDto(
        Long id,
        Long parentId,
        String deptName,
        String deptCode,
        Integer sortNo,
        String status) {
}
