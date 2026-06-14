package com.example.judicialappraisal.ledger.dto;

import java.time.LocalDateTime;
import java.util.List;

public record LedgerRowDto(
        String rowKey,
        String primaryText,
        String secondaryText,
        String tertiaryText,
        String ownerName,
        String statusLabel,
        String metricText,
        String progressLabel,
        String actionHint,
        LocalDateTime updatedTime,
        LocalDateTime deadlineTime,
        List<String> tags,
        List<String> facts,
        String relatedPath
) {
    public LedgerRowDto {
        tags = tags == null ? List.of() : List.copyOf(tags);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
