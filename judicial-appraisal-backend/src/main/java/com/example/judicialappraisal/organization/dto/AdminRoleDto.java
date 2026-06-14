package com.example.judicialappraisal.organization.dto;

import java.util.List;

public record AdminRoleDto(
        Long id,
        String roleCode,
        String roleName,
        String status,
        String dataScope,
        List<Long> customDeptIds
) {
    public AdminRoleDto {
        customDeptIds = customDeptIds == null ? List.of() : List.copyOf(customDeptIds);
    }
}
