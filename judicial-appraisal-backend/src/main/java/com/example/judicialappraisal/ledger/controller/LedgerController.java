package com.example.judicialappraisal.ledger.controller;

import com.example.judicialappraisal.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("ledger placeholder");
    }
}
