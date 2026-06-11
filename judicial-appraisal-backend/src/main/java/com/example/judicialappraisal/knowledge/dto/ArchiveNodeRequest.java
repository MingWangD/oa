package com.example.judicialappraisal.knowledge.dto;

import java.util.List;
import java.util.Map;

public record ArchiveNodeRequest(
        Long caseId,
        Long wfInstanceId,
        String nodeCode,
        String nodeName,
        Long taskId,
        String title,
        String artifactCode,
        Map<String, Object> formData,
        List<Long> fileIds,
        String archiveSummary
) {
}
