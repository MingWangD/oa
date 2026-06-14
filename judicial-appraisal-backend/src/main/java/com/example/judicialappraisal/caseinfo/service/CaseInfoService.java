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
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseTaskCandidate;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.service.WorkflowRuntimeService;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CaseInfoService extends ServiceImpl<CaseInfoMapper, CaseInfo> {

    private final WorkflowRuntimeService workflowRuntimeService;
    private final CaseTaskMapper caseTaskMapper;
    private final CaseTaskCandidateMapper caseTaskCandidateMapper;

    public CaseInfoService(WorkflowRuntimeService workflowRuntimeService,
                           CaseTaskMapper caseTaskMapper,
                           CaseTaskCandidateMapper caseTaskCandidateMapper) {
        this.workflowRuntimeService = workflowRuntimeService;
        this.caseTaskMapper = caseTaskMapper;
        this.caseTaskCandidateMapper = caseTaskCandidateMapper;
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
        Page<CaseInfo> page = page(Page.of(request.pageNo(), request.pageSize()), caseQueryWrapper(request));
        return new PageResult<>(page.getRecords().stream().map(this::toListResponse).toList(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public PageResult<CaseListResponse> pageList(CaseQueryRequest request, CurrentUserInfo currentUser) {
        if (isAdmin(currentUser)) {
            return pageList(request);
        }
        List<CaseListResponse> filtered = list(caseQueryWrapper(request)).stream()
                .filter(caseInfo -> canReadCase(caseInfo, currentUser))
                .map(this::toListResponse)
                .toList();
        long total = filtered.size();
        long fromIndex = Math.max(0L, (request.pageNo() - 1L) * request.pageSize());
        if (fromIndex >= total) {
            return new PageResult<>(Collections.emptyList(), total, request.pageNo(), request.pageSize());
        }
        long toIndex = Math.min(total, fromIndex + request.pageSize());
        return new PageResult<>(filtered.subList((int) fromIndex, (int) toIndex), total, request.pageNo(), request.pageSize());
    }

    private LambdaQueryWrapper<CaseInfo> caseQueryWrapper(CaseQueryRequest request) {
        return new LambdaQueryWrapper<CaseInfo>()
                .and(hasText(request.keyword()), wrapper -> wrapper
                        .like(CaseInfo::getCaseTitle, request.keyword())
                        .or()
                        .like(CaseInfo::getCaseNo, request.keyword()))
                .eq(hasText(request.caseStatus()), CaseInfo::getCaseStatus, request.caseStatus())
                .eq(request.acceptDeptId() != null, CaseInfo::getAcceptDeptId, request.acceptDeptId())
                .eq(request.currentHandlerId() != null, CaseInfo::getCurrentHandlerId, request.currentHandlerId())
                .eq(CaseInfo::getDeleted, 0)
                .orderByDesc(CaseInfo::getId);
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

    public CaseInfo getDetail(Long caseId, CurrentUserInfo currentUser) {
        CaseInfo caseInfo = baseMapper.selectRawById(caseId);
        if (caseInfo == null || Objects.equals(caseInfo.getDeleted(), 1)) {
            throw new BusinessException(404, "案件不存在或已删除");
        }
        if (!canReadCase(caseInfo, currentUser)) {
            throw new BusinessException(403, "无权查看该案件");
        }
        return caseInfo;
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

    private boolean canReadCase(CaseInfo caseInfo, CurrentUserInfo currentUser) {
        if (isAdmin(currentUser)) {
            return true;
        }
        Long userId = currentUser.id();
        if (Objects.equals(caseInfo.getCreatedBy(), userId) || Objects.equals(caseInfo.getCurrentHandlerId(), userId)) {
            return true;
        }
        List<Long> roleIds = currentUser.roles().stream().map(role -> role.id()).filter(Objects::nonNull).toList();
        List<CaseTask> tasks = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseInfo.getId())
                .and(wrapper -> wrapper
                        .eq(CaseTask::getAssigneeId, userId)
                        .or()
                        .eq(CaseTask::getClaimedBy, userId)));
        if (!tasks.isEmpty()) {
            return true;
        }
        List<Long> activeTaskIds = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                        .select(CaseTask::getId)
                        .eq(CaseTask::getCaseId, caseInfo.getId())
                        .in(CaseTask::getStatus, "pending", "processing"))
                .stream()
                .map(CaseTask::getId)
                .toList();
        if (activeTaskIds.isEmpty()) {
            return false;
        }
        return caseTaskCandidateMapper.selectCount(new LambdaQueryWrapper<CaseTaskCandidate>()
                .eq(CaseTaskCandidate::getCaseId, caseInfo.getId())
                .in(CaseTaskCandidate::getTaskId, activeTaskIds)
                .and(wrapper -> {
                    wrapper.eq(CaseTaskCandidate::getCandidateUserId, userId);
                    if (!roleIds.isEmpty()) {
                        wrapper.or().in(CaseTaskCandidate::getCandidateRoleId, roleIds);
                    }
                })) > 0;
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
