package com.example.judicialappraisal.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.common.enums.TaskStatus;
import com.example.judicialappraisal.common.enums.WorkflowStatus;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.knowledge.dto.ArchiveNodeRequest;
import com.example.judicialappraisal.knowledge.service.KnowledgeService;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.example.judicialappraisal.workflow.design.FormVersion;
import com.example.judicialappraisal.workflow.design.FormVersionMapper;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.workflow.dto.WorkflowActionResult;
import com.example.judicialappraisal.workflow.entity.CaseNodeInstance;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseTaskCandidate;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.mapper.CaseNodeInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRuntimeService {

    private static final String MAIN_WF_CODE = "received-entrust";
    private static final String MAIN_WF_NAME = "收到委托书";
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
    private static final String NODE_END = "end";
    private static final String TASK_PENDING = TaskStatus.PENDING.name().toLowerCase();
    private static final String TASK_CLAIMED = TaskStatus.CLAIMED.name().toLowerCase();
    private static final String TASK_COMPLETED = TaskStatus.COMPLETED.name().toLowerCase();
    private static final String TASK_CANCELLED = TaskStatus.CANCELLED.name().toLowerCase();
    private static final String TASK_SUBFLOW_RUNNING = "subflow_running";
    private static final String TASK_TYPE_SINGLE = "single";
    private static final String TASK_TYPE_CANDIDATE = "candidate";
    private static final String ENABLED_STATUS = "enabled";
    private static final String PUBLISHED_STATUS = "published";

    private final CaseInfoMapper caseInfoMapper;
    private final CaseWfInstanceMapper caseWfInstanceMapper;
    private final CaseSubflowInstanceMapper caseSubflowInstanceMapper;
    private final CaseNodeInstanceMapper caseNodeInstanceMapper;
    private final CaseTaskMapper caseTaskMapper;
    private final WfDefinitionMapper wfDefinitionMapper;
    private final WfNodeDefMapper wfNodeDefMapper;
    private final WfTransitionDefMapper wfTransitionDefMapper;
    private final FormVersionMapper formVersionMapper;
    private final CaseTaskCandidateMapper caseTaskCandidateMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    public WorkflowRuntimeService(
            CaseInfoMapper caseInfoMapper,
            CaseWfInstanceMapper caseWfInstanceMapper,
            CaseSubflowInstanceMapper caseSubflowInstanceMapper,
            CaseNodeInstanceMapper caseNodeInstanceMapper,
            CaseTaskMapper caseTaskMapper,
            WfDefinitionMapper wfDefinitionMapper,
            WfNodeDefMapper wfNodeDefMapper,
            WfTransitionDefMapper wfTransitionDefMapper,
            FormVersionMapper formVersionMapper,
            CaseTaskCandidateMapper caseTaskCandidateMapper,
            SysRoleMapper sysRoleMapper,
            SysUserRoleMapper sysUserRoleMapper,
            KnowledgeService knowledgeService,
            ObjectMapper objectMapper) {
        this.caseInfoMapper = caseInfoMapper;
        this.caseWfInstanceMapper = caseWfInstanceMapper;
        this.caseSubflowInstanceMapper = caseSubflowInstanceMapper;
        this.caseNodeInstanceMapper = caseNodeInstanceMapper;
        this.caseTaskMapper = caseTaskMapper;
        this.wfDefinitionMapper = wfDefinitionMapper;
        this.wfNodeDefMapper = wfNodeDefMapper;
        this.wfTransitionDefMapper = wfTransitionDefMapper;
        this.formVersionMapper = formVersionMapper;
        this.caseTaskCandidateMapper = caseTaskCandidateMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysUserRoleMapper = sysUserRoleMapper;
        this.knowledgeService = knowledgeService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowActionResult submitCase(Long caseId, WorkflowActionRequest request) {
        CurrentOperator currentOperator = currentOperatorFromSecurityContext();
        return submitCase(caseId, request, currentOperator.id(), currentOperator.name());
    }

    @Transactional
    public WorkflowActionResult submitCase(Long caseId, WorkflowActionRequest request, Long currentUserId, String currentUserName) {
        CaseInfo caseInfo = requireCase(caseId);
        if (!CaseStatus.DRAFT.name().equals(caseInfo.getCaseStatus())) {
            throw new BusinessException("仅草稿案件允许提交");
        }
        requireCurrentUser(currentUserId);

        LocalDateTime now = LocalDateTime.now();
        CaseWfInstance wfInstance = createOrReuseWfInstance(caseInfo, currentUserId, now);
        WfNodeDef firstNode = findFirstActionableNode(wfInstance.getWfId());
        String nodeCode = firstNode == null ? ACCEPT_NODE_CODE : firstNode.getNodeCode();
        String nodeName = firstNode == null ? ACCEPT_NODE_NAME : firstNode.getNodeName();
        CaseNodeInstance nodeInstance = createNodeInstance(caseId, wfInstance.getId(), null, nodeCode, nodeName, now);
        CaseTask task = createTask(
                caseInfo,
                wfInstance.getId(),
                null,
                nodeInstance.getId(),
                nodeCode,
                nodeName,
                currentUserId,
                defaultName(currentUserName),
                now);

        updateCaseFormData(caseInfo, request.formData());

        caseInfo.setCaseStatus(resolveCaseStatusForNode(nodeCode).name());
        caseInfo.setCurrentNodeCode(nodeCode);
        caseInfo.setCurrentNodeName(nodeName);
        caseInfo.setCurrentHandlerId(task.getAssigneeId());
        caseInfo.setCurrentHandlerName(task.getAssigneeName());
        caseInfo.setSubmittedTime(now);
        if (isBlank(caseInfo.getCaseNo())) {
            caseInfo.setCaseNo("JA-" + caseId);
        }
        caseInfoMapper.updateById(caseInfo);

        wfInstance.setCurrentNodeCode(nodeCode);
        wfInstance.setCurrentNodeName(nodeName);
        caseWfInstanceMapper.updateById(wfInstance);
        return new WorkflowActionResult(caseId, task.getId(), ActionCode.SUBMIT.name(), true, "提交成功");
    }

    private void updateCaseFormData(CaseInfo caseInfo, Map<String, Object> incomingFormData) {
        if (incomingFormData == null || incomingFormData.isEmpty()) {
            return;
        }
        Map<String, Object> existing = caseInfo.getFormData();
        if (existing == null) {
            existing = new LinkedHashMap<>();
        } else {
            existing = new LinkedHashMap<>(existing);
        }
        existing.putAll(incomingFormData);
        caseInfo.setFormData(existing);
        syncCaseSummaryFields(caseInfo, existing);
    }

    private void syncCaseSummaryFields(CaseInfo caseInfo, Map<String, Object> formData) {
        String caseNo = firstText(formData, "caseNo", "projectNo");
        if (!isBlank(caseNo)) {
            caseInfo.setCaseNo(caseNo);
        }
        String entrustOrgName = firstText(formData, "entrustOrgName", "clientName");
        if (!isBlank(entrustOrgName)) {
            caseInfo.setEntrustOrgName(entrustOrgName);
        }
    }

    private String firstText(Map<String, Object> formData, String... keys) {
        if (formData == null || formData.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = formData.get(key);
            if (value != null && !isBlank(String.valueOf(value))) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }


    @Transactional
    public WorkflowActionResult completeTask(Long caseId, WorkflowActionRequest request) {
        CurrentOperator currentOperator = currentOperatorFromSecurityContext();
        return completeTask(caseId, request, currentOperator.id(), currentOperator.name());
    }

    @Transactional
    public WorkflowActionResult completeTask(Long caseId, WorkflowActionRequest request, Long currentUserId, String currentUserName) {
        validateTaskActionRequest(request);
        requireCurrentUser(currentUserId);

        CaseInfo caseInfo = requireCase(caseId);
        CaseTask task = requireTask(caseId, request.taskId());
        validateTaskCanProcess(task);
        validateTaskOperator(task, currentUserId);
        
        // Merge incoming form data for validation
        Map<String, Object> mergedFormData = new LinkedHashMap<>();
        if (caseInfo.getFormData() != null) {
            mergedFormData.putAll(caseInfo.getFormData());
        }
        if (request.formData() != null) {
            mergedFormData.putAll(request.formData());
        }
        validateRequiredFormFields(mergedFormData, task, request);

        LocalDateTime now = LocalDateTime.now();
        switch (request.actionCode()) {
            case CLAIM:
                return handleClaim(caseInfo, task, request, currentUserId, currentUserName, now);
            case ASSIGN:
                return handleAssign(caseInfo, task, request, now);
            case WITHDRAW:
                return handleWithdraw(caseInfo, task, request, now);
            case APPROVE:
            case COMPLETE:
                completeCurrentTaskAndNode(caseInfo, task, request, currentUserId, currentUserName, now);
                return handleApprove(caseInfo, task, request, now);
            case RETURN:
                completeCurrentTaskAndNode(caseInfo, task, request, currentUserId, currentUserName, now);
                return handleReturn(caseInfo, task, request, now);
            case TERMINATE:
                completeCurrentTaskAndNode(caseInfo, task, request, currentUserId, currentUserName, now);
                return handleTerminate(caseInfo, task, request, now);
            case REOPEN:
                completeCurrentTaskAndNode(caseInfo, task, request, currentUserId, currentUserName, now);
                return handleReopen(caseInfo, task, request, now);
            default:
                completeCurrentTaskAndNode(caseInfo, task, request, currentUserId, currentUserName, now);
                return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "办理成功");
        }
    }

    private void validateTaskActionRequest(WorkflowActionRequest request) {
        if (request.actionCode() != ActionCode.SUBMIT && request.taskId() == null) {
            throw new BusinessException("任务ID不能为空");
        }
        if (requiresReason(request.actionCode()) && isBlank(request.reason()) && isBlank(request.opinion())) {
            throw new BusinessException("原因不能为空");
        }
        if (requiresAssignee(request.actionCode()) && request.resolvedNextAssigneeId() == null) {
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
        return actionCode == ActionCode.ASSIGN;
    }

    private List<SysRole> selectUserRoles(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Long> roleIds = sysUserRoleMapper.selectEnabledRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRole> roles = sysRoleMapper.selectBatchIds(roleIds);
        return roles == null ? List.of() : roles;
    }

    private boolean isFieldWritableForUser(Map<String, Object> field, Map<String, Object> permissionSchema, Long userId) {
        if (permissionSchema == null || !permissionSchema.containsKey("groups")) {
            return true;
        }
        String groupName = stringValue(field.get("group"));
        if (isBlank(groupName)) {
            return true;
        }
        Map<String, Map<String, Object>> groups = (Map<String, Map<String, Object>>) permissionSchema.get("groups");
        if (groups == null || !groups.containsKey(groupName)) {
            return true;
        }
        Map<String, Object> groupConfig = groups.get(groupName);
        if (groupConfig == null) {
            return true;
        }
        if (Boolean.TRUE.equals(toBoolean(groupConfig.get("readOnly")))) {
            return false;
        }
        Object rolesObj = groupConfig.get("roles");
        if (rolesObj instanceof List<?> allowedRoles) {
            if (allowedRoles.isEmpty()) {
                return true;
            }
            if (userId == null) {
                return true; // Fallback to true if no user context to avoid breaking unit tests
            }
            List<SysRole> userRoles = selectUserRoles(userId);
            for (Object allowedRoleObj : allowedRoles) {
                String allowedRole = stringValue(allowedRoleObj);
                if (isBlank(allowedRole)) continue;
                boolean hasRole = userRoles.stream().anyMatch(role ->
                        allowedRole.equalsIgnoreCase(role.getRoleCode()) ||
                        allowedRole.equalsIgnoreCase(role.getRoleName())
                );
                if (hasRole) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private void validateRequiredFormFields(Map<String, Object> formData, CaseTask task, WorkflowActionRequest request) {
        if (!shouldValidateRequiredFormFields(request.actionCode())) {
            return;
        }
        CaseWfInstance wfInstance = requireRunningInstance(task.getCaseId());
        Long activeWfId = resolveActiveDefinitionWfId(wfInstance, task);
        WfDefinition definition = wfDefinitionMapper.selectById(activeWfId);
        if (definition == null || isBlank(definition.getFormCode())) {
            return;
        }
        FormVersion formVersion = formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersion>()
                .eq(FormVersion::getFormCode, definition.getFormCode())
                .eq(FormVersion::getStatus, PUBLISHED_STATUS)
                .eq(FormVersion::getDeleted, 0)
                .orderByDesc(FormVersion::getVersionNo)
                .orderByDesc(FormVersion::getId)
                .last("limit 1"));
        if (formVersion == null || isBlank(formVersion.getFieldSchemaJson())) {
            return;
        }
        List<Map<String, Object>> fields = parseFieldSchema(formVersion.getFieldSchemaJson());
        WfNodeDef nodeDef = findNodeDef(activeWfId, task.getNodeCode());
        Map<String, Object> formRule = null;
        if (nodeDef != null && !isBlank(nodeDef.getFormRuleJson())) {
            try {
                formRule = objectMapper.readValue(nodeDef.getFormRuleJson(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                // ignore
            }
        }
        Map<String, Object> finalFormRule = formRule;

        Map<String, Object> permissionSchema = null;
        if (formVersion.getPermissionSchemaJson() != null && !formVersion.getPermissionSchemaJson().isBlank()) {
            try {
                permissionSchema = objectMapper.readValue(formVersion.getPermissionSchemaJson(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                // ignore
            }
        }
        Map<String, Object> finalPermissionSchema = permissionSchema;

        Long currentUserId = null;
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CurrentUserInfo userInfo) {
                currentUserId = userInfo.id();
            }
        } catch (Exception e) {
            // ignore
        }
        Long finalUserId = currentUserId;

        List<String> missingFields = fields.stream()
                .filter(field -> {
                    String fieldName = stringValue(field.get("field"));
                    // 如果 委托审查是否受理 为 否 (entrustAccepted == false)，则后续的初步勘验、材料接收、指定人等字段在后端也不作必填校验
                    if (Boolean.FALSE.equals(toBoolean(formData.get("entrustAccepted"))) && 
                            ("preliminarySurveyRequired".equals(fieldName) || 
                             "materialReceiveRequired".equals(fieldName) || 
                             "projectLeaderId".equals(fieldName) || 
                             "projectAssistantId".equals(fieldName) || 
                             "departmentHeadId".equals(fieldName))) {
                        return false;
                    }
                    boolean isRequired = Boolean.TRUE.equals(toBoolean(field.get("required")));
                    if (finalFormRule != null && finalFormRule.containsKey("fieldAuth")) {
                        Map<String, Map<String, Object>> fieldAuth = (Map<String, Map<String, Object>>) finalFormRule.get("fieldAuth");
                        if (fieldAuth != null && fieldAuth.containsKey(fieldName)) {
                            Map<String, Object> auth = fieldAuth.get(fieldName);
                            if (Boolean.TRUE.equals(auth.get("hidden"))) return false;
                            if (auth.containsKey("required")) {
                                isRequired = Boolean.TRUE.equals(auth.get("required"));
                            }
                        }
                    }
                    return isRequired;
                })
                .filter(field -> {
                    String fieldName = stringValue(field.get("field"));
                    boolean isReadOnly = Boolean.TRUE.equals(toBoolean(field.get("readOnly")))
                            || Boolean.TRUE.equals(toBoolean(field.get("readonly")));
                    if (finalFormRule != null && finalFormRule.containsKey("fieldAuth")) {
                        Map<String, Map<String, Object>> fieldAuth = (Map<String, Map<String, Object>>) finalFormRule.get("fieldAuth");
                        if (fieldAuth != null && fieldAuth.containsKey(fieldName)) {
                            Map<String, Object> auth = fieldAuth.get(fieldName);
                            if (auth.containsKey("readonly")) {
                                isReadOnly = isReadOnly || Boolean.TRUE.equals(toBoolean(auth.get("readonly")));
                            }
                            if (auth.containsKey("readOnly")) {
                                isReadOnly = isReadOnly || Boolean.TRUE.equals(toBoolean(auth.get("readOnly")));
                            }
                        }
                    }
                    return !isReadOnly;
                })
                .filter(field -> isFieldWritableForUser(field, finalPermissionSchema, finalUserId))
                .filter(field -> isMissingFormValue(formData, stringValue(field.get("field"))))
                .map(field -> {
                    String label = stringValue(field.get("label"));
                    return isBlank(label) ? stringValue(field.get("field")) : label;
                })
                .filter(label -> !isBlank(label))
                .toList();
        if (!missingFields.isEmpty()) {
            throw new BusinessException("必填字段未填写：" + String.join("、", missingFields));
        }
    }

    private boolean shouldValidateRequiredFormFields(ActionCode actionCode) {
        return actionCode != ActionCode.CLAIM
                && actionCode != ActionCode.ASSIGN
                && actionCode != ActionCode.WITHDRAW
                && actionCode != ActionCode.RETURN
                && actionCode != ActionCode.TERMINATE
                && actionCode != ActionCode.REOPEN;
    }

    private List<Map<String, Object>> parseFieldSchema(String fieldSchemaJson) {
        try {
            return objectMapper.readValue(fieldSchemaJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException("表单字段配置不是合法 JSON");
        }
    }

    private boolean isMissingFormValue(Map<String, Object> formData, String field) {
        if (isBlank(field)) {
            return false;
        }
        if (formData == null || !formData.containsKey(field)) {
            return true;
        }
        Object value = formData.get(field);
        if (value instanceof Boolean) {
            return false;
        }
        return value == null || isBlank(String.valueOf(value));
    }

    private WorkflowActionResult handleClaim(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, Long currentUserId, String currentUserName, LocalDateTime now) {
        validateClaimEligibility(task, currentUserId);
        updateActiveTaskAssignee(task, currentUserId, currentUserName, TASK_CLAIMED, now);
        updateNodeHandler(task.getNodeInstanceId(), task.getAssigneeId(), task.getAssigneeName());
        syncCaseCurrentHandler(caseInfo, task.getAssigneeId(), task.getAssigneeName());
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "认领成功");
    }

    private WorkflowActionResult handleAssign(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        updateActiveTaskAssignee(task, request.resolvedNextAssigneeId(), request.resolvedNextAssigneeName(), TASK_PENDING, now);
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

        if (isUserInCandidateScope(candidates, assigneeId)) {
            return;
        }

        throw new BusinessException("当前用户不在任务候选范围内");
    }

    private boolean isUserInCandidateScope(List<CaseTaskCandidate> candidates, Long userId) {
        if (userId == null) {
            return false;
        }
        boolean matchedByUser = candidates.stream()
                .anyMatch(candidate -> Objects.equals(candidate.getCandidateUserId(), userId));
        if (matchedByUser) {
            return true;
        }
        Set<Long> candidateRoleIds = candidates.stream()
                .map(CaseTaskCandidate::getCandidateRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (candidateRoleIds.isEmpty()) {
            return false;
        }
        return selectUserRoleIds(userId).stream().anyMatch(candidateRoleIds::contains);
    }

    private void validateManualAssigneeMatchesNodeRole(WfNodeDef nodeDef, Long assigneeId) {
        List<SysRole> roles = resolveHandlerRoles(nodeDef.getHandlerRoleRule());
        Set<Long> nodeRoleIds = roles.stream()
                .map(SysRole::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (nodeRoleIds.isEmpty()) {
            throw new BusinessException("流程节点承办角色规则未匹配到启用角色");
        }
        boolean matched = selectUserRoleIds(assigneeId).stream().anyMatch(nodeRoleIds::contains);
        if (!matched) {
            throw new BusinessException("指定承办人不在目标节点候选角色范围内");
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
                null,
                previousCompletedNode.getNodeCode(),
                previousCompletedNode.getNodeName(),
                now);
        recreatedNode.setHandlerId(previousAssigneeId);
        recreatedNode.setHandlerName(defaultName(previousAssigneeName));
        caseNodeInstanceMapper.updateById(recreatedNode);

        CaseTask recreatedTask = createTask(
                caseInfo,
                wfInstance.getId(),
                null,
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

    private void updateActiveTaskAssignee(CaseTask task, Long assigneeId, String assigneeName, String taskStatus, LocalDateTime now) {
        task.setStatus(taskStatus);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(defaultName(assigneeName));
        task.setStartedTime(task.getStartedTime() == null ? now : task.getStartedTime());
        if (TASK_CLAIMED.equals(taskStatus)) {
            task.setClaimedBy(assigneeId);
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

    private void completeCurrentTaskAndNode(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, Long operatorId, String operatorName, LocalDateTime now) {
        task.setStatus(TASK_COMPLETED);
        task.setAssigneeId(operatorId);
        task.setAssigneeName(defaultName(operatorName));
        task.setStartedTime(task.getStartedTime() == null ? now : task.getStartedTime());
        task.setCompletedTime(now);
        task.setResultAction(request.actionCode().name());
        task.setResultOpinion(resolveOutcomeOpinion(request));
        caseTaskMapper.updateById(task);

        CaseNodeInstance nodeInstance = caseNodeInstanceMapper.selectById(task.getNodeInstanceId());
        if (nodeInstance != null) {
            nodeInstance.setStatus(NODE_COMPLETED);
            nodeInstance.setCompletedTime(now);
            nodeInstance.setHandlerId(operatorId);
            nodeInstance.setHandlerName(defaultName(operatorName));
            nodeInstance.setResultAction(request.actionCode().name());
            nodeInstance.setResultOpinion(resolveOutcomeOpinion(request));
            nodeInstance.setFormData(request.formData());
            caseNodeInstanceMapper.updateById(nodeInstance);
            archiveCompletedNode(task, nodeInstance, request);
        }

        if (caseInfo != null) {
            updateCaseFormData(caseInfo, request.formData());
            caseInfoMapper.updateById(caseInfo);
        }
    }

    private void archiveCompletedNode(CaseTask task, CaseNodeInstance nodeInstance, WorkflowActionRequest request) {
        String summary = request.actionCode().name() + ": " + resolveOutcomeOpinion(request);
        knowledgeService.archiveNode(new ArchiveNodeRequest(
                task.getCaseId(),
                task.getWfInstanceId(),
                task.getNodeCode(),
                task.getNodeName(),
                task.getId(),
                null,
                archiveArtifactCode(task, request),
                request.formData(),
                request.fileIds(),
                summary
        ));
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
        WorkflowActionResult dynamicResult = tryAdvanceByDefinition(caseInfo, task, request, now);
        if (dynamicResult != null) {
            return dynamicResult;
        }
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
            cancelOtherActiveTasksForCase(caseInfo.getId(), task.getId(), now);
            return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "案件已办结");
        }
        return new WorkflowActionResult(caseInfo.getId(), task.getId(), request.actionCode().name(), true, "办理成功");
    }

    private WorkflowActionResult handleReturn(CaseInfo caseInfo, CaseTask task, WorkflowActionRequest request, LocalDateTime now) {
        WorkflowActionResult dynamicResult = tryAdvanceByDefinition(caseInfo, task, request, now);
        if (dynamicResult != null) {
            return dynamicResult;
        }
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
        cancelOtherActiveTasksForCase(caseInfo.getId(), task.getId(), now);
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
        CaseNodeInstance nextNode = createNodeInstance(caseInfo.getId(), wfInstance.getId(), null, nextNodeCode, nextNodeName, now);
        CaseTask nextTask = createNextNodeTask(
                caseInfo,
                wfInstance,
                wfInstance.getWfId(),
                null,
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

    private WorkflowActionResult tryAdvanceByDefinition(CaseInfo caseInfo, CaseTask completedTask, WorkflowActionRequest request, LocalDateTime now) {
        CaseWfInstance wfInstance = requireRunningInstance(caseInfo.getId());
        Long activeWfId = resolveActiveDefinitionWfId(wfInstance, completedTask);
        List<WfTransitionDef> transitions = wfTransitionDefMapper.selectList(new LambdaQueryWrapper<WfTransitionDef>()
                .eq(WfTransitionDef::getWfId, activeWfId)
                .eq(WfTransitionDef::getFromNodeCode, completedTask.getNodeCode())
                .eq(WfTransitionDef::getActionCode, request.actionCode().name())
                .eq(WfTransitionDef::getEnabled, 1)
                .orderByAsc(WfTransitionDef::getSortNo)
                .orderByAsc(WfTransitionDef::getId));
        if (transitions == null || transitions.isEmpty()) {
            return null;
        }
        List<WfTransitionDef> matchedTransitions = transitions.stream()
                .filter(transition -> matchesCondition(transition.getConditionExpression(), request))
                .toList();
        if (matchedTransitions.isEmpty()) {
            throw new BusinessException("没有命中的流程流转条件");
        }

        List<TransitionAdvance> advances = matchedTransitions.stream()
                .map(transition -> createTransitionTarget(caseInfo, wfInstance, completedTask, transition, request, now, activeWfId))
                .toList();
        List<CaseTask> createdTasks = advances.stream()
                .flatMap(advance -> advance.tasks().stream())
                .toList();
        boolean finished = advances.stream().anyMatch(TransitionAdvance::finished);
        boolean handled = advances.stream().anyMatch(TransitionAdvance::handled);
        if (!finished && !handled && createdTasks.isEmpty()) {
            return null;
        }
        if (finished) {
            return new WorkflowActionResult(caseInfo.getId(), completedTask.getId(), request.actionCode().name(), true, "流程已完成");
        }
        if (createdTasks.isEmpty()) {
            return new WorkflowActionResult(caseInfo.getId(), completedTask.getId(), request.actionCode().name(), true, "按流程定义完成办理");
        }

        CaseTask primaryTask = createdTasks.get(0);
        syncCaseAndWorkflowToTask(caseInfo, wfInstance, primaryTask);
        return new WorkflowActionResult(caseInfo.getId(), completedTask.getId(), request.actionCode().name(), true, "按流程定义完成办理");
    }

    private boolean matchesCondition(String conditionExpression, WorkflowActionRequest request) {
        if (isBlank(conditionExpression)) {
            return true;
        }
        String expression = conditionExpression.trim();
        if ("true".equalsIgnoreCase(expression) || "always".equalsIgnoreCase(expression)) {
            return true;
        }
        if ("false".equalsIgnoreCase(expression) || "never".equalsIgnoreCase(expression)) {
            return false;
        }

        String operator = expression.contains("!=") ? "!=" : expression.contains("==") ? "==" : null;
        if (operator == null) {
            throw new BusinessException("暂不支持的流程条件表达式：" + conditionExpression);
        }
        String[] parts = expression.split(java.util.regex.Pattern.quote(operator), 2);
        if (parts.length != 2) {
            throw new BusinessException("流程条件表达式格式错误：" + conditionExpression);
        }
        Object actual = resolveConditionValue(parts[0].trim(), request);
        Object expected = parseConditionLiteral(parts[1].trim());
        boolean equals = conditionValueEquals(actual, expected);
        return "==".equals(operator) == equals;
    }

    private Object resolveConditionValue(String fieldExpression, WorkflowActionRequest request) {
        if (request.formData() == null || request.formData().isEmpty()) {
            return null;
        }
        String field = fieldExpression;
        if (field.startsWith("form.")) {
            field = field.substring("form.".length());
        }
        return request.formData().get(field);
    }

    private Object parseConditionLiteral(String literal) {
        String value = literal.trim();
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        if ("true".equalsIgnoreCase(value) || "是".equals(value) || "yes".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value) || "否".equals(value) || "no".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private boolean conditionValueEquals(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (expected instanceof Boolean expectedBoolean) {
            return expectedBoolean.equals(toBoolean(actual));
        }
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue()) == 0;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "是".equals(text) || "yes".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "否".equals(text) || "no".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private TransitionAdvance createTransitionTarget(
            CaseInfo caseInfo,
            CaseWfInstance wfInstance,
            CaseTask completedTask,
            WfTransitionDef transition,
            WorkflowActionRequest request,
            LocalDateTime now,
            Long activeWfId) {
        Map<String, Object> transitionConfig = parseTransitionConfig(transition.getTransitionConfigJson());
        if (Boolean.TRUE.equals(toBoolean(transitionConfig.get("launchSubflow")))) {
            return createLaunchedSubflowTarget(caseInfo, wfInstance, completedTask, transition, request, now, transitionConfig, activeWfId);
        }

        WfNodeDef targetNode = findNodeDef(activeWfId, transition.getToNodeCode());
        if (targetNode == null) {
            throw new BusinessException("流程定义缺少目标节点：" + transition.getToNodeCode());
        }
        Long subflowInstanceId = completedTask.getSubflowInstanceId();
        CaseNodeInstance nodeInstance = createNodeInstance(caseInfo.getId(), wfInstance.getId(), subflowInstanceId,
                targetNode.getNodeCode(), targetNode.getNodeName(), now);
        if (NODE_END.equalsIgnoreCase(targetNode.getNodeType())) {
            return handleEndTransition(caseInfo, wfInstance, completedTask, now);
        }
        
        if ("gateway".equalsIgnoreCase(targetNode.getNodeType())) {
            nodeInstance.setStatus(NODE_COMPLETED);
            nodeInstance.setCompletedTime(now);
            caseNodeInstanceMapper.updateById(nodeInstance);
            return handleGatewayTransition(caseInfo, wfInstance, completedTask, targetNode, request, now, activeWfId);
        }

        CaseTask nextTask = createNextNodeTask(
                caseInfo,
                wfInstance,
                activeWfId,
                subflowInstanceId,
                nodeInstance.getId(),
                targetNode.getNodeCode(),
                targetNode.getNodeName(),
                request,
                completedTask.getAssigneeId(),
                completedTask.getAssigneeName(),
                now);
        return new TransitionAdvance(List.of(nextTask), false, true);
    }

    private TransitionAdvance handleGatewayTransition(CaseInfo caseInfo, CaseWfInstance wfInstance, CaseTask completedTask, WfNodeDef gatewayNode, WorkflowActionRequest request, LocalDateTime now, Long activeWfId) {
        if ("inclusive".equalsIgnoreCase(gatewayNode.getTaskType())) {
            // Count active tasks for this wfInstance to see if there are other incoming branches still running
            long activeTaskCount = caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
                    .eq(CaseTask::getWfInstanceId, wfInstance.getId())
                    .eq(completedTask.getSubflowInstanceId() != null, CaseTask::getSubflowInstanceId, completedTask.getSubflowInstanceId())
                    .isNull(completedTask.getSubflowInstanceId() == null, CaseTask::getSubflowInstanceId)
                    .in(CaseTask::getStatus, TASK_PENDING, TASK_CLAIMED, "processing", TASK_SUBFLOW_RUNNING));
            long runningSubflowCount = countRunningSubflowsInScope(
                    caseInfo.getId(),
                    wfInstance.getId(),
                    completedTask.getSubflowInstanceId());
            if (activeTaskCount > 0 || runningSubflowCount > 0) {
                // There are other active tasks running in parallel. Stop advancing this path.
                return new TransitionAdvance(List.of(), false, true);
            }
        }

        List<WfTransitionDef> transitions = wfTransitionDefMapper.selectList(new LambdaQueryWrapper<WfTransitionDef>()
                .eq(WfTransitionDef::getWfId, activeWfId)
                .eq(WfTransitionDef::getFromNodeCode, gatewayNode.getNodeCode())
                .eq(WfTransitionDef::getActionCode, "APPROVE")
                .eq(WfTransitionDef::getEnabled, 1)
                .orderByAsc(WfTransitionDef::getSortNo)
                .orderByAsc(WfTransitionDef::getId));

        List<WfTransitionDef> matchedTransitions = transitions.stream()
                .filter(transition -> matchesCondition(transition.getConditionExpression(), request))
                .toList();

        if (matchedTransitions.isEmpty()) {
            throw new BusinessException("网关没有命中任何分支流转条件：" + gatewayNode.getNodeCode());
        }

        List<TransitionAdvance> advances = matchedTransitions.stream()
                .map(transition -> createTransitionTarget(caseInfo, wfInstance, completedTask, transition, request, now, activeWfId))
                .toList();

        List<CaseTask> createdTasks = advances.stream()
                .flatMap(advance -> advance.tasks().stream())
                .toList();
        boolean finished = advances.stream().anyMatch(TransitionAdvance::finished);

        return new TransitionAdvance(createdTasks, finished, true);
    }

    private long countRunningSubflowsInScope(Long caseId, Long wfInstanceId, Long subflowInstanceId) {
        List<CaseSubflowInstance> runningSubflows = caseSubflowInstanceMapper.selectList(new LambdaQueryWrapper<CaseSubflowInstance>()
                .eq(CaseSubflowInstance::getCaseId, caseId)
                .eq(CaseSubflowInstance::getParentWfInstanceId, wfInstanceId)
                .eq(CaseSubflowInstance::getStatus, WORKFLOW_RUNNING));
        return runningSubflows.stream()
                .map(CaseSubflowInstance::getParentTaskId)
                .filter(Objects::nonNull)
                .map(caseTaskMapper::selectById)
                .filter(Objects::nonNull)
                .filter(task -> Objects.equals(task.getWfInstanceId(), wfInstanceId))
                .filter(task -> Objects.equals(task.getSubflowInstanceId(), subflowInstanceId))
                .filter(task -> TASK_SUBFLOW_RUNNING.equals(task.getStatus()))
                .count();
    }

    private TransitionAdvance createLaunchedSubflowTarget(
            CaseInfo caseInfo,
            CaseWfInstance wfInstance,
            CaseTask completedTask,
            WfTransitionDef transition,
            WorkflowActionRequest request,
            LocalDateTime now,
            Map<String, Object> transitionConfig,
            Long activeWfId) {
        
        WfNodeDef targetNode = findNodeDef(activeWfId, transition.getToNodeCode());
        if (targetNode == null) {
            throw new BusinessException("流程定义缺少子流程挂载节点：" + transition.getToNodeCode());
        }

        CaseNodeInstance parentNodeInstance = createNodeInstance(caseInfo.getId(), wfInstance.getId(), completedTask.getSubflowInstanceId(),
                targetNode.getNodeCode(), targetNode.getNodeName(), now);
        
        CaseTask parentTask = createNextNodeTask(
                caseInfo,
                wfInstance,
                activeWfId,
                completedTask.getSubflowInstanceId(),
                parentNodeInstance.getId(),
                targetNode.getNodeCode(),
                targetNode.getNodeName(),
                request,
                completedTask.getAssigneeId(),
                completedTask.getAssigneeName(),
                now);
        parentTask.setStatus(TASK_SUBFLOW_RUNNING);
        caseTaskMapper.updateById(parentTask);

        CaseSubflowInstance subflowInstance = maybeCreateSubflowInstance(caseInfo, wfInstance, parentTask, transition, request, now, transitionConfig);
        
        WfNodeDef firstNode = findFirstActionableNode(subflowInstance.getWfId());
        if (firstNode == null) {
            throw new BusinessException("子流程缺少可办理节点：" + subflowInstance.getWfCode());
        }
        CaseNodeInstance nodeInstance = createNodeInstance(caseInfo.getId(), wfInstance.getId(), subflowInstance.getId(),
                firstNode.getNodeCode(), firstNode.getNodeName(), now);
        CaseTask nextTask = createNextNodeTask(
                caseInfo,
                wfInstance,
                subflowInstance.getWfId(),
                subflowInstance.getId(),
                nodeInstance.getId(),
                firstNode.getNodeCode(),
                firstNode.getNodeName(),
                request,
                completedTask.getAssigneeId(),
                completedTask.getAssigneeName(),
                now);
        return new TransitionAdvance(List.of(nextTask), false, true);
    }

    private TransitionAdvance handleEndTransition(CaseInfo caseInfo, CaseWfInstance wfInstance, CaseTask completedTask, LocalDateTime now) {
        if (completedTask.getSubflowInstanceId() != null) {
            finishSubflowInstance(completedTask.getSubflowInstanceId(), now);
            CaseSubflowInstance subflowInstance = caseSubflowInstanceMapper.selectById(completedTask.getSubflowInstanceId());
            if (subflowInstance != null && "archive".equalsIgnoreCase(subflowInstance.getWfCode())) {
                cancelOtherActiveTasksForCase(caseInfo.getId(), completedTask.getId(), now);
                caseInfo.setCaseStatus(CaseStatus.COMPLETED.name());
                caseInfoMapper.updateById(caseInfo);
            }
            if (subflowInstance != null && subflowInstance.getParentTaskId() != null) {
                CaseTask parentTask = caseTaskMapper.selectById(subflowInstance.getParentTaskId());
                if (parentTask != null && TASK_SUBFLOW_RUNNING.equals(parentTask.getStatus())) {
                    WorkflowActionRequest completeRequest = new WorkflowActionRequest(
                            parentTask.getId(), ActionCode.COMPLETE, "子流程已结束", null, null, null, null, null);
                    completeCurrentTaskAndNode(
                            caseInfo,
                            parentTask,
                            completeRequest,
                            parentTask.getAssigneeId(),
                            parentTask.getAssigneeName(),
                            now);
                    WorkflowActionResult advanceResult = tryAdvanceByDefinition(caseInfo, parentTask, completeRequest, now);
                    if (advanceResult != null && "流程已完成".equals(advanceResult.message())) {
                        return new TransitionAdvance(List.of(), true, true);
                    }
                }
            }
        }
        CaseTask nextActiveTask = findLatestActiveTask(caseInfo.getId(), wfInstance.getId());
        if (nextActiveTask != null) {
            return new TransitionAdvance(List.of(nextActiveTask), false, true);
        }
        
        // If this instance is root-level or the main flow code
        if (completedTask.getSubflowInstanceId() == null || MAIN_WF_CODE.equalsIgnoreCase(wfInstance.getWfCode())) {
            // Check for ANY remaining active tasks for this case
            long activeCount = caseTaskMapper.selectCount(new LambdaQueryWrapper<CaseTask>()
                    .eq(CaseTask::getCaseId, caseInfo.getId())
                    .in(CaseTask::getStatus, TASK_PENDING, TASK_CLAIMED, "processing", TASK_SUBFLOW_RUNNING));
            
            if (activeCount == 0) {
                caseInfo.setCaseStatus(CaseStatus.COMPLETED.name());
                caseInfo.setCurrentNodeCode(null);
                caseInfo.setCurrentNodeName(null);
                caseInfo.setCurrentHandlerId(null);
                caseInfo.setCurrentHandlerName(null);
                caseInfo.setCompletedTime(now);
                caseInfoMapper.updateById(caseInfo);
                finishWorkflowInstance(wfInstance, now);
                return new TransitionAdvance(List.of(), true, true);
            }
        }

        caseInfo.setCurrentNodeCode(null);
        caseInfo.setCurrentNodeName(null);
        caseInfo.setCurrentHandlerId(null);
        caseInfo.setCurrentHandlerName(null);
        caseInfoMapper.updateById(caseInfo);
        finishWorkflowInstance(wfInstance, now);
        return new TransitionAdvance(List.of(), false, true);
    }

    private record TransitionAdvance(List<CaseTask> tasks, boolean finished, boolean handled) {
        private TransitionAdvance {
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }
    }

    private CaseSubflowInstance maybeCreateSubflowInstance(
            CaseInfo caseInfo,
            CaseWfInstance parentWfInstance,
            CaseTask parentTask,
            WfTransitionDef transition,
            WorkflowActionRequest request,
            LocalDateTime now,
            Map<String, Object> config) {
        if (!Boolean.TRUE.equals(toBoolean(config.get("launchSubflow")))) {
            return null;
        }

        String subflowCode = stringValue(config.get("subflowCode"));
        if (isBlank(subflowCode)) {
            throw new BusinessException("子流程配置缺少 subflowCode：" + transition.getFromNodeCode() + " -> " + transition.getToNodeCode());
        }
        WfDefinition subflowDefinition = latestPublishedDefinition(subflowCode);
        if (subflowDefinition == null) {
            throw new BusinessException("未找到已发布的子流程定义：" + subflowCode);
        }

        CaseSubflowInstance existing = caseSubflowInstanceMapper.selectOne(new LambdaQueryWrapper<CaseSubflowInstance>()
                .eq(CaseSubflowInstance::getCaseId, caseInfo.getId())
                .eq(CaseSubflowInstance::getParentWfInstanceId, parentWfInstance.getId())
                .eq(CaseSubflowInstance::getParentTaskId, parentTask.getId())
                .eq(CaseSubflowInstance::getParentNodeCode, parentTask.getNodeCode())
                .eq(CaseSubflowInstance::getSubflowType, subflowCode)
                .eq(CaseSubflowInstance::getStatus, WORKFLOW_RUNNING)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }

        CaseSubflowInstance subflowInstance = new CaseSubflowInstance();
        subflowInstance.setCaseId(caseInfo.getId());
        subflowInstance.setParentWfInstanceId(parentWfInstance.getId());
        subflowInstance.setParentTaskId(parentTask.getId());
        subflowInstance.setParentNodeCode(parentTask.getNodeCode());
        subflowInstance.setWfId(subflowDefinition.getId());
        subflowInstance.setWfCode(subflowDefinition.getWfCode());
        subflowInstance.setWfName(subflowDefinition.getWfName());
        subflowInstance.setSubflowType(subflowCode);
        subflowInstance.setStatus(WORKFLOW_RUNNING);
        subflowInstance.setStartedBy(parentTask.getAssigneeId());
        subflowInstance.setStartedTime(now);
        subflowInstance.setReason(resolveSubflowReason(config, transition, request));
        // Clean up case form data for non-read-only fields defined in the subflow form schema.
        // This ensures old values from other subflows/tasks do not bleed into this subflow.
        if (subflowDefinition.getFormCode() != null && !subflowDefinition.getFormCode().trim().isEmpty()) {
            FormVersion formVersion = formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersion>()
                    .eq(FormVersion::getFormCode, subflowDefinition.getFormCode())
                    .eq(FormVersion::getStatus, PUBLISHED_STATUS)
                    .eq(FormVersion::getDeleted, 0)
                    .orderByDesc(FormVersion::getVersionNo)
                    .orderByDesc(FormVersion::getId)
                    .last("limit 1"));
            if (formVersion != null && formVersion.getFieldSchemaJson() != null && !formVersion.getFieldSchemaJson().trim().isEmpty()) {
                List<Map<String, Object>> fields = parseFieldSchema(formVersion.getFieldSchemaJson());
                Map<String, Object> caseFormData = caseInfo.getFormData();
                if (caseFormData == null) {
                    caseFormData = new LinkedHashMap<>();
                } else {
                    caseFormData = new LinkedHashMap<>(caseFormData);
                }
                boolean modified = false;
                for (Map<String, Object> field : fields) {
                    String fieldName = stringValue(field.get("field"));
                    boolean isReadOnly = Boolean.TRUE.equals(toBoolean(field.get("readOnly")))
                            || Boolean.TRUE.equals(toBoolean(field.get("readonly")));
                    if (!isReadOnly && fieldName != null && !fieldName.trim().isEmpty()) {
                        if ("nextRecommendation".equals(fieldName)
                                || "projectReviewPassed".equals(fieldName)
                                || "technicalReviewPassed".equals(fieldName)
                                || "departmentReviewPassed".equals(fieldName)
                                || "projectMaterialReviewPassed".equals(fieldName)) {
                            continue;
                        }
                        String type = stringValue(field.get("type"));
                        if ("boolean".equalsIgnoreCase(type)) {
                            caseFormData.put(fieldName, false);
                        } else {
                            caseFormData.remove(fieldName);
                        }
                        modified = true;
                    }
                }
                if (modified) {
                    caseInfo.setFormData(caseFormData);
                    caseInfoMapper.updateById(caseInfo);
                }
            }
        }

        caseSubflowInstanceMapper.insert(subflowInstance);
        return subflowInstance;
    }

    private Map<String, Object> parseTransitionConfig(String transitionConfigJson) {
        if (isBlank(transitionConfigJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(transitionConfigJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new BusinessException("流程流转配置不是合法 JSON");
        }
    }

    private String resolveSubflowReason(Map<String, Object> config, WfTransitionDef transition, WorkflowActionRequest request) {
        String configuredReason = stringValue(config.get("reason"));
        if (!isBlank(configuredReason)) {
            return configuredReason;
        }
        String outcome = resolveOutcomeOpinion(request);
        return transition.getActionName() + (isBlank(outcome) ? "" : "：" + outcome);
    }

    private CaseInfo requireCase(Long caseId) {
        CaseInfo caseInfo = caseInfoMapper.selectRawById(caseId);
        if (caseInfo == null) {
            throw new BusinessException("案件不存在");
        }
        return caseInfo;
    }

    private Long resolveActiveDefinitionWfId(CaseWfInstance wfInstance, CaseTask task) {
        if (task.getSubflowInstanceId() == null) {
            return wfInstance.getWfId();
        }
        CaseSubflowInstance subflowInstance = requireSubflowInstance(task.getSubflowInstanceId(), task.getCaseId());
        return subflowInstance.getWfId();
    }

    private CaseTask requireTask(Long caseId, Long taskId) {
        CaseTask task = caseTaskMapper.selectById(taskId);
        if (task == null || !caseId.equals(task.getCaseId())) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private CaseSubflowInstance requireSubflowInstance(Long subflowInstanceId, Long caseId) {
        CaseSubflowInstance subflowInstance = caseSubflowInstanceMapper.selectById(subflowInstanceId);
        if (subflowInstance == null || (caseId != null && !caseId.equals(subflowInstance.getCaseId()))) {
            throw new BusinessException("子流程实例不存在");
        }
        return subflowInstance;
    }

    private void validateTaskCanProcess(CaseTask task) {
        if (TASK_COMPLETED.equals(task.getStatus())) {
            throw new BusinessException("任务已办理");
        }
        if (TASK_CANCELLED.equals(task.getStatus())) {
            throw new BusinessException("任务已取消");
        }
        if ("returned".equals(task.getStatus())) {
            throw new BusinessException("任务已退回");
        }
        if ("terminated".equals(task.getStatus())) {
            throw new BusinessException("任务已终止");
        }
    }

    private void validateTaskOperator(CaseTask task, Long operatorId) {
        requireCurrentUser(operatorId);
        if (task.getAssigneeId() != null && !task.getAssigneeId().equals(operatorId)) {
            throw new BusinessException("当前用户不是任务主办人，无法办理");
        }
        List<CaseTaskCandidate> candidates = selectCandidateRows(task.getId());
        if (task.getAssigneeId() == null) {
            if (candidates.isEmpty()) {
                throw new BusinessException("任务没有主办人或候选范围，无法办理");
            }
            if (!isUserInCandidateScope(candidates, operatorId)) {
                throw new BusinessException("当前用户不在任务候选范围内，无法办理");
            }
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
        WfDefinition workflowDefinition = latestPublishedDefinition(MAIN_WF_CODE);
        CaseWfInstance instance = new CaseWfInstance();
        instance.setCaseId(caseInfo.getId());
        instance.setWfId(workflowDefinition == null ? 1L : workflowDefinition.getId());
        instance.setWfCode(workflowDefinition == null ? MAIN_WF_CODE : workflowDefinition.getWfCode());
        instance.setWfName(workflowDefinition == null ? MAIN_WF_NAME : workflowDefinition.getWfName());
        instance.setStatus(WORKFLOW_RUNNING);
        instance.setStartedBy(operatorId);
        instance.setStartedTime(now);
        caseWfInstanceMapper.insert(instance);
        return instance;
    }

    private WfDefinition latestPublishedDefinition(String wfCode) {
        WfDefinition published = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getPublishStatus, PUBLISHED_STATUS)
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("limit 1"));
        if (published != null) {
            return published;
        }
        return wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("limit 1"));
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

    private CaseTask findLatestActiveTask(Long caseId, Long wfInstanceId) {
        return caseTaskMapper.selectOne(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .eq(CaseTask::getWfInstanceId, wfInstanceId)
                .in(CaseTask::getStatus, TASK_PENDING, TASK_CLAIMED, "processing", TASK_SUBFLOW_RUNNING)
                .orderByDesc(CaseTask::getSubflowInstanceId)
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
        return CaseStatus.PROCESSING;
    }

    private void syncCaseAndWorkflowToTask(CaseInfo caseInfo, CaseWfInstance wfInstance, CaseTask task) {
        caseInfo.setCaseStatus(resolveCaseStatusForNode(task.getNodeCode()).name());
        caseInfo.setCurrentNodeCode(task.getNodeCode());
        caseInfo.setCurrentNodeName(task.getNodeName());
        caseInfo.setCurrentHandlerId(task.getAssigneeId());
        caseInfo.setCurrentHandlerName(task.getAssigneeName());
        caseInfoMapper.updateById(caseInfo);

        wfInstance.setCurrentNodeCode(task.getNodeCode());
        wfInstance.setCurrentNodeName(task.getNodeName());
        caseWfInstanceMapper.updateById(wfInstance);
    }

    private CaseNodeInstance createNodeInstance(Long caseId, Long wfInstanceId, Long subflowInstanceId,
                                                String nodeCode, String nodeName, LocalDateTime now) {
        CaseNodeInstance nodeInstance = new CaseNodeInstance();
        nodeInstance.setCaseId(caseId);
        nodeInstance.setWfInstanceId(wfInstanceId);
        nodeInstance.setSubflowInstanceId(subflowInstanceId);
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
            Long activeWfId,
            Long subflowInstanceId,
            Long nodeInstanceId,
            String nodeCode,
            String nodeName,
            WorkflowActionRequest request,
            Long inheritedAssigneeId,
            String inheritedAssigneeName,
            LocalDateTime now) {
        WfNodeDef nodeDef = findNodeDef(activeWfId, nodeCode);
        if (nodeDef != null && !isBlank(nodeDef.getHandlerRoleRule())) {
            Long requestedNextAssigneeId = request.resolvedNextAssigneeId();
            if (requestedNextAssigneeId != null && Boolean.TRUE.equals(toBoolean(nodeDef.getAllowManualAssign()))) {
                validateManualAssigneeMatchesNodeRole(nodeDef, requestedNextAssigneeId);
                return createTask(caseInfo, wfInstance.getId(), subflowInstanceId, nodeInstanceId, nodeCode, nodeName,
                        requestedNextAssigneeId, defaultName(request.resolvedNextAssigneeName()), now);
            }
            // If role rules exist, don't inherit assignee from previous task unless explicitly allowed
            return createCandidateTask(caseInfo, wfInstance.getId(), subflowInstanceId, nodeInstanceId, nodeCode, nodeName, nodeDef, now);
        }

        Long targetAssigneeId = request.resolvedNextAssigneeId();
        String targetAssigneeName = targetAssigneeId == null ? null : defaultName(request.resolvedNextAssigneeName());

        return createTask(caseInfo, wfInstance.getId(), subflowInstanceId, nodeInstanceId, nodeCode, nodeName, targetAssigneeId, targetAssigneeName, now);
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

    private WfNodeDef findFirstActionableNode(Long wfId) {
        if (wfId == null) {
            return null;
        }
        return wfNodeDefMapper.selectOne(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getWfId, wfId)
                .ne(WfNodeDef::getNodeType, "start")
                .eq(WfNodeDef::getEnabled, 1)
                .orderByAsc(WfNodeDef::getSortNo)
                .orderByAsc(WfNodeDef::getId)
                .last("limit 1"));
    }

    private CaseTask createCandidateTask(
            CaseInfo caseInfo,
            Long wfInstanceId,
            Long subflowInstanceId,
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
        task.setSubflowInstanceId(subflowInstanceId);
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
                        wrapper.in(SysRole::getId, roleIds)
                                .or()
                                .in(SysRole::getRoleCode, roleCodes)
                                .or()
                                .in(SysRole::getRoleName, roleCodes);
                    } else if (!roleIds.isEmpty()) {
                        wrapper.in(SysRole::getId, roleIds);
                    } else {
                        wrapper.in(SysRole::getRoleCode, roleCodes)
                                .or()
                                .in(SysRole::getRoleName, roleCodes);
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
            Long subflowInstanceId,
            Long nodeInstanceId,
            String nodeCode,
            String nodeName,
            Long assigneeId,
            String assigneeName,
            LocalDateTime now) {
        CaseTask task = new CaseTask();
        task.setCaseId(caseInfo.getId());
        task.setWfInstanceId(wfInstanceId);
        task.setSubflowInstanceId(subflowInstanceId);
        task.setNodeInstanceId(nodeInstanceId);
        task.setTaskType(TASK_TYPE_SINGLE);
        task.setTaskTitle(caseInfo.getCaseTitle() + " - " + nodeName);
        task.setNodeCode(nodeCode);
        task.setNodeName(nodeName);
        task.setStatus(TASK_PENDING);
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeId == null ? null : defaultName(assigneeName));
        task.setDeadlineTime(caseInfo.getDeadlineTime());
        task.setStartedTime(now);
        task.setOvertimeFlag(0);
        caseTaskMapper.insert(task);
        return task;
    }

    private void finishWorkflowInstance(Long caseId, LocalDateTime now) {
        CaseWfInstance wfInstance = requireRunningInstance(caseId);
        finishWorkflowInstance(wfInstance, now);
    }

    private void finishWorkflowInstance(CaseWfInstance wfInstance, LocalDateTime now) {
        wfInstance.setStatus(WORKFLOW_COMPLETED);
        wfInstance.setCompletedTime(now);
        wfInstance.setCurrentNodeCode(null);
        wfInstance.setCurrentNodeName(null);
        caseWfInstanceMapper.updateById(wfInstance);
    }

    private void finishSubflowInstance(Long subflowInstanceId, LocalDateTime now) {
        if (subflowInstanceId == null) {
            return;
        }
        CaseSubflowInstance subflowInstance = caseSubflowInstanceMapper.selectById(subflowInstanceId);
        if (subflowInstance == null || WORKFLOW_COMPLETED.equals(subflowInstance.getStatus())) {
            return;
        }
        subflowInstance.setStatus(WORKFLOW_COMPLETED);
        subflowInstance.setCompletedTime(now);
        caseSubflowInstanceMapper.updateById(subflowInstance);
    }

    private void terminateWorkflowInstance(Long caseId, LocalDateTime now) {
        CaseWfInstance wfInstance = requireRunningInstance(caseId);
        wfInstance.setStatus(WORKFLOW_TERMINATED);
        wfInstance.setTerminatedTime(now);
        caseWfInstanceMapper.updateById(wfInstance);
    }

    private Long resolveNextAssigneeId(WorkflowActionRequest request, CaseTask task) {
        return request.resolvedNextAssigneeId();
    }

    private String resolveNextAssigneeName(WorkflowActionRequest request, CaseTask task) {
        if (!isBlank(request.resolvedNextAssigneeName())) {
            return request.resolvedNextAssigneeName();
        }
        return null;
    }

    private String resolveOutcomeOpinion(WorkflowActionRequest request) {
        if (!isBlank(request.opinion())) {
            return request.opinion();
        }
        return request.reason();
    }

    private String archiveArtifactCode(CaseTask task, WorkflowActionRequest request) {
        String prefix = task.getSubflowInstanceId() == null ? "" : "SUBFLOW-" + task.getSubflowInstanceId() + "-";
        return prefix + task.getNodeCode() + "-" + request.actionCode().name();
    }

    private CurrentOperator currentOperatorFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        return new CurrentOperator(userInfo.id(), displayName(userInfo));
    }

    private void requireCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
    }

    private String displayName(CurrentUserInfo userInfo) {
        if (!isBlank(userInfo.realName())) {
            return userInfo.realName();
        }
        return userInfo.username();
    }

    private record CurrentOperator(Long id, String name) {
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String defaultName(String name) {
        return isBlank(name) ? "管理员" : name;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void cancelOtherActiveTasksForCase(Long caseId, Long currentTaskId, LocalDateTime now) {
        java.util.Set<Long> parentTaskIds = new java.util.HashSet<>();
        CaseTask completedTask = caseTaskMapper.selectById(currentTaskId);
        if (completedTask != null) {
            Long subId = completedTask.getSubflowInstanceId();
            while (subId != null) {
                CaseSubflowInstance sub = caseSubflowInstanceMapper.selectById(subId);
                if (sub == null) {
                    break;
                }
                if (sub.getParentTaskId() != null) {
                    parentTaskIds.add(sub.getParentTaskId());
                }
                CaseTask parentTask = caseTaskMapper.selectById(sub.getParentTaskId());
                subId = parentTask != null ? parentTask.getSubflowInstanceId() : null;
            }
        }

        List<CaseTask> activeTasks = caseTaskMapper.selectList(new LambdaQueryWrapper<CaseTask>()
                .eq(CaseTask::getCaseId, caseId)
                .in(CaseTask::getStatus, TASK_PENDING, TASK_CLAIMED, "processing", TASK_SUBFLOW_RUNNING));
        
        for (CaseTask task : activeTasks) {
            if (Objects.equals(task.getId(), currentTaskId) || parentTaskIds.contains(task.getId())) {
                continue;
            }
            boolean isSubflowRunner = TASK_SUBFLOW_RUNNING.equals(task.getStatus());
            task.setStatus(TASK_CANCELLED);
            task.setCompletedTime(now);
            task.setResultAction("CANCEL");
            task.setResultOpinion("案件已归档，自动取消");
            caseTaskMapper.updateById(task);

            if (isSubflowRunner) {
                CaseSubflowInstance subflow = caseSubflowInstanceMapper.selectOne(new LambdaQueryWrapper<CaseSubflowInstance>()
                        .eq(CaseSubflowInstance::getParentTaskId, task.getId()));
                if (subflow != null && WORKFLOW_RUNNING.equals(subflow.getStatus())) {
                    subflow.setStatus(WORKFLOW_TERMINATED);
                    subflow.setCompletedTime(now);
                    caseSubflowInstanceMapper.updateById(subflow);
                }
            }
        }
    }
}
