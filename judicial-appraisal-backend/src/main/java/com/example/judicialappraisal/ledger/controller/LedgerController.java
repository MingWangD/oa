package com.example.judicialappraisal.ledger.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.ledger.dto.LedgerBoardDto;
import com.example.judicialappraisal.ledger.service.LedgerService;
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
}
