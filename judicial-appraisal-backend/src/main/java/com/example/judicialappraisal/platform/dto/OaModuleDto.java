package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record OaModuleDto(
        String code,
        String name,
        String scope,
        String priority,
        String implementationStatus,
        List<String> requiredCapabilities
) {
    public OaModuleDto {
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
    }
}
