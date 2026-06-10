package com.example.judicialappraisal.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.common.enums.TaskStatus;
import com.example.judicialappraisal.common.enums.WorkflowStatus;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.entity.CaseNodeInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseTaskCandidate;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.mapper.CaseNodeInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRuntimeService {

    private static final String MAIN_WF_CODE = "JUDICIAL_MAIN";
    private static final String MAIN_WF_NAME = "司法鉴定主流程";
    private static final String ACCEPT_NODE_CODE = "ACCEPT_REVIEW";
    private static final String ACCEPT_NODE_NAME = "受理审查";
    private static final String PROCESS_NODE_CODE = "PROCESSING";
    private static final String PROCESS_NODE_NAME = "鉴定办理";
    private static final String REVIEW_NODE_CODE = "REVIEW";
    private static final String REVIEW_NODE_NAME = "审核签发";
    private static final String DOC_NODE_CODE = "DOC_ISSUE";
    private static final String DOC_NODE_NAME = "文书出具";
    private static final String ARCHIVE_NODE_CODE = "ARCHIVE";
    private static final String ARCHIVE_NODE_NAME = "归档";
    private static final String WORKFLOW_RUNNING = WorkflowStatus.RUNNING.name().toLowerCase();
    private static final String WORKFLOW_COMPLETED = WorkflowStatus.COMPLETED.name().toLowerCase();
    private static final String WORKFLOW_TERMINATED = WorkflowStatus.TERMINATED.name().toLowerCase();
    private static final String NODE_RUNNING = "running";
    private static final String NODE_COMPLETED = "completed";
    private static final String NODE_CANCELLED = "cancelled";
    private static final String TASK_PENDING = TaskStatus.PENDING.name().toLowerCase();
    private static final String TASK_CLAIMED = TaskStatus.CLAIMED.name().toLowerCase();
    private static final String TASK_COMPLETED = TaskStatus.COMPLETED.name().toLowerCase();
    private static final String TASK_CANCELLED = TaskStatus.CANCELLED.name().toLowerCase();
    private static final String TASK_TYPE_SINGLE = "single";
    private static final String TASK_TYPE_CANDIDATE = "candidate";
    private static final String ENABLED_STATUS = "enabled";

    private final CaseInfoMapper caseInfoMapper;
    private final CaseWfInstanceMapper caseWfInstanceMapper;
    private final CaseNodeInstanceMapper caseNodeInstanceMapper;
    private final CaseTaskMapper caseTaskMapper;
    private final WfNodeDefMapper wfNodeDefMapper;
    private final CaseTaskCandidateMapper caseTaskCandidateMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public WorkflowRuntimeService(
            CaseInfoMapper caseInfoMapper,
            CaseWfInstanceMapper caseWfInstanceMapper,
            CaseNodeInstanceMapper caseNodeInstanceMapper,
            CaseTaskMapper caseTaskMapper,
            WfNodeDefMapper wfNodeDefMapper,
            CaseTaskCandidateMapper caseTaskCandidateMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper) {
        this.caseInfoMapper = caseInfoMapper;
        this.caseWfInstanceMapper = caseWfInstanceMapper;
        this.caseNodeInstanceMapper = caseNodeInstanceMapper;
        this.caseTaskMapper = caseTaskMapper;
        this.wfNodeDefMapper = wfNodeDefMapper;
        this.caseTaskCandidateMapper = caseTaskCandidateMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
    }

    @Transactional
    public WorkflowActionResult submitCase(Long caseId, Long operatorId, String operatorName, String opinion) {
        CaseInfo caseInfo = requireCase(caseId);
        if (!CaseStatus.DRAFT.name().equals(caseInfo.getCaseStatus())) {
            throw new BusinessException("仅草稿案件允许提交");
        }
        if (operatorId == null) {
            throw new BusinessException("提交人不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        CaseWfInstance wfInstance = createOrReuseWfInstance(caseInfo, operatorId, now);
        CaseNodeInstance nodeInstance = createNodeInstance(caseId, wfInstance.getId(), ACCEPT_NODE_CODE, ACCEPT_NODE_NAME, now);
        CaseTask task = createTask(
                caseInfo,
                wfInstance.getId(),
                nodeInstance.getId(),
                ACCEPT_NODE_CODE,
                ACCEPT_NODE_NAME,
                operatorId,
                defaultName(operatorName),
                now);

        caseInfo.setCaseStatus(CaseStatus.TO_ACCEPT.name());
        caseInfo.setCurrentNodeCode(ACCEPT_NODE_CODE);
        caseInfo.setCurrentNodeName(ACCEPT_NODE_NAME);
        caseInfo.setCurrentHandlerId(task.getAssigneeId());
        caseInfo.setCurrentHandlerName(task.getAssigneeName());
        caseInfo.setSubmittedTime(now);
        if (isBlank(caseInfo.getCaseNo())) {
            caseInfo.setCaseNo("JA-" + caseId);
        }
        caseInfoMapper.updateById(caseInfo);

        wfInstance.setCurrentNodeCode(ACCEPT_NODE_CODE);
        wfInstance.setCurrentNodeName(ACCEPT_NODE_NAME);
        caseWfInstanceMapper.updateById(wfInstance);
        return new WorkflowActionResult(caseId, task.getId(), ActionCode.SUBMIT.name(), true, "提交成功");
    }

    @Transactional
    public WorkflowActionResult completeTask(Long caseId, WorkflowActionRequest request) {
        validateTaskActionRequest(request);

        CaseInfo caseInfo = requireCase(caseId);
        CaseTask task = requireTask(caseId, request.taskId());
        validateTaskCanProcess(task);

        LocalDateTime now = LocalDateTime.now();
        return switch (request.actionCode()) {
            case CLAIM -> handleClaim(caseInfo, task, request, now);
            case ASSIGN -> handleAssign(caseInfo, task, request, now);
            case WITHDRAW -> handleWithdraw(caseInfo, task, request, now);
            case APPROVE, COMPLETE -> {
                completeCurrentTaskAndNode(task, request, now);
                yield handleApprove(caseInfo, task, request, now);
            }
            case RETURN -> {
                completeCurrentTaskAndNode(task, request, now);
                yield handleReturn(caseInfo, task, request, now);
            }
            case TERMINATE -> {
                completeCurrentTaskAndNode(task, request, now);
                yield handleTerminate(caseInfo, task, request, now);
            }
            case REOPEN -> {
                completeCurrentTaskAndNode(task, request, now);
                yield handleReopen(caseInfo, task, request, now);
            }
            default -> {
                completeCurrentTaskAndNode(task, request, now);
                yield new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "办理成功");
            }
        };
    }

    private void validateTaskActionRequest(WorkflowActionRequest request) {
        if (request.actionCode() != ActionCode.SUBMIT && request.taskId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        if (requiresReason(request.actionCode()) && isBlank(request.reason())) {
            throw new BusinessException("原因不能为空");
        }
        if (requiresAssignee(request.actionCode()) && request.assigneeId() == null) {
            throw new BusinessException("承办人不能为空");
        }
    }

    private boolean requiresReason(ActionCode actionCode) {
        return actionCode == ActionCode.RETURN
                || actionCode == ActionCode.WITHDRAW
                || actionCode == ActionCode.TERMINATE
                || actionCode == ActionCode.REOPEN;
    }

    private boolean requiresAssignee(ActionCode actionCode) {
        return actionCode == ActionCode.CLAIM || actionCode == ActionCode.ASSIGN;
    }

    private WorkflowActionResult handleClaim(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        validateClaimEligibility(task, request.assigneeId());
        updateActiveTaskAssignee(task, request, TASK_CLAIMED, now);
        updateNodeHandler(task.getNodeInstanceId(), task.getAssigneeId(), task.getAssigneeName());
        syncCaseCurrentHandler(caseInfo, task.getAssigneeId(), task.getAssigneeName());
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "认领成功");
    }

    private WorkflowActionResult handleAssign(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        updateActiveTaskAssignee(task, request, TASK_PENDING, now);
        clearCandidateRows(task.getId());
        updateNodeHandler(task.getNodeInstanceId(), task.getAssigneeId(), task.getAssigneeName());
        syncCaseCurrentHandler(caseInfo, task.getAssigneeId(), task.getAssigneeName());
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "转办成功");
    }

    private void validateClaimEligibility(CaseTask task, Long assigneeId) {
        if (task.getClaimedBy() != null || task.getClaimedTime() != null) {
            throw new BusinessException("\u4efb\u52a1\u5df2\u88ab\u5176\u4ed6\u7528\u6237\u8ba4\u9886\uff0c\u65e0\u6cd5\u91cd\u590d\u8ba4\u9886");
        }
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new BusinessException("\u4efb\u52a1\u53ea\u80fd\u5728\u5f85\u529e\u72b6\u6001\u4e0b\u8ba4\u9886");
        }

        List<CaseTaskCandidate> candidates = selectCandidateRows(task.getId());
        if (candidates.isEmpty()) {
            return;
        }
        if (task.getAssigneeId() != null) {
            throw new BusinessException("\u4efb\u52a1\u5df2\u5206\u914d\u7ed9\u5176\u4ed6\u7528\u6237");
        }

        boolean matchedByUser = candidates.stream()
                .anyMatch(candidate -> Objects.equals(candidate.getCandidateUserId(), assigneeId));
        if (matchedByUser) {
            return;
        }

        Set<Long> candidateRoleIds = candidates.stream()
                .map(CaseTaskCandidate::getCandidateRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (candidateRoleIds.isEmpty()) {
            throw new BusinessException("当前用户不在任务候选范围内");
        }

        boolean matchedByRole = selectUserRoleIds(assigneeId).stream().anyMatch(candidateRoleIds::contains);
        if (!matchedByRole) {
            throw new BusinessException("当前用户不在任务候选范围内");
        }
    }

    private List<CaseTaskCandidate> selectCandidateRows(Long taskId) {
        List<CaseTaskCandidate> candidates = caseTaskCandidateMapper.selectList(new LambdaQueryWrapper<CaseTaskCandidate>()
                .eq(CaseTaskCandidate::getTaskId, taskId));
        return candidates == null ? List.of() : candidates;
    }

    private List<Long> selectUserRoleIds(Long userId) {
        List<Long> roleIds = sysUserRoleMapper.selectEnabledRoleIdsByUserId(userId);
        return roleIds == null ? List.of() : roleIds;
    }

    private void clearCandidateRows(Long taskId) {
        caseTaskCandidateMapper.delete(new LambdaQueryWrapper<CaseTaskCandidate>()
                .eq(CaseTaskCandidate::getTaskId, taskId));
    }

    private WorkflowActionResult handleWithdraw(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        CaseWfInstance wfInstance = requireRunningInstance(task.getWfInstanceId(), caseInfo.getId());
        CaseNodeInstance currentNode = requireActiveNodeInstance(task.getNodeInstanceId(), caseInfo.getId());
        CaseNodeInstance previousCompletedNode = findPreviousCompletedNodeInstance(wfInstance.getId(), currentNode.getId());
        if (previousCompletedNode == null) {
            throw new BusinessException("当前节点不存在可撤回的上一已完成节点");
        }

        cancelActiveTasks(currentNode.getId(), request, now);

        currentNode.setStatus(NODE_CANCELLED);
        currentNode.setCompletedTime(now);
        currentNode.setHandlerId(task.getAssigneeId());
        currentNode.setHandlerName(task.getAssigneeName());
        currentNode.setResultAction(request.actionCode().name());
        currentNode.setResultOpinion(resolveOutcomeOpinion(request));
        caseNodeInstanceMapper.updateById(currentNode);

        CaseTask previousCompletedTask = findLatestCompletedTask(previousCompletedNode.getId());
        Long previousAssigneeId = previousCompletedTask != null ? previousCompletedTask.getAssigneeId() : previousCompletedNode.getHandlerId();
        String previousAssigneeName = previousCompletedTask != null && !isBlank(previousCompletedTask.getAssigneeName())
                ? previousCompletedTask.getAssigneeName()
                : defaultName(previousCompletedNode.getHandlerName());

        CaseNodeInstance recreatedNode = createNodeInstance(
                caseInfo.getId(),
                wfInstance.getId(),
                previousCompletedNode.getNodeCode(),
                previousCompletedNode.getNodeName(),
                now);
        recreatedNode.setHandlerId(previousAssigneeId);
        recreatedNode.setHandlerName(defaultName(previousAssigneeName));
        caseNodeInstanceMapper.updateById(recreatedNode);

        CaseTask recreatedTask = createTask(
                caseInfo,
                wfInstance.getId(),
                recreatedNode.getId(),
                previousCompletedNode.getNodeCode(),
                previousCompletedNode.getNodeName(),
                previousAssigneeId,
                previousAssigneeName,
                now);

        caseInfo.setCaseStatus(resolveCaseStatusForNode(previousCompletedNode.getNodeCode()).name());
        caseInfo.setCurrentNodeCode(previousCompletedNode.getNodeCode());
        caseInfo.setCurrentNodeName(previousCompletedNode.getNodeName());
        caseInfo.setCurrentHandlerId(recreatedTask.getAssigneeId());
        caseInfo.setCurrentHandlerName(recreatedTask.getAssigneeName());
        caseInfoMapper.updateById(caseInfo);

        wfInstance.setCurrentNodeCode(previousCompletedNode.getNodeCode());
        wfInstance.setCurrentNodeName(previousCompletedNode.getNodeName());
        caseWfInstanceMapper.updateById(wfInstance);
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "撤回成功");
    }

    private void updateActiveTaskAssignee(CaseTask task, WorkflowActionRequest request, String taskStatus, LocalDateTime now) {
        task.setStatus(taskStatus);
        task.setAssigneeId(request.assigneeId());
        task.setAssigneeName(resolveRequestedAssigneeName(request, task));
        task.setStartedTime(task.getStartedTime() == null ? now : task.getStartedTime());
        if (TASK_CLAIMED.equals(taskStatus)) {
            task.setClaimedBy(request.assigneeId());
            task.setClaimedTime(now);
        } else if (TASK_PENDING.equals(taskStatus)) {
            task.setClaimedBy(null);
            task.setClaimedTime(null);
        }
        caseTaskMapper.updateById(task);
    }

    private void updateNodeHandler(Long nodeInstanceId, Long handlerId, String handlerName) {
        CaseNodeInstance nodeInstance = caseNodeInstanceMapper.selectById(nodeInstanceId);
        if (nodeInstance == null) {
            return;
        }
        nodeInstance.setHandlerId(handlerId);
        nodeInstance.setHandlerName(defaultName(handlerName));
        caseNodeInstanceMapper.updateById(nodeInstance);
    }

    private void syncCaseCurrentHandler(CaseInfo caseInfo, Long handlerId, String handlerName) {
        caseInfo.setCurrentHandlerId(handlerId);
        caseInfo.setCurrentHandlerName(defaultName(handlerName));
        caseInfoMapper.updateById(caseInfo);
    }

    private void completeCurrentTaskAndNode(CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        task.setStatus(TASK_COMPLETED);
        task.setStartedTime(task.getStartedTime() == null ? now : task.getStartedTime());
        task.setCompletedTime(now);
        task.setResultAction(request.actionCode().name());
        task.setResultOpinion(resolveOutcomeOpinion(request));
        caseTaskMapper.updateById(task);

        CaseNodeInstance nodeInstance = caseNodeInstanceMapper.selectById(task.getNodeInstanceId());
        if (nodeInstance != null) {
            nodeInstance.setStatus(NODE_COMPLETED);
            nodeInstance.setCompletedTime(now);
            nodeInstance.setHandlerId(resolveOperatorId(request, task));
            nodeInstance.setHandlerName(resolveOperatorName(request, task));
            nodeInstance.setResultAction(request.actionCode().name());
            nodeInstance.setResultOpinion(resolveOutcomeOpinion(request));
            caseNodeInstanceMapper.updateById(nodeInstance);
        }
    }

    private void cancelActiveTasks(Long nodeInstanceId, WorkflowActionRequest request, LocalDateTime now) {
        List<CaseTask> tasks = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getNodeInstanceId, nodeInstanceId)
                .orderByDesc(CaseTask::getId));
        for (CaseTask currentTask : tasks) {
            if (TASK_COMPLETED.equals(currentTask.getStatus()) || TASK_CANCELLED.equals(currentTask.getStatus())) {
                continue;
            }
            currentTask.setStatus(TASK_CANCELLED);
            currentTask.setStartedTime(currentTask.getStartedTime() == null ? now : currentTask.getStartedTime());
            currentTask.setCompletedTime(now);
            currentTask.setResultAction(request.actionCode().name());
            currentTask.setResultOpinion(resolveOutcomeOpinion(request));
            caseTaskMapper.updateById(currentTask);
        }
    }

    private WorkflowActionResult handleApprove(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        if (ACCEPT_NODE_CODE.equals(task.getNodeCode())) {
            return moveToNextNode(
                    caseInfo,
                    task,
                    request,
                    now,
                    CaseStatus.PROCESSING,
                    PROCESS_NODE_CODE,
                    PROCESS_NODE_NAME,
                    resolveNextAssigneeId(request, task),
                    resolveNextAssigneeName(request, task),
                    "受理通过");
        }
        if (PROCESS_NODE_CODE.equals(task.getNodeCode())) {
            return moveToNextNode(
                    caseInfo,
                    task,
                    request,
                    now,
                    CaseStatus.REVIEWING,
                    REVIEW_NODE_CODE,
                    REVIEW_NODE_NAME,
                    resolveNextAssigneeId(request, task),
                    resolveNextAssigneeName(request, task),
                    "鉴定办理完成");
        }
        if (REVIEW_NODE_CODE.equals(task.getNodeCode())) {
            return moveToNextNode(
                    caseInfo,
                    task,
                    request,
                    now,
                    CaseStatus.DOC_ISSUING,
                    DOC_NODE_CODE,
                    DOC_NODE_NAME,
                    resolveNextAssigneeId(request, task),
                    resolveNextAssigneeName(request, task),
                    "审核通过");
        }
        if (DOC_NODE_CODE.equals(task.getNodeCode())) {
            return moveToNextNode(
                    caseInfo,
                    task,
                    request,
                    now,
                    CaseStatus.ARCHIVED,
                    ARCHIVE_NODE_CODE,
                    ARCHIVE_NODE_NAME,
                    resolveNextAssigneeId(request, task),
                    resolveNextAssigneeName(request, task),
                    "文书出具完成");
        }
        if (ARCHIVE_NODE_CODE.equals(task.getNodeCode())) {
            caseInfo.setCaseStatus(CaseStatus.COMPLETED.name());
            caseInfo.setCurrentNodeCode(null);
            caseInfo.setCurrentNodeName(null);
            caseInfo.setCurrentHandlerId(null);
            caseInfo.setCurrentHandlerName(null);
            caseInfo.setCompletedTime(now);
            caseInfoMapper.updateById(caseInfo);
            finishWorkflowInstance(caseInfo.getId(), now);
            return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "案件已办结");
        }
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "办理成功");
    }

    private WorkflowActionResult handleReturn(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        return moveToNextNode(
                caseInfo,
                task,
                request,
                now,
                CaseStatus.CORRECTION_PENDING,
                PROCESS_NODE_CODE,
                PROCESS_NODE_NAME,
                resolveNextAssigneeId(request, task),
                resolveNextAssigneeName(request, task),
                "已退回修改");
    }

    private WorkflowActionResult handleTerminate(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        caseInfo.setCaseStatus(CaseStatus.TERMINATED.name());
        caseInfo.setCurrentNodeCode(null);
        caseInfo.setCurrentNodeName(null);
        caseInfo.setCurrentHandlerId(null);
        caseInfo.setCurrentHandlerName(null);
        caseInfoMapper.updateById(caseInfo);
        terminateWorkflowInstance(caseInfo.getId(), now);
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "案件已终止");
    }

    private WorkflowActionResult handleReopen(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        return moveToNextNode(
                caseInfo,
                task,
                request,
                now,
                CaseStatus.PROCESSING,
                PROCESS_NODE_CODE,
                PROCESS_NODE_NAME,
                resolveNextAssigneeId(request, task),
                resolveNextAssigneeName(request, task),
                "案件已重启办理");
    }

    private WorkflowActionResult moveToNextNode(
            CaseInfo caseInfo,
            CaseTask completedTask,
            WorkflowActionRequest request,
            LocalDateTime now,
            CaseStatus nextStatus,
            String nextNodeCode,
            String nextNodeName,
            Long nextAssigneeId,
            String nextAssigneeName,
            String message) {
        CaseWfInstance wfInstance = requireRunningInstance(caseInfo.getId());
        CaseNodeInstance nextNode = createNodeInstance(caseInfo.getId(), wfInstance.getId(), nextNodeCode, nextNodeName, now);
        CaseTask nextTask = createNextNodeTask(
                caseInfo,
                wfInstance,
                nextNode.getId(),
                nextNodeCode,
                nextNodeName,
                request,
                nextAssigneeId,
                nextAssigneeName,
                now);

        caseInfo.setCaseStatus(nextStatus.name());
        caseInfo.setCurrentNodeCode(nextNodeCode);
        caseInfo.setCurrentNodeName(nextNodeName);
        caseInfo.setCurrentHandlerId(nextTask.getAssigneeId());
        caseInfo.setCurrentHandlerName(nextTask.getAssigneeName());
        caseInfoMapper.updateById(caseInfo);

        wfInstance.setCurrentNodeCode(nextNodeCode);
        wfInstance.setCurrentNodeName(nextNodeName);
        caseWfInstanceMapper.updateById(wfInstance);
        return new WorkflowActionResult(caseInfo.getId(), completedTask.getId(), request.actionCode().name(), true, message);
    }

    private CaseInfo requireCase(Long caseId) {
        CaseInfo caseInfo = caseInfoMapper.selectById(caseId);
        if (caseInfo == null) {
            throw new BusinessException("案件不存在");
        }
        return caseInfo;
    }

    private CaseTask requireTask(Long caseId, Long taskId) {
        CaseTask task = caseTaskMapper.selectById(taskId);
        if (task == null || !caseId.equals(task.getCaseId())) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private void validateTaskCanProcess(CaseTask task) {
        if (TASK_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("任务已办理");
        }
        if (TASK_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("任务已取消");
        }
    }

    private CaseWfInstance createOrReuseWfInstance(CaseInfo caseInfo, Long operatorId, LocalDateTime now) {
        CaseWfInstance existing = caseWfInstanceMapper.selectOne(new LambdaQueryWrapper<CaseWfInstance>()
                .eq(CaseWfInstance::getCaseId, caseInfo.getId())
                .eq(CaseWfInstance::getStatus, WORKFLOW_RUNNING)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        CaseWfInstance instance = new CaseWfInstance();
        instance.setCaseId(caseInfo.getId());
        instance.setWfId(1L);
        instance.setWfCode(MAIN_WF_CODE);
        instance.setWfName(MAIN_WF_NAME);
        instance.setStatus(WORKFLOW_RUNNING);
        instance.setStartedBy(operatorId);
        instance.setStartedTime(now);
        caseWfInstanceMapper.insert(instance);
        return instance;
    }

    private CaseWfInstance requireRunningInstance(Long caseId) {
        CaseWfInstance wfInstance = caseWfInstanceMapper.selectOne(new LambdaQueryWrapper<CaseWfInstance>()
                .eq(CaseWfInstance::getCaseId, caseId)
                .eq(CaseWfInstance::getStatus, WORKFLOW_RUNNING)
                .last("limit 1"));
        if (wfInstance == null) {
            throw new BusinessException("流程实例不存在");
        }
        return wfInstance;
    }

    private CaseWfInstance requireRunningInstance(Long wfInstanceId, Long caseId) {
        CaseWfInstance wfInstance = caseWfInstanceMapper.selectById(wfInstanceId);
        if (wfInstance == null || !caseId.equals(wfInstance.getCaseId()) || !WORKFLOW_RUNNING.equals(wfInstance.getStatus())) {
            throw new BusinessException("流程实例不存在");
        }
        return wfInstance;
    }

    private CaseNodeInstance requireActiveNodeInstance(Long nodeInstanceId, Long caseId) {
        CaseNodeInstance nodeInstance = caseNodeInstanceMapper.selectById(nodeInstanceId);
        if (nodeInstance == null || !caseId.equals(nodeInstance.getCaseId()) || !NODE_RUNNING.equals(nodeInstance.getStatus())) {
            throw new BusinessException("当前节点不是活动状态");
        }
        return nodeInstance;
    }

    private CaseNodeInstance findPreviousCompletedNodeInstance(Long wfInstanceId, Long currentNodeInstanceId) {
        return caseNodeInstanceMapper.selectOne(new LambdaQueryWrapper<CaseNodeInstance>()
                .eq(CaseNodeInstance::getWfInstanceId, wfInstanceId)
                .eq(CaseNodeInstance::getStatus, NODE_COMPLETED)
                .lt(CaseNodeInstance::getId, currentNodeInstanceId)
                .orderByDesc(CaseNodeInstance::getId)
                .last("limit 1"));
    }

    private CaseTask findLatestCompletedTask(Long nodeInstanceId) {
        return caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getNodeInstanceId, nodeInstanceId)
                .eq(CaseTask::getStatus, TASK_COMPLETED)
                .orderByDesc(CaseTask::getCompletedTime)
                .orderByDesc(CaseTask::getId)
                .last("limit 1"));
    }

    private CaseStatus resolveCaseStatusForNode(String nodeCode) {
        if (ACCEPT_NODE_CODE.equals(nodeCode)) {
            return CaseStatus.TO_ACCEPT;
        }
        if (PROCESS_NODE_CODE.equals(nodeCode)) {
            return CaseStatus.PROCESSING;
        }
        if (REVIEW_NODE_CODE.equals(nodeCode)) {
            return CaseStatus.REVIEWING;
        }
        if (DOC_NODE_CODE.equals(nodeCode)) {
            return CaseStatus.DOC_ISSUING;
        }
        if (ARCHIVE_NODE_CODE.equals(nodeCode)) {
            return CaseStatus.ARCHIVED;
        }
        throw new BusinessException("不支持的节点状态映射");
    }

    private CaseNodeInstance createNodeInstance(Long caseId, Long wfInstanceId, String nodeCode, String nodeName, LocalDateTime now) {
        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseId);
        nodeInstance.setWfInstanceId(wfInstanceId);
        nodeInstance.setNodeCode(nodeCode);
        nodeInstance.setNodeName(nodeName);
        nodeInstance.setStatus(NODE_RUNNING);
        nodeInstance.setStartedTime(now);
        caseNodeInstanceMapper.insert(nodeInstance);
        return nodeInstance;
    }

    private CaseTask createNextNodeTask(
            CaseInfo caseInfo,
            CaseWfInstance wfInstance,
            Long nodeInstanceId,
            String nodeCode,
            String nodeName,
            WorkflowActionRequest request,
            Long inheritedAssigneeId,
            String inheritedAssigneeName,
            LocalDateTime now) {
        if (request.assigneeId() != null) {
            return createTask(caseInfo, wfInstance.getId(), nodeInstanceId, nodeCode, nodeName, inheritedAssigneeId, inheritedAssigneeName, now);
        }

        WfNodeDef nodeDef = findNodeDef(wfInstance.getWfId(), nodeCode);
        if (nodeDef != null && !isBlank(nodeDef.getHandlerRoleRule())) {
            return createCandidateTask(caseInfo, wfInstance.getId(), nodeInstanceId, nodeCode, nodeName, nodeDef, now);
        }

        return createTask(caseInfo, wfInstance.getId(), nodeInstanceId, nodeCode, nodeName, inheritedAssigneeId, inheritedAssigneeName, now);
    }

    private WfNodeDef findNodeDef(Long wfId, String nodeCode) {
        if (wfId == null || isBlank(nodeCode)) {
            return null;
        }
        return wfNodeDefMapper.selectOne(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getWfId, wfId)
                .eq(WfNodeDef::getNodeCode, nodeCode)
                .eq(WfNodeDef::getEnabled, 1)
                .last("limit 1"));
    }

    private CaseTask createCandidateTask(
            CaseInfo caseInfo,
            Long wfInstanceId,
            Long nodeInstanceId,
            String nodeCode,
            String nodeName,
            WfNodeDef nodeDef,
            LocalDateTime now) {
        List<SysRole> roles = resolveHandlerRoles(nodeDef.getHandlerRoleRule());
        if (roles.isEmpty()) {
            throw new BusinessException("流程节点承办角色规则未匹配到启用角色");
        }

        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstanceId);
        task.setNodeInstanceId(nodeInstanceId);
        task.setTaskType(isBlank(nodeDef.getTaskType()) ? TASK_TYPE_CANDIDATE : nodeDef.getTaskType());
        task.setTaskTitle(caseInfo.getCaseTitle() + " - " + nodeName);
        task.setNodeCode(nodeCode);
        task.setNodeName(nodeName);
        task.setStatus(TASK_PENDING);
        task.setAssigneeId(null);
        task.setAssigneeName(null);
        task.setDeadlineTime(caseInfo.getDeadlineTime());
        task.setStartedTime(now);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);

        insertCandidateRows(task, roles, now);
        return task;
    }

    private List<SysRole> resolveHandlerRoles(String handlerRoleRule) {
        List<String> tokens = parseHandlerRoleRule(handlerRoleRule);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = tokens.stream()
                .filter(this::isLongToken)
                .map(Long::valueOf)
                .distinct()
                .toList();
        List<String> roleCodes = tokens.stream()
                .filter(token -> !isLongToken(token))
                .distinct()
                .toList();
        if (roleIds.isEmpty() && roleCodes.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getDeleted, 0)
                .eq(SysRole::getStatus, ENABLED_STATUS)
                .and(wrapper -> {
                    if (!roleIds.isEmpty() && !roleCodes.isEmpty()) {
                        wrapper.in(SysRole::getId, roleIds).or().in(SysRole::getRoleCode, roleCodes);
                    } else if (!roleIds.isEmpty()) {
                        wrapper.in(SysRole::getId, roleIds);
                    } else {
                        wrapper.in(SysRole::getRoleCode, roleCodes);
                    }
                });
        List<SysRole> roles = sysRoleMapper.selectList(queryWrapper);
        return roles == null ? List.of() : roles;
    }

    private List<String> parseHandlerRoleRule(String handlerRoleRule) {
        if (isBlank(handlerRoleRule)) {
            return List.of();
        }
        return Arrays.stream(handlerRoleRule.trim().split("[,;|\\s]+"))
                .filter(token -> !isBlank(token))
                .distinct()
                .toList();
    }

    private void insertCandidateRows(CaseTask task, List<SysRole> roles, LocalDateTime now) {
        List<Long> roleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return;
        }

        List<SysUserRoleMapper.UserRoleCandidateRow> userRows = sysUserRoleMapper.selectEnabledUserRoleCandidates(roleIds);
        Map<Long, List<Long>> userIdsByRoleId = (userRows == null ? List.<SysUserRoleMapper.UserRoleCandidateRow>of() : userRows).stream()
                .collect(Collectors.groupingBy(
                        SysUserRoleMapper.UserRoleCandidateRow::roleId,
                        LinkedHashMap::new,
                        Collectors.mapping(SysUserRoleMapper.UserRoleCandidateRow::userId, Collectors.toList())));

        for (Long roleId : roleIds) {
            List<Long> userIds = userIdsByRoleId.getOrDefault(roleId, List.of()).stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (userIds.isEmpty()) {
                insertCandidateRow(task, null, roleId, now);
                continue;
            }
            for (Long userId : userIds) {
                insertCandidateRow(task, userId, roleId, now);
            }
        }
    }

    private void insertCandidateRow(CaseTask task, Long userId, Long roleId, LocalDateTime now) {
        CaseTaskCandidate candidate = new CaseTaskCandidate();
        candidate.setTaskId(task.getId());
        candidate.setCaseId(task.getCaseId());
        candidate.setCandidateUserId(userId);
        candidate.setCandidateRoleId(roleId);
        candidate.setCreatedTime(now);
        caseTaskCandidateMapper.insert(candidate);
    }

    private boolean isLongToken(String token) {
        try {
            Long.parseLong(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private CaseTask createTask(
            CaseInfo caseInfo,
            Long wfInstanceId,
            Long nodeInstanceId,
            String nodeCode,
            String nodeName,
            Long assigneeId,
            String assigneeName,
            LocalDateTime now) {
        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstanceId);
        task.setNodeInstanceId(nodeInstanceId);
        task.setTaskType(TASK_TYPE_SINGLE);
        task.setTaskTitle(caseInfo.getCaseTitle() + " - " + nodeName);
        task.setNodeCode(nodeCode);
        task.setNodeName(nodeName);
        task.setStatus(TASK_PENDING);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(defaultName(assigneeName));
        task.setDeadlineTime(caseInfo.getDeadlineTime());
        task.setStartedTime(now);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);
        return task;
    }

    private void finishWorkflowInstance(Long caseId, LocalDateTime now) {
        CaseWfInstance wfInstance = requireRunningInstance(caseId);
        wfInstance.setStatus(WORKFLOW_COMPLETED);
        wfInstance.setCompletedTime(now);
        caseWfInstanceMapper.updateById(wfInstance);
    }

    private void terminateWorkflowInstance(Long caseId, LocalDateTime now) {
        CaseWfInstance wfInstance = requireRunningInstance(caseId);
        wfInstance.setStatus(WORKFLOW_TERMINATED);
        wfInstance.setTerminatedTime(now);
        caseWfInstanceMapper.updateById(wfInstance);
    }

    private Long resolveOperatorId(WorkflowActionRequest request, CaseTask task) {
        return request.assigneeId() != null ? request.assigneeId() : task.getAssigneeId();
    }

    private String resolveOperatorName(WorkflowActionRequest request, CaseTask task) {
        return !isBlank(request.assigneeName()) ? request.assigneeName() : task.getAssigneeName();
    }

    private Long resolveNextAssigneeId(WorkflowActionRequest request, CaseTask task) {
        return request.assigneeId() != null ? request.assigneeId() : task.getAssigneeId();
    }

    private String resolveNextAssigneeName(WorkflowActionRequest request, CaseTask task) {
        if (!isBlank(request.assigneeName())) {
            return request.assigneeName();
        }
        return defaultName(task.getAssigneeName());
    }

    private String resolveRequestedAssigneeName(WorkflowActionRequest request, CaseTask task) {
        if (!isBlank(request.assigneeName())) {
            return request.assigneeName();
        }
        if (request.assigneeId() != null && request.assigneeId().equals(task.getAssigneeId()) && !isBlank(task.getAssigneeName())) {
            return task.getAssigneeName();
        }
        return defaultName(null);
    }

    private String resolveOutcomeOpinion(WorkflowActionRequest request) {
        if (!isBlank(request.opinion())) {
            return request.opinion();
        }
        return request.reason();
    }

    private String defaultName(String name) {
        return isBlank(name) ? "管理员" : name;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
