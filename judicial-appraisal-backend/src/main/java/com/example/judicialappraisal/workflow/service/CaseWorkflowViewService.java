package com.example.judicialappraisal.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.dto.CaseWorkflowViewResponse;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CaseWorkflowViewService {

    private final CaseWfInstanceMapper instanceMapper;
    private final CaseTaskMapper taskMapper;
    private final CaseSubflowInstanceMapper subflowInstanceMapper;
    private final WfNodeDefMapper nodeMapper;
    private final WfTransitionDefMapper transitionMapper;

    public CaseWorkflowViewService(CaseWfInstanceMapper instanceMapper,
                                   CaseTaskMapper taskMapper,
                                   CaseSubflowInstanceMapper subflowInstanceMapper,
                                   WfNodeDefMapper nodeMapper,
                                   WfTransitionDefMapper transitionMapper) {
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.subflowInstanceMapper = subflowInstanceMapper;
        this.nodeMapper = nodeMapper;
        this.transitionMapper = transitionMapper;
    }

    public CaseWorkflowViewResponse getCaseWorkflow(Long caseId, Long taskId) {
        CaseWfInstance instance = instanceMapper.selectOne(new LambdaQueryWrapper<CaseWfInstance>()
                .eq(CaseWfInstance::getCaseId, caseId)
                .orderByDesc(CaseWfInstance::getId)
                .last("limit 1"));
        if (instance == null) {
            throw new BusinessException("案件流程尚未启动");
        }

        Long activeWfId = instance.getWfId();
        String workflowCode = instance.getWfCode();
        String workflowName = instance.getWfName();
        String currentNodeCode = instance.getCurrentNodeCode();
        if (taskId != null) {
            CaseTask task = taskMapper.selectById(taskId);
            if (task == null || !caseId.equals(task.getCaseId())) {
                throw new BusinessException("任务不存在");
            }
            currentNodeCode = task.getNodeCode();
            if (task.getSubflowInstanceId() != null) {
                CaseSubflowInstance subflow = subflowInstanceMapper.selectById(task.getSubflowInstanceId());
                if (subflow == null || !caseId.equals(subflow.getCaseId())) {
                    throw new BusinessException("子流程实例不存在");
                }
                activeWfId = subflow.getWfId();
                workflowCode = subflow.getWfCode();
                workflowName = subflow.getWfName();
            }
        }

        List<CaseWorkflowViewResponse.Node> nodes = nodeMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                        .eq(WfNodeDef::getWfId, activeWfId)
                        .eq(WfNodeDef::getEnabled, 1)
                        .orderByAsc(WfNodeDef::getSortNo)
                        .orderByAsc(WfNodeDef::getId))
                .stream()
                .map(node -> new CaseWorkflowViewResponse.Node(
                        node.getNodeCode(), node.getNodeName(), node.getNodeType(), node.getSortNo()))
                .toList();

        List<CaseWorkflowViewResponse.Transition> transitions = transitionMapper
                .selectList(new LambdaQueryWrapper<WfTransitionDef>()
                        .eq(WfTransitionDef::getWfId, activeWfId)
                        .eq(WfTransitionDef::getEnabled, 1)
                        .orderByAsc(WfTransitionDef::getSortNo)
                        .orderByAsc(WfTransitionDef::getId))
                .stream()
                .map(transition -> new CaseWorkflowViewResponse.Transition(
                        transition.getFromNodeCode(), transition.getToNodeCode(),
                        transition.getActionCode(), transition.getActionName(),
                        transition.getConditionExpression(), transition.getSortNo()))
                .toList();

        String activeNodeCode = currentNodeCode;
        List<CaseWorkflowViewResponse.Transition> nextTransitions = transitions.stream()
                .filter(transition -> transition.fromNodeCode().equals(activeNodeCode))
                .toList();
        return new CaseWorkflowViewResponse(workflowCode, workflowName,
                currentNodeCode, nodes, transitions, nextTransitions);
    }
}
