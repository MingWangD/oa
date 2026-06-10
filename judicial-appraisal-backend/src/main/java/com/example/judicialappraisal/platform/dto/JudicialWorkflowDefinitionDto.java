package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record JudicialWorkflowDefinitionDto(
        String code,
        String name,
        String formCode,
        String entryMode,
        List<String> roles,
        List<String> keyRules,
        List<String> nextFlows
) {
    public JudicialWorkflowDefinitionDto {
        roles = roles == null ? List.of() : List.copyOf(roles);
        keyRules = keyRules == null ? List.of() : List.copyOf(keyRules);
        nextFlows = nextFlows == null ? List.of() : List.copyOf(nextFlows);
    }
}
