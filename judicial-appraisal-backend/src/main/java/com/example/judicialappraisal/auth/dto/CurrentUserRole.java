package com.example.judicialappraisal.auth.dto;

import java.util.List;

public record CurrentUserRole(Long id, String code, String name, String dataScope, List<Long> customDeptIds) {
    public CurrentUserRole {
        customDeptIds = customDeptIds == null ? List.of() : List.copyOf(customDeptIds);
    }
}
