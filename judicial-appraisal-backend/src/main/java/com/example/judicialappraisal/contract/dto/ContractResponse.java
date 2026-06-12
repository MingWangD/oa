package com.example.judicialappraisal.contract.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ContractResponse(
        Long id,
        String contractNo,
        String contractName,
        String customerName,
        Long relatedCaseId,
        BigDecimal amount,
        Long ownerId,
        String ownerName,
        Long departmentId,
        String departmentName,
        String status,
        String statusName,
        Long archiveDocumentId,
        String reviewOpinion,
        LocalDateTime submittedAt,
        LocalDateTime approvedAt,
        LocalDateTime archivedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        ContractVersionDto latestVersion,
        List<ContractAttachmentDto> attachments
) {
}
