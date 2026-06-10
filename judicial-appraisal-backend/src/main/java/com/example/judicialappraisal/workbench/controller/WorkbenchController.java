package com.example.judicialappraisal.workbench.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.task.dto.TaskSummaryResponse;
import com.example.judicialappraisal.workbench.dto.WorkbenchSummary;
import com.example.judicialappraisal.workbench.service.WorkbenchService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;

    public WorkbenchController(WorkbenchService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/summary")
    public ApiResponse<WorkbenchSummary> summary(@RequestParam(required = false) Long assigneeId) {
        return ApiResponse.success(workbenchService.getSummary(assigneeId));
    }

    @GetMapping("/todo")
    public ApiResponse<List<TaskSummaryResponse>> todo(@RequestParam(required = false) Long assigneeId) {
        return ApiResponse.success(workbenchService.getTodoTasks(assigneeId));
    }

    @GetMapping("/done")
    public ApiResponse<List<TaskSummaryResponse>> done(@RequestParam(required = false) Long assigneeId) {
        return ApiResponse.success(workbenchService.getDoneTasks(assigneeId));
    }
}