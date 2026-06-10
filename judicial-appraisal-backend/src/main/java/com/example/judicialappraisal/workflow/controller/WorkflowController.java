package com.example.judicialappraisal.workflow.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.service.StateMachineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cases")
public class WorkflowController {

    private final StateMachineService stateMachineService;

    public WorkflowController(StateMachineService stateMachineService) {
        this.stateMachineService = stateMachineService;
    }

    @PostMapping("/{caseId}/actions")
    public ApiResponse<WorkflowActionResult> processAction(@PathVariable Long caseId,
                                                           @Valid @RequestBody WorkflowActionRequest request) {
        return ApiResponse.success(stateMachineService.processAction(caseId, request));
    }
}
