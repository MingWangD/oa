package com.example.judicialappraisal.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserUpdateRequest(
        @NotBlank @Size(max = 64) String realName,
        @Size(max = 32) String mobile,
        @Size(max = 128) String email,
        Long deptId,
        Long postId,
        String status
) {
}
