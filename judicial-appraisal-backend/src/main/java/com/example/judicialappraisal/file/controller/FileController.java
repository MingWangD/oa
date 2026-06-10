package com.example.judicialappraisal.file.controller;

import com.example.judicialappraisal.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("file placeholder");
    }
}
