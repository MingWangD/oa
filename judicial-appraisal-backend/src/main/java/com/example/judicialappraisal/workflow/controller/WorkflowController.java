package com.example.judicialappraisal.workflow.controller;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.workflow.dto.CaseSubflowSummaryResponse;
import com.example.judicialappraisal.workflow.dto.CaseWorkflowViewResponse;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.service.CaseSubflowQueryService;
import com.example.judicialappraisal.workflow.service.CaseWorkflowViewService;
import com.example.judicialappraisal.workflow.service.StateMachineService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
public class WorkflowController {

    private final StateMachineService stateMachineService;
    private final CaseSubflowQueryService caseSubflowQueryService;
    private final CaseWorkflowViewService caseWorkflowViewService;

    public WorkflowController(StateMachineService stateMachineService,
                              CaseSubflowQueryService caseSubflowQueryService,
                              CaseWorkflowViewService caseWorkflowViewService) {
        this.stateMachineService = stateMachineService;
        this.caseSubflowQueryService = caseSubflowQueryService;
        this.caseWorkflowViewService = caseWorkflowViewService;
    }

    @PostMapping("/{caseId}/actions")
    public ApiResponse<WorkflowActionResult> processAction(@PathVariable Long caseId,
                                                           @Valid @RequestBody WorkflowActionRequest request,
                                                           Authentication authentication) {
        CurrentUserInfo currentUser = currentUser(authentication);
        return ApiResponse.success(stateMachineService.processAction(
                caseId,
                request,
                currentUser.id(),
                displayName(currentUser)));
    }

    @GetMapping("/{caseId}/subflows")
    public ApiResponse<List<CaseSubflowSummaryResponse>> listSubflows(@PathVariable Long caseId) {
        return ApiResponse.success(caseSubflowQueryService.listByCaseId(caseId));
    }

    @GetMapping("/{caseId}/workflow-view")
    public ApiResponse<CaseWorkflowViewResponse> workflowView(@PathVariable Long caseId,
                                                              @RequestParam(required = false) Long taskId) {
        return ApiResponse.success(caseWorkflowViewService.getCaseWorkflow(caseId, taskId));
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
