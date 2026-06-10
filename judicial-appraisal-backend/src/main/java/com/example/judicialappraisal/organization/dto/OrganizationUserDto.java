package com.example.judicialappraisal.organization.dto;

public record OrganizationUserDto(
        Long id,
        String username,
        String realName,
        Long deptId,
        Long postId,
        String status) {
}
