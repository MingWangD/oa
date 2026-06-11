package com.example.judicialappraisal.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.task.dto.TaskDetailResponse;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {

    private final CaseTaskMapper caseTaskMapper;
    private final CaseWfInstanceMapper caseWfInstanceMapper;
    private final CaseInfoMapper caseInfoMapper;

    public TaskQueryService(
            CaseTaskMapper caseTaskMapper,
            CaseWfInstanceMapper caseWfInstanceMapper,
            CaseInfoMapper caseInfoMapper) {
        this.caseTaskMapper = caseTaskMapper;
        this.caseWfInstanceMapper = caseWfInstanceMapper;
        this.caseInfoMapper = caseInfoMapper;
    }

    public TaskDetailResponse getTaskDetail(Long taskId) {
        CaseTask task = caseTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return toDetail(task);
    }

    public TaskDetailResponse getTaskByCaseAndNode(Long caseId, String nodeCode) {
        CaseTask task = caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getNodeCode, nodeCode)
                .orderByDesc(CaseTask::getId)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return toDetail(task);
    }

    private TaskDetailResponse toDetail(CaseTask task) {
        CaseInfo caseInfo = caseInfoMapper.selectById(task.getCaseId());
        CaseWfInstance wfInstance = caseWfInstanceMapper.selectById(task.getWfInstanceId());
        String caseTitle = caseInfo != null ? caseInfo.getCaseTitle() : "";
        String caseNo = caseInfo != null ? caseInfo.getCaseNo() : "";
        String wfName = wfInstance != null ? wfInstance.getWfName() : "";
        return new TaskDetailResponse(
                task.getId(),
                task.getCaseId(),
                task.getSubflowInstanceId(),
                caseNo,
                caseTitle,
                wfName,
                task.getWfInstanceId(),
                task.getNodeInstanceId(),
                task.getTaskType(),
                task.getTaskTitle(),
                task.getNodeCode(),
                task.getNodeName(),
                task.getStatus(),
                task.getAssigneeId(),
                task.getAssigneeName(),
                task.getStartedTime(),
                task.getCompletedTime(),
                task.getDeadlineTime(),
                task.getOvertimeFlag(),
                task.getResultAction(),
                task.getResultOpinion());
    }
}
