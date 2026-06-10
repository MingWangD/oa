package com.example.judicialappraisal.organization.dto;

public record AdminRoleDto(
        Long id,
        String roleCode,
        String roleName,
        String status
) {
}
