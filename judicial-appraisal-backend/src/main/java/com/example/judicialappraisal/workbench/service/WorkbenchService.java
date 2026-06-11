package com.example.judicialappraisal.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.task.dto.TaskSummaryResponse;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workbench.dto.WorkbenchSummary;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkbenchService {

    private final CaseTaskMapper caseTaskMapper;
    private final CaseTaskCandidateMapper caseTaskCandidateMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public WorkbenchService(
            CaseTaskMapper caseTaskMapper,
            CaseTaskCandidateMapper caseTaskCandidateMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.caseTaskMapper = caseTaskMapper;
        this.caseTaskCandidateMapper = caseTaskCandidateMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public WorkbenchSummary getSummary(Long assigneeId) {
        List<Long> candidateTaskIds = selectEligibleCandidateTaskIds(assigneeId);
        long todoCount = countTodoTasks(assigneeId, candidateTaskIds);
        long doneCount = countDoneTasks(assigneeId);
        long processingCount = countProcessingCases(assigneeId, candidateTaskIds);
        long overdueCount = countOverdueTasks(assigneeId, candidateTaskIds);
        return new WorkbenchSummary(todoCount, doneCount, processingCount, overdueCount);
    }

    public List<TaskSummaryResponse> getTodoTasks(Long assigneeId) {
        List<Long> candidateTaskIds = selectEligibleCandidateTaskIds(assigneeId);
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .orderByAsc(CaseTask::getDeadlineTime)
                .orderByDesc(CaseTask::getId);
        applyTodoVisibility(queryWrapper, assigneeId, candidateTaskIds);
        return caseTaskMapper.selectList(queryWrapper).stream().map(this::toSummary).toList();
    }

    public List<TaskSummaryResponse> getDoneTasks(Long assigneeId) {
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .eq(assigneeId != null, CaseTask::getAssigneeId, assigneeId)
                .eq(CaseTask::getStatus, "completed")
                .orderByDesc(CaseTask::getCompletedTime)
                .last("limit 20");
        return caseTaskMapper.selectList(queryWrapper).stream().map(this::toSummary).toList();
    }

    private long countTodoTasks(Long assigneeId, List<Long> candidateTaskIds) {
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .in(CaseTask::getStatus, "pending", "claimed", "processing");
        applyTodoVisibility(queryWrapper, assigneeId, candidateTaskIds);
        return caseTaskMapper.selectCount(queryWrapper);
    }

    private long countDoneTasks(Long assigneeId) {
        return caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
                .eq(assigneeId != null, CaseTask::getAssigneeId, assigneeId)
                .eq(CaseTask::getStatus, "completed"));
    }

    private long countProcessingCases(Long assigneeId, List<Long> candidateTaskIds) {
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .in(CaseTask::getStatus, "pending", "claimed", "processing");
        applyTodoVisibility(queryWrapper, assigneeId, candidateTaskIds);
        return caseTaskMapper.selectCount(queryWrapper);
    }

    private long countOverdueTasks(Long assigneeId, List<Long> candidateTaskIds) {
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .in(CaseTask::getStatus, "pending", "claimed", "processing")
                .lt(CaseTask::getDeadlineTime, LocalDateTime.now());
        applyTodoVisibility(queryWrapper, assigneeId, candidateTaskIds);
        return caseTaskMapper.selectCount(queryWrapper);
    }

    private void applyTodoVisibility(LambdaQueryWrapper<CaseTask> queryWrapper, Long assigneeId, List<Long> candidateTaskIds) {
        if (assigneeId == null) {
            return;
        }
        queryWrapper.and(wrapper -> {
            wrapper.eq(CaseTask::getAssigneeId, assigneeId);
            if (!candidateTaskIds.isEmpty()) {
                wrapper.or(candidateWrapper -> candidateWrapper
                        .in(CaseTask::getId, candidateTaskIds)
                        .eq(CaseTask::getStatus, "pending")
                        .isNull(CaseTask::getAssigneeId));
            }
        });
    }

    private List<Long> selectEligibleCandidateTaskIds(Long assigneeId) {
        if (assigneeId == null) {
            return List.of();
        }
        List<Long> roleIds = selectUserRoleIds(assigneeId);
        List<Long> taskIds = caseTaskCandidateMapper.selectEligibleTaskIds(assigneeId, roleIds);
        return taskIds == null ? List.of() : taskIds;
    }

    private List<Long> selectUserRoleIds(Long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectEnabledRoleIdsByUserId(userId);
        return roleIds == null ? List.of() : roleIds;
    }

    private TaskSummaryResponse toSummary(CaseTask task) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getCaseId(),
                task.getSubflowInstanceId(),
                task.getTaskTitle(),
                task.getNodeCode(),
                task.getNodeName(),
                task.getStatus(),
                task.getAssigneeId(),
                task.getAssigneeName(),
                task.getDeadlineTime());
    }
}
