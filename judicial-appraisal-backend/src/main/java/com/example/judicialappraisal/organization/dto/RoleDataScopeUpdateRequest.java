package com.example.judicialappraisal.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record RoleDataScopeUpdateRequest(
        @NotBlank String dataScope,
        List<Long> deptIds
) {
}
