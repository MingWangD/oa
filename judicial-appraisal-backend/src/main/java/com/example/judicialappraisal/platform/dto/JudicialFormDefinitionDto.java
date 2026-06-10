package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record JudicialFormDefinitionDto(
        String code,
        String name,
        String alias,
        List<String> inputFiles,
        List<String> outputFiles,
        List<String> versionedArtifacts
) {
    public JudicialFormDefinitionDto {
        inputFiles = inputFiles == null ? List.of() : List.copyOf(inputFiles);
        outputFiles = outputFiles == null ? List.of() : List.copyOf(outputFiles);
        versionedArtifacts = versionedArtifacts == null ? List.of() : List.copyOf(versionedArtifacts);
    }
}
