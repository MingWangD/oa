package com.example.judicialappraisal.organization.dto;

import java.util.List;

public record AdminUserDto(
        Long id,
        String username,
        String realName,
        String mobile,
        String email,
        Long deptId,
        String deptName,
        Long postId,
        String postName,
        String status,
        List<AdminRoleDto> roles
) {
    public AdminUserDto {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
