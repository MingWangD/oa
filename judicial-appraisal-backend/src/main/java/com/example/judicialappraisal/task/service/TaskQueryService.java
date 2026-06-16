package com.example.judicialappraisal.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.task.dto.TaskDetailResponse;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import org.springframework.stereotype.Service;

@Service
public class TaskQueryService {

    private final CaseTaskMapper caseTaskMapper;
    private final CaseWfInstanceMapper caseWfInstanceMapper;
    private final CaseInfoMapper caseInfoMapper;
    private final CaseSubflowInstanceMapper caseSubflowInstanceMapper;
    private final WfDefinitionMapper wfDefinitionMapper;

    public TaskQueryService(
            CaseTaskMapper caseTaskMapper,
            CaseWfInstanceMapper caseWfInstanceMapper,
            CaseInfoMapper caseInfoMapper,
            CaseSubflowInstanceMapper caseSubflowInstanceMapper,
            WfDefinitionMapper wfDefinitionMapper) {
        this.caseTaskMapper = caseTaskMapper;
        this.caseWfInstanceMapper = caseWfInstanceMapper;
        this.caseInfoMapper = caseInfoMapper;
        this.caseSubflowInstanceMapper = caseSubflowInstanceMapper;
        this.wfDefinitionMapper = wfDefinitionMapper;
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
        String caseTitle = caseInfo != null ? caseInfo.getCaseTitle() : "";
        String caseNo = caseInfo != null ? caseInfo.getCaseNo() : "";

        String wfName = "";
        String formCode = null;

        if (task.getSubflowInstanceId() != null) {
            CaseSubflowInstance subflow = caseSubflowInstanceMapper.selectById(task.getSubflowInstanceId());
            if (subflow != null) {
                wfName = subflow.getWfName();
                WfDefinition definition = wfDefinitionMapper.selectById(subflow.getWfId());
                if (definition != null) {
                    formCode = definition.getFormCode();
                }
            }
        }

        if (wfName == null || wfName.isEmpty()) {
            CaseWfInstance wfInstance = caseWfInstanceMapper.selectById(task.getWfInstanceId());
            if (wfInstance != null) {
                wfName = wfInstance.getWfName();
                WfDefinition definition = wfDefinitionMapper.selectById(wfInstance.getWfId());
                if (definition != null) {
                    formCode = definition.getFormCode();
                }
            }
        }

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
                task.getResultOpinion(),
                formCode);
    }
}
