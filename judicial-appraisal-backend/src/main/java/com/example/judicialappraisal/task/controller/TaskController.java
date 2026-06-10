package com.example.judicialappraisal.task.controller;

import com.example.judicialappraisal.common.ApiResponse;
import com.example.judicialappraisal.task.dto.TaskDetailResponse;
import com.example.judicialappraisal.task.dto.TaskSummaryResponse;
import com.example.judicialappraisal.task.service.TaskQueryService;
import com.example.judicialappraisal.workflow.service.CaseTaskService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final CaseTaskService caseTaskService;
    private final TaskQueryService taskQueryService;

    public TaskController(CaseTaskService caseTaskService, TaskQueryService taskQueryService) {
        this.caseTaskService = caseTaskService;
        this.taskQueryService = taskQueryService;
    }

    @GetMapping("/todo")
    public ApiResponse<List<TaskSummaryResponse>> todo(@RequestParam(required = false) Long assigneeId) {
        return ApiResponse.success(caseTaskService.listTodoTasks(assigneeId));
    }

    @GetMapping("/done")
    public ApiResponse<List<TaskSummaryResponse>> done(@RequestParam(required = false) Long assigneeId) {
        return ApiResponse.success(caseTaskService.listDoneTasks(assigneeId));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskDetailResponse> getDetail(@PathVariable Long taskId) {
        return ApiResponse.success(taskQueryService.getTaskDetail(taskId));
    }

    @GetMapping
    public ApiResponse<TaskDetailResponse> getByCase(@RequestParam Long caseId, @RequestParam String nodeCode) {
        return ApiResponse.success(taskQueryService.getTaskByCaseAndNode(caseId, nodeCode));
    }
}
