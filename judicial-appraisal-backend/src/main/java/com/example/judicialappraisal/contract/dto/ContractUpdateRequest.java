package com.example.judicialappraisal.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record ContractUpdateRequest(
        @NotBlank String contractName,
        @NotBlank String customerName,
        Long relatedCaseId,
        @NotNull BigDecimal amount,
        Long departmentId,
        String departmentName,
        @NotBlank String content,
        String changeNote,
        List<Long> fileIds
) {
}
