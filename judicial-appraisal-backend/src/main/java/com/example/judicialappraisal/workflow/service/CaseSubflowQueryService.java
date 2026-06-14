package com.example.judicialappraisal.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.workflow.dto.CaseSubflowSummaryResponse;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CaseSubflowQueryService {

    private final CaseSubflowInstanceMapper caseSubflowInstanceMapper;

    public CaseSubflowQueryService(CaseSubflowInstanceMapper caseSubflowInstanceMapper) {
        this.caseSubflowInstanceMapper = caseSubflowInstanceMapper;
    }

    public List<CaseSubflowSummaryResponse> listByCaseId(Long caseId) {
        return caseSubflowInstanceMapper.selectList(new LambdaQueryWrapper<CaseSubflowInstance>()
                        .eq(CaseSubflowInstance::getCaseId, caseId)
                        .orderByDesc(CaseSubflowInstance::getStartedTime)
                        .orderByDesc(CaseSubflowInstance::getId))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    private CaseSubflowSummaryResponse toSummary(CaseSubflowInstance instance) {
        return new CaseSubflowSummaryResponse(
                instance.getId(),
                instance.getCaseId(),
                instance.getParentWfInstanceId(),
                instance.getParentTaskId(),
                instance.getParentNodeCode(),
                instance.getWfId(),
                instance.getWfCode(),
                instance.getWfName(),
                instance.getSubflowType(),
                instance.getStatus(),
                instance.getReason(),
                instance.getStartedBy(),
                instance.getStartedTime(),
                instance.getCompletedTime()
        );
    }
}
