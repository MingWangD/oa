package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record JudicialWorkflowVerificationDto(
        String code,
        String name,
        String formCode,
        String entryMode,
        boolean published,
        Integer publishedVersion,
        boolean formPublished,
        int nodeCount,
        int transitionCount,
        boolean hasStart,
        boolean hasEnd,
        boolean hasActionableNode,
        boolean hasReturnPath,
        boolean hasEndPath,
        List<String> subflowTargets,
        List<String> missingSubflowTargets,
        List<String> issues,
        boolean passed
) {
    public JudicialWorkflowVerificationDto {
        subflowTargets = subflowTargets == null ? List.of() : List.copyOf(subflowTargets);
        missingSubflowTargets = missingSubflowTargets == null ? List.of() : List.copyOf(missingSubflowTargets);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
