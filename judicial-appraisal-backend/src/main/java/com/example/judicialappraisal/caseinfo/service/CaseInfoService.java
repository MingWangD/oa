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
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.PageResult;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.service.WorkflowRuntimeService;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CaseInfoService extends ServiceImpl<CaseInfoMapper, CaseInfo> {

    private final WorkflowRuntimeService workflowRuntimeService;

    public CaseInfoService(WorkflowRuntimeService workflowRuntimeService) {
        this.workflowRuntimeService = workflowRuntimeService;
    }

    public CaseInfo createDraft(CaseCreateRequest request) {
        return createDraft(request, null);
    }

    public CaseInfo createDraft(CaseCreateRequest request, Long currentUserId) {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setCaseTitle(request.caseTitle());
        caseInfo.setCaseType(request.caseType());
        caseInfo.setEntrustOrgName(request.entrustOrgName());
        caseInfo.setAcceptDeptId(request.acceptDeptId());
        caseInfo.setCaseStatus(CaseStatus.DRAFT.name());
        caseInfo.setCreatedBy(currentUserId);
        caseInfo.setUpdatedBy(currentUserId);
        caseInfo.setDeleted(0);
        save(caseInfo);
        return caseInfo;
    }

    public PageResult<CaseListResponse> pageList(CaseQueryRequest request) {
        Page<CaseInfo> page = page(
                Page.of(request.pageNo(), request.pageSize()),
                new LambdaQueryWrapper<CaseInfo>()
                        .and(hasText(request.keyword()), wrapper -> wrapper
                                .like(CaseInfo::getCaseTitle, request.keyword())
                                .or()
                                .like(CaseInfo::getCaseNo, request.keyword()))
                        .eq(hasText(request.caseStatus()), CaseInfo::getCaseStatus, request.caseStatus())
                        .eq(request.acceptDeptId() != null, CaseInfo::getAcceptDeptId, request.acceptDeptId())
                        .eq(request.currentHandlerId() != null, CaseInfo::getCurrentHandlerId, request.currentHandlerId())
                        .eq(CaseInfo::getDeleted, 0)
                        .orderByDesc(CaseInfo::getId));
        return new PageResult<>(page.getRecords().stream().map(this::toListResponse).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public WorkflowActionResult submitCase(Long caseId, CaseSubmitRequest request) {
        return workflowRuntimeService.submitCase(caseId, toSubmitRequest(request));
    }

    public WorkflowActionResult submitCase(Long caseId, CaseSubmitRequest request, Long currentUserId, String currentUserName) {
        return workflowRuntimeService.submitCase(caseId, toSubmitRequest(request), currentUserId, currentUserName);
    }

    private com.example.judicialappraisal.workflow.dto.WorkflowActionRequest toSubmitRequest(CaseSubmitRequest request) {
        com.example.judicialappraisal.common.enums.ActionCode submitCode = com.example.judicialappraisal.common.enums.ActionCode.SUBMIT;
        return new com.example.judicialappraisal.workflow.dto.WorkflowActionRequest(
                null, submitCode, request.opinion(), null, null, null, null, null);
    }

    public CaseInfo getDetail(Long caseId) {
        CaseInfo caseInfo = getById(caseId);
        if (caseInfo != null && !Objects.equals(caseInfo.getDeleted(), 1)) {
            return caseInfo;
        }
        CaseInfo fallback = new CaseInfo();
        fallback.setId(caseId);
        fallback.setCaseTitle("示例司法鉴定案件");
        fallback.setCaseStatus(CaseStatus.DRAFT.name());
        return fallback;
    }

    public void deleteDraft(Long caseId, CurrentUserInfo currentUser) {
        CaseInfo caseInfo = getById(caseId);
        if (caseInfo == null || Objects.equals(caseInfo.getDeleted(), 1)) {
            throw new BusinessException("草稿不存在或已删除");
        }
        if (!CaseStatus.DRAFT.name().equals(caseInfo.getCaseStatus())) {
            throw new BusinessException("只有尚未提交的草稿可以删除");
        }
        if (!isAdmin(currentUser) && !Objects.equals(caseInfo.getCreatedBy(), currentUser.id())) {
            throw new BusinessException("只能删除本人创建的草稿");
        }
        removeById(caseId);
    }

    private boolean isAdmin(CurrentUserInfo currentUser) {
        return currentUser.roles().stream().anyMatch(role -> "ADMIN".equalsIgnoreCase(role.code()));
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
