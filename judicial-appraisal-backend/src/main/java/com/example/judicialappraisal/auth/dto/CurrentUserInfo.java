package com.example.judicialappraisal.auth.dto;

import java.util.List;

public record CurrentUserInfo(
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
        List<CurrentUserRole> roles
) {
    public CurrentUserInfo {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
