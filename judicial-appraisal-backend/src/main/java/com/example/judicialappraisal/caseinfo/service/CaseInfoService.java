package com.example.judicialappraisal.caseinfo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.judicialappraisal.caseinfo.dto.CaseCreateRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseListResponse;
import com.example.judicialappraisal.caseinfo.dto.CaseQueryRequest;
import com.example.judicialappraisal.caseinfo.dto.CaseSubmitRequest;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.PageResult;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.service.WorkflowRuntimeService;
import org.springframework.stereotype.Service;

@Service
public class CaseInfoService extends ServiceImpl<CaseInfoMapper, CaseInfo> {

    private final WorkflowRuntimeService workflowRuntimeService;

    public CaseInfoService(WorkflowRuntimeService workflowRuntimeService) {
        this.workflowRuntimeService = workflowRuntimeService;
    }

    public CaseInfo createDraft(CaseCreateRequest request) {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setCaseTitle(request.caseTitle());
        caseInfo.setCaseType(request.caseType());
        caseInfo.setEntrustOrgName(request.entrustOrgName());
        caseInfo.setAcceptDeptId(request.acceptDeptId());
        caseInfo.setCaseStatus(CaseStatus.DRAFT.name());
        save(caseInfo);
        return caseInfo;
    }

    public PageResult<CaseListResponse> pageList(CaseQueryRequest request) {
        Page<CaseInfo> page = page(
                Page.of(request.pageNo(), request.pageSize()),
                new LambdaQueryWrapper<CaseInfo>()
                        .like(hasText(request.keyword()), CaseInfo::getCaseTitle, request.keyword())
                        .eq(hasText(request.caseStatus()), CaseInfo::getCaseStatus, request.caseStatus())
                        .eq(request.acceptDeptId() != null, CaseInfo::getAcceptDeptId, request.acceptDeptId())
                        .eq(request.currentHandlerId() != null, CaseInfo::getCurrentHandlerId, request.currentHandlerId())
                        .orderByDesc(CaseInfo::getId));
        return new PageResult<>(page.getRecords().stream().map(this::toListResponse).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public WorkflowActionResult submitCase(Long caseId, CaseSubmitRequest request) {
        com.example.judicialappraisal.common.enums.ActionCode submitCode = com.example.judicialappraisal.common.enums.ActionCode.SUBMIT;
        com.example.judicialappraisal.workflow.dto.WorkflowActionRequest actionRequest = 
            new com.example.judicialappraisal.workflow.dto.WorkflowActionRequest(
                null, submitCode, request.opinion(), null, request.operatorId(), request.operatorName(), null, null);
        return workflowRuntimeService.submitCase(caseId, actionRequest);
    }

    public CaseInfo getDetail(Long caseId) {
        CaseInfo caseInfo = getById(caseId);
        if (caseInfo != null) {
            return caseInfo;
        }
        CaseInfo fallback = new CaseInfo();
        fallback.setId(caseId);
        fallback.setCaseTitle("示例司法鉴定案件");
        fallback.setCaseStatus(CaseStatus.DRAFT.name());
        return fallback;
    }

    private CaseListResponse toListResponse(CaseInfo caseInfo) {
        return new CaseListResponse(
                caseInfo.getId(),
                caseInfo.getCaseNo(),
                caseInfo.getCaseTitle(),
                caseInfo.getCaseType(),
                caseInfo.getCaseStatus(),
                statusName(caseInfo.getCaseStatus()),
                caseInfo.getCurrentNodeCode(),
                caseInfo.getCurrentNodeName(),
                caseInfo.getCurrentHandlerId(),
                caseInfo.getCurrentHandlerName(),
                caseInfo.getAcceptDeptId(),
                caseInfo.getAcceptDeptName(),
                caseInfo.getEntrustOrgName(),
                caseInfo.getDeadlineTime(),
                caseInfo.getUrgentFlag(),
                caseInfo.getSubmittedTime(),
                caseInfo.getCompletedTime(),
                caseInfo.getCreatedTime());
    }

    private String statusName(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "TO_ACCEPT" -> "待受理";
            case "ACCEPT_REVIEWING" -> "受理审核中";
            case "REJECTED_ACCEPTANCE" -> "受理退回";
            case "CORRECTION_PENDING" -> "补正中";
            case "PROCESSING" -> "鉴定办理中";
            case "REVIEWING" -> "审核中";
            case "DOC_ISSUING" -> "文书出具中";
            case "COMPLETED" -> "已办结";
            case "ARCHIVED" -> "待归档";
            case "TERMINATED" -> "已终止";
            default -> status;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
