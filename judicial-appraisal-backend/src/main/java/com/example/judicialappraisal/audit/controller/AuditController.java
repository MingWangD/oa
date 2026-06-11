package com.example.judicialappraisal.audit.controller;

import com.example.judicialappraisal.audit.dto.AuditEventDto;
import com.example.judicialappraisal.audit.service.AuditLogService;
import com.example.judicialappraisal.common.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("audit ok");
    }

    @GetMapping("/events")
    public ApiResponse<List<AuditEventDto>> events(@RequestParam(required = false) Long caseId,
                                                   @RequestParam(required = false) String bizType,
                                                   @RequestParam(required = false) Long bizId,
                                                   @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(auditLogService.list(caseId, bizType, bizId, limit));
    }
}
