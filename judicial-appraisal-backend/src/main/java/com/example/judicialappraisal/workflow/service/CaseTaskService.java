package com.example.judicialappraisal.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.judicialappraisal.common.enums.TaskStatus;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.task.dto.TaskSummaryResponse;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CaseTaskService extends ServiceImpl<CaseTaskMapper, CaseTask> {

    private final CaseTaskMapper caseTaskMapper;
    private final CaseTaskCandidateMapper caseTaskCandidateMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public CaseTaskService(
            CaseTaskMapper caseTaskMapper,
            CaseTaskCandidateMapper caseTaskCandidateMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.caseTaskMapper = caseTaskMapper;
        this.caseTaskCandidateMapper = caseTaskCandidateMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    public List<TaskSummaryResponse> listTodoTasks(Long assigneeId) {
        List<Long> candidateTaskIds = selectEligibleCandidateTaskIds(assigneeId);
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .in(CaseTask::getStatus, TaskStatus.PENDING.name().toLowerCase(), TaskStatus.CLAIMED.name().toLowerCase(), TaskStatus.PROCESSING.name().toLowerCase())
                .orderByDesc(CaseTask::getId);
        applyTodoVisibility(queryWrapper, assigneeId, candidateTaskIds);
        return caseTaskMapper.selectList(queryWrapper).stream().map(this::toSummary).toList();
    }

    public List<TaskSummaryResponse> listDoneTasks(Long assigneeId) {
        LambdaQueryWrapper<CaseTask> queryWrapper = new LambdaQueryWrapper<CaseTask>()
                .eq(assigneeId != null, CaseTask::getAssigneeId, assigneeId)
                .eq(CaseTask::getStatus, TaskStatus.COMPLETED.name().toLowerCase())
                .orderByDesc(CaseTask::getCompletedTime)
                .orderByDesc(CaseTask::getId);
        return caseTaskMapper.selectList(queryWrapper).stream().map(this::toSummary).toList();
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
                        .eq(CaseTask::getStatus, TaskStatus.PENDING.name().toLowerCase())
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
