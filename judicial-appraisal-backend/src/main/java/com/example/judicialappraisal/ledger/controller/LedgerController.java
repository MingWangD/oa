package com.example.judicialappraisal.ledger.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.ledger.dto.LedgerBoardDto;
import com.example.judicialappraisal.ledger.dto.ReportCenterDto;
import com.example.judicialappraisal.ledger.service.LedgerService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/modules/{moduleCode}")
    public ApiResponse<LedgerBoardDto> board(@PathVariable String moduleCode,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(ledgerService.board(moduleCode, keyword, status, limit));
    }

    @GetMapping("/report-center")
    public ApiResponse<ReportCenterDto> reportCenter(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "20") Integer pageSize) {
        return ApiResponse.success(ledgerService.reportCenter(keyword, status, page, pageSize));
    }

    @GetMapping("/report-center/export")
    public ResponseEntity<byte[]> exportReportCenter(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status) {
        byte[] csv = ledgerService.exportReportCenterCsv(keyword, status).getBytes(StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("report-center.csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
