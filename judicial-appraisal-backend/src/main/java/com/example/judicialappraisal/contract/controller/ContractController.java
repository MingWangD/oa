package com.example.judicialappraisal.contract.controller;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.common.PageResult;
import com.example.judicialappraisal.contract.dto.ContractCreateRequest;
import com.example.judicialappraisal.contract.dto.ContractQueryRequest;
import com.example.judicialappraisal.contract.dto.ContractResponse;
import com.example.judicialappraisal.contract.dto.ContractReviewRequest;
import com.example.judicialappraisal.contract.dto.ContractUpdateRequest;
import com.example.judicialappraisal.contract.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    private final ContractService contractService;

    public ContractController(ContractService contractService) {
        this.contractService = contractService;
    }

    @GetMapping
    public ApiResponse<PageResult<ContractResponse>> page(@ModelAttribute ContractQueryRequest request,
                                                          Authentication authentication) {
        return ApiResponse.success(contractService.page(request, currentUser(authentication)));
    }

    @PostMapping
    public ApiResponse<ContractResponse> create(@Valid @RequestBody ContractCreateRequest request,
                                                Authentication authentication) {
        return ApiResponse.success(contractService.create(request, currentUser(authentication)));
    }

    @GetMapping("/{contractId}")
    public ApiResponse<ContractResponse> detail(@PathVariable Long contractId,
                                                Authentication authentication) {
        return ApiResponse.success(contractService.detail(contractId, currentUser(authentication)));
    }

    @PutMapping("/{contractId}")
    public ApiResponse<ContractResponse> update(@PathVariable Long contractId,
                                                @Valid @RequestBody ContractUpdateRequest request,
                                                Authentication authentication) {
        return ApiResponse.success(contractService.update(contractId, request, currentUser(authentication)));
    }

    @PostMapping("/{contractId}/submit")
    public ApiResponse<ContractResponse> submit(@PathVariable Long contractId,
                                                Authentication authentication) {
        return ApiResponse.success(contractService.submit(contractId, currentUser(authentication)));
    }

    @PostMapping("/{contractId}/approve")
    public ApiResponse<ContractResponse> approve(@PathVariable Long contractId,
                                                 @RequestBody(required = false) ContractReviewRequest request,
                                                 Authentication authentication) {
        return ApiResponse.success(contractService.approve(
                contractId,
                request == null ? new ContractReviewRequest(null) : request,
                currentUser(authentication)));
    }

    @PostMapping("/{contractId}/reject")
    public ApiResponse<ContractResponse> reject(@PathVariable Long contractId,
                                                @RequestBody(required = false) ContractReviewRequest request,
                                                Authentication authentication) {
        return ApiResponse.success(contractService.reject(
                contractId,
                request == null ? new ContractReviewRequest(null) : request,
                currentUser(authentication)));
    }

    private CurrentUserInfo currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        return userInfo;
    }
}
