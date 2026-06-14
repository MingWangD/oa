package com.example.judicialappraisal.platform.dto;

import java.util.List;

public record ReconstructionPhaseDto(
        String phase,
        String goal,
        String status,
        List<String> deliverables
) {
    public ReconstructionPhaseDto {
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
    }
}
