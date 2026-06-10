package com.example.judicialappraisal.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminUserCreateRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(max = 64) String realName,
        @NotBlank @Size(min = 6, max = 64) String password,
        @Size(max = 32) String mobile,
        @Size(max = 128) String email,
        Long deptId,
        Long postId,
        String status,
        List<Long> roleIds
) {
}
