package com.example.judicialappraisal.auth.dto;

import java.util.List;
import java.util.Set;

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
        List<CurrentUserRole> roles,
        Set<String> permissions
) {
    public CurrentUserInfo {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
