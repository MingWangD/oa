package com.example.judicialappraisal.caseinfo.controller;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseListResponse;
import com.example.judicialappraisal.caseinfo.dto.CaseQueryRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.service.CaseInfoService;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.common.PageResult;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
public class CaseInfoController {

    private final CaseInfoService caseInfoService;

    public CaseInfoController(CaseInfoService caseInfoService) {
        this.caseInfoService = caseInfoService;
    }

    @PostMapping
    public ApiResponse<CaseInfo> createDraft(@Valid @RequestBody CaseCreateRequest request) {
        return ApiResponse.success(caseInfoService.createDraft(request));
    }

    @GetMapping
    public ApiResponse<PageResult<CaseListResponse>> pageList(@ModelAttribute CaseQueryRequest request) {
        return ApiResponse.success(caseInfoService.pageList(request));
    }

    @PostMapping("/{caseId}/submit")
    public ApiResponse<WorkflowActionResult> submit(@PathVariable Long caseId,
                                                    @Valid @RequestBody CaseSubmitRequest request,
                                                    Authentication authentication) {
        CurrentUserInfo currentUser = currentUser(authentication);
        return ApiResponse.success(caseInfoService.submitCase(caseId, request, currentUser.id(), displayName(currentUser)));
    }

    @GetMapping("/{caseId}")
    public ApiResponse<CaseInfo> getDetail(@PathVariable Long caseId) {
        return ApiResponse.success(caseInfoService.getDetail(caseId));
    }

    private CurrentUserInfo currentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        return userInfo;
    }

    private String displayName(CurrentUserInfo userInfo) {
        if (userInfo.realName() != null && !userInfo.realName().isBlank()) {
            return userInfo.realName();
        }
        return userInfo.username();
    }
}
