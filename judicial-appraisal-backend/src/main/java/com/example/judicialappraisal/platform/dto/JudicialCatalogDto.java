package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record JudicialCatalogDto(
        int workflowCount,
        int formCount,
        List<String> dedicatedRoles,
        List<JudicialWorkflowDefinitionDto> workflows,
        List<JudicialFormDefinitionDto> forms
) {
    public JudicialCatalogDto {
        dedicatedRoles = dedicatedRoles == null ? List.of() : List.copyOf(dedicatedRoles);
        workflows = workflows == null ? List.of() : List.copyOf(workflows);
        forms = forms == null ? List.of() : List.copyOf(forms);
    }
}
