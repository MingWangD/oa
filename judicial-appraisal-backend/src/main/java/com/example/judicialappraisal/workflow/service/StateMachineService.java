package com.example.judicialappraisal.workflow.service;

import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import org.springframework.stereotype.Service;

@Service
public class StateMachineService {

    private final WorkflowRuntimeService workflowRuntimeService;

    public StateMachineService(WorkflowRuntimeService workflowRuntimeService) {
        this.workflowRuntimeService = workflowRuntimeService;
    }

    public WorkflowActionResult processAction(Long caseId, WorkflowActionRequest request) {
        if (request.actionCode() == ActionCode.SUBMIT && request.taskId() == null) {
            return workflowRuntimeService.submitCase(caseId, request);
        }
        return workflowRuntimeService.completeTask(caseId, request);
    }
}
