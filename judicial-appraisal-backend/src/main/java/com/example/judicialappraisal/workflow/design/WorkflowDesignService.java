package com.example.judicialappraisal.workflow.design;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDefinitionDto;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowNodeDto;
import com.example.judicialappraisal.workflow.design.dto.WorkflowNodeRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowTransitionDto;
import com.example.judicialappraisal.workflow.design.dto.WorkflowTransitionRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowVersionDto;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowDesignService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String NODE_START = "start";
    private static final String NODE_END = "end";

    private final com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper wfDefinitionMapper;
    private final com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper wfNodeDefMapper;
    private final com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper wfTransitionDefMapper;

    public WorkflowDesignService(com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper wfDefinitionMapper,
                                 com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper wfNodeDefMapper,
                                 com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper wfTransitionDefMapper) {
        this.wfDefinitionMapper = wfDefinitionMapper;
        this.wfNodeDefMapper = wfNodeDefMapper;
        this.wfTransitionDefMapper = wfTransitionDefMapper;
    }

    public List<WorkflowDefinitionDto> listDefinitions() {
        return wfDefinitionMapper.selectList(new LambdaQueryWrapper<WfDefinition>()
                        .eq(WfDefinition::getDeleted, 0)
                        .orderByAsc(WfDefinition::getWfCode)
                        .orderByDesc(WfDefinition::getVersionNo)
                        .orderByDesc(WfDefinition::getId))
                .stream()
                .map(this::toDetail)
                .toList();
    }

    public List<WorkflowVersionDto> listVersions(String wfCode) {
        return wfDefinitionMapper.selectList(new LambdaQueryWrapper<WfDefinition>()
                        .eq(WfDefinition::getWfCode, wfCode)
                        .eq(WfDefinition::getDeleted, 0)
                        .orderByDesc(WfDefinition::getVersionNo)
                        .orderByDesc(WfDefinition::getId))
                .stream()
                .map(this::toVersionDto)
                .toList();
    }

    public WorkflowDefinitionDto getDraft(String wfCode) {
        return loadDetail(requireDraft(wfCode));
    }

    public WorkflowDefinitionDto preview(String wfCode) {
        WfDefinition published = latestPublished(wfCode);
        if (published != null) {
            return loadDetail(published);
        }
        return loadDetail(requireDraft(wfCode));
    }

    @Transactional
    public WorkflowDefinitionDto saveDraft(WorkflowDesignRequest request) {
        validateRequest(request);
        WfDefinition definition = ensureDefinition(request);
        saveChildren(definition.getId(), request.nodes(), request.transitions());
        return loadDetail(definition);
    }

    @Transactional
    public WorkflowDefinitionDto publish(String wfCode) {
        WfDefinition draft = requireDraft(wfCode);
        List<WfNodeDef> nodes = wfNodeDefMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getWfId, draft.getId())
                .eq(WfNodeDef::getDeleted, 0));
        List<WfTransitionDef> transitions = wfTransitionDefMapper.selectList(new LambdaQueryWrapper<WfTransitionDef>()
                .eq(WfTransitionDef::getWfId, draft.getId())
                .eq(WfTransitionDef::getDeleted, 0));
        validateGraph(nodes, transitions);

        int nextVersion = nextPublishedVersion(wfCode);
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();
        WfDefinition published = cloneDefinition(draft);
        published.setId(null);
        published.setVersionNo(nextVersion);
        published.setPublishStatus(STATUS_PUBLISHED);
        published.setEnabled(1);
        published.setImmutableFlag(1);
        published.setSourceWfId(draft.getId());
        published.setPublishedBy(userId);
        published.setPublishedTime(now);
        published.setCreatedBy(userId);
        published.setUpdatedBy(userId);
        published.setCreatedTime(now);
        published.setUpdatedTime(now);
        wfDefinitionMapper.insert(published);

        cloneChildren(draft.getId(), published.getId(), nodes, transitions, userId, now);
        return loadDetail(published);
    }

    @Transactional
    public WorkflowDefinitionDto restore(String wfCode, Integer versionNo) {
        if (versionNo == null || versionNo <= 0) {
            throw new BusinessException("版本号不能为空");
        }
        WfDefinition source = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getVersionNo, versionNo)
                .eq(WfDefinition::getPublishStatus, STATUS_PUBLISHED)
                .eq(WfDefinition::getDeleted, 0)
                .last("limit 1"));
        if (source == null) {
            throw new BusinessException("版本不存在");
        }
        List<WfNodeDef> nodes = wfNodeDefMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getWfId, source.getId())
                .eq(WfNodeDef::getDeleted, 0));
        List<WfTransitionDef> transitions = wfTransitionDefMapper.selectList(new LambdaQueryWrapper<WfTransitionDef>()
                .eq(WfTransitionDef::getWfId, source.getId())
                .eq(WfTransitionDef::getDeleted, 0));
        deleteExistingDraft(wfCode);

        WfDefinition draft = cloneDefinition(source);
        draft.setId(null);
        draft.setVersionNo(0);
        draft.setPublishStatus(STATUS_DRAFT);
        draft.setEnabled(1);
        draft.setImmutableFlag(0);
        draft.setSourceWfId(source.getId());
        draft.setPublishedBy(null);
        draft.setPublishedTime(null);
        draft.setCreatedBy(currentUserId());
        draft.setUpdatedBy(currentUserId());
        draft.setCreatedTime(LocalDateTime.now());
        draft.setUpdatedTime(LocalDateTime.now());
        wfDefinitionMapper.insert(draft);

        cloneChildren(source.getId(), draft.getId(), nodes, transitions, currentUserId(), LocalDateTime.now());
        return loadDetail(draft);
    }

    public WorkflowDefinitionDto getDefinition(String wfCode) {
        return loadDetail(requireDefinition(wfCode));
    }

    private void validateRequest(WorkflowDesignRequest request) {
        if (request.wfCode() == null || request.wfCode().isBlank()) {
            throw new BusinessException("流程编码不能为空");
        }
        if (request.wfName() == null || request.wfName().isBlank()) {
            throw new BusinessException("流程名称不能为空");
        }
        if (request.nodes() == null || request.nodes().isEmpty()) {
            throw new BusinessException("流程节点不能为空");
        }
        if (request.transitions() == null || request.transitions().isEmpty()) {
            throw new BusinessException("流程流转不能为空");
        }
    }

    private WfDefinition ensureDefinition(WorkflowDesignRequest request) {
        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, request.wfCode())
                .eq(WfDefinition::getVersionNo, 0)
                .eq(WfDefinition::getDeleted, 0)
                .last("limit 1"));
        if (definition == null) {
            definition = new WfDefinition();
            definition.setWfCode(request.wfCode());
            definition.setWfName(request.wfName());
            definition.setWfType(request.wfType());
            definition.setFormCode(request.formCode());
            definition.setVersionNo(0);
            definition.setEnabled(1);
            definition.setPublishStatus(STATUS_DRAFT);
            definition.setRemark(request.remark());
            definition.setDefinitionJson(request.definitionJson());
            definition.setImmutableFlag(0);
            definition.setCreatedBy(currentUserId());
            definition.setUpdatedBy(currentUserId());
            definition.setCreatedTime(LocalDateTime.now());
            definition.setUpdatedTime(LocalDateTime.now());
            wfDefinitionMapper.insert(definition);
        } else {
            definition.setWfName(request.wfName());
            definition.setWfType(request.wfType());
            definition.setFormCode(request.formCode());
            definition.setRemark(request.remark());
            definition.setDefinitionJson(request.definitionJson());
            definition.setUpdatedBy(currentUserId());
            definition.setUpdatedTime(LocalDateTime.now());
            wfDefinitionMapper.updateById(definition);
            deleteDefinitionChildren(definition.getId());
        }
        return definition;
    }

    private WfDefinition requireDefinition(String wfCode) {
        WfDefinition definition = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("limit 1"));
        if (definition == null) {
            throw new BusinessException("流程不存在");
        }
        return definition;
    }

    private WfDefinition requireDraft(String wfCode) {
        WfDefinition draft = wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getVersionNo, 0)
                .eq(WfDefinition::getPublishStatus, STATUS_DRAFT)
                .eq(WfDefinition::getDeleted, 0)
                .last("limit 1"));
        if (draft == null) {
            throw new BusinessException("草稿不存在");
        }
        return draft;
    }

    private WfDefinition latestPublished(String wfCode) {
        return wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getPublishStatus, STATUS_PUBLISHED)
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("limit 1"));
    }

    private int nextPublishedVersion(String wfCode) {
        WfDefinition latest = latestPublished(wfCode);
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private void saveChildren(Long wfId, List<WorkflowNodeRequest> nodes, List<WorkflowTransitionRequest> transitions) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();
        for (WorkflowNodeRequest request : nodes) {
            WfNodeDef node = new WfNodeDef();
            node.setWfId(wfId);
            node.setNodeCode(request.nodeCode());
            node.setNodeName(request.nodeName());
            node.setNodeType(request.nodeType());
            node.setTaskType(request.taskType());
            node.setCaseStatus(request.caseStatus());
            node.setHandlerDeptRule(request.handlerDeptRule());
            node.setHandlerPostRule(request.handlerPostRule());
            node.setHandlerRoleRule(request.handlerRoleRule());
            node.setAllowManualAssign(request.allowManualAssign());
            node.setTimeoutHours(request.timeoutHours());
            node.setConfigJson(request.configJson());
            node.setAssigneeRuleJson(request.assigneeRuleJson());
            node.setFormRuleJson(request.formRuleJson());
            node.setPermissionJson(request.permissionJson());
            node.setSortNo(request.sortNo());
            node.setEnabled(request.enabled() == null ? 1 : request.enabled());
            node.setCreatedBy(userId);
            node.setUpdatedBy(userId);
            node.setCreatedTime(now);
            node.setUpdatedTime(now);
            wfNodeDefMapper.insert(node);
        }
        for (WorkflowTransitionRequest request : transitions) {
            WfTransitionDef transition = new WfTransitionDef();
            transition.setWfId(wfId);
            transition.setFromNodeCode(request.fromNodeCode());
            transition.setToNodeCode(request.toNodeCode());
            transition.setActionCode(request.actionCode());
            transition.setActionName(request.actionName());
            transition.setRequireReason(request.requireReason());
            transition.setRequireOpinion(request.requireOpinion());
            transition.setConditionExpression(request.conditionExpression());
            transition.setTransitionConfigJson(request.transitionConfigJson());
            transition.setEnabled(request.enabled() == null ? 1 : request.enabled());
            transition.setSortNo(request.sortNo());
            transition.setCreatedBy(userId);
            transition.setUpdatedBy(userId);
            transition.setCreatedTime(now);
            transition.setUpdatedTime(now);
            wfTransitionDefMapper.insert(transition);
        }
    }

    private void cloneChildren(Long sourceWfId, Long targetWfId, List<WfNodeDef> nodes, List<WfTransitionDef> transitions, Long userId, LocalDateTime now) {
        for (WfNodeDef source : nodes) {
            WfNodeDef clone = new WfNodeDef();
            clone.setWfId(targetWfId);
            clone.setNodeCode(source.getNodeCode());
            clone.setNodeName(source.getNodeName());
            clone.setNodeType(source.getNodeType());
            clone.setTaskType(source.getTaskType());
            clone.setCaseStatus(source.getCaseStatus());
            clone.setHandlerDeptRule(source.getHandlerDeptRule());
            clone.setHandlerPostRule(source.getHandlerPostRule());
            clone.setHandlerRoleRule(source.getHandlerRoleRule());
            clone.setAllowManualAssign(source.getAllowManualAssign());
            clone.setTimeoutHours(source.getTimeoutHours());
            clone.setConfigJson(source.getConfigJson());
            clone.setAssigneeRuleJson(source.getAssigneeRuleJson());
            clone.setFormRuleJson(source.getFormRuleJson());
            clone.setPermissionJson(source.getPermissionJson());
            clone.setSortNo(source.getSortNo());
            clone.setEnabled(source.getEnabled());
            clone.setCreatedBy(userId);
            clone.setUpdatedBy(userId);
            clone.setCreatedTime(now);
            clone.setUpdatedTime(now);
            wfNodeDefMapper.insert(clone);
        }
        for (WfTransitionDef source : transitions) {
            WfTransitionDef clone = new WfTransitionDef();
            clone.setWfId(targetWfId);
            clone.setFromNodeCode(source.getFromNodeCode());
            clone.setToNodeCode(source.getToNodeCode());
            clone.setActionCode(source.getActionCode());
            clone.setActionName(source.getActionName());
            clone.setRequireReason(source.getRequireReason());
            clone.setRequireOpinion(source.getRequireOpinion());
            clone.setConditionExpression(source.getConditionExpression());
            clone.setTransitionConfigJson(source.getTransitionConfigJson());
            clone.setEnabled(source.getEnabled());
            clone.setSortNo(source.getSortNo());
            clone.setCreatedBy(userId);
            clone.setUpdatedBy(userId);
            clone.setCreatedTime(now);
            clone.setUpdatedTime(now);
            wfTransitionDefMapper.insert(clone);
        }
    }

    private void deleteExistingDraft(String wfCode) {
        List<WfDefinition> drafts = wfDefinitionMapper.selectList(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getVersionNo, 0)
                .eq(WfDefinition::getPublishStatus, STATUS_DRAFT)
                .eq(WfDefinition::getDeleted, 0));
        for (WfDefinition draft : drafts) {
            deleteDefinitionChildren(draft.getId());
            wfDefinitionMapper.deleteById(draft.getId());
        }
    }

    private void deleteDefinitionChildren(Long wfId) {
        wfNodeDefMapper.physicalDeleteByWfId(wfId);
        wfTransitionDefMapper.physicalDeleteByWfId(wfId);
    }

    private void validateGraph(List<WfNodeDef> nodes, List<WfTransitionDef> transitions) {
        if (nodes == null || nodes.isEmpty()) {
            throw new BusinessException("流程节点不能为空");
        }
        long startCount = nodes.stream().filter(node -> NODE_START.equalsIgnoreCase(node.getNodeType())).count();
        long endCount = nodes.stream().filter(node -> NODE_END.equalsIgnoreCase(node.getNodeType())).count();
        if (startCount != 1) {
            throw new BusinessException("流程必须且只能包含一个开始节点");
        }
        if (endCount < 1) {
            throw new BusinessException("流程必须至少包含一个结束节点");
        }
        Set<String> nodeCodes = new LinkedHashSet<>();
        for (WfNodeDef node : nodes) {
            if (node.getNodeCode() == null || node.getNodeCode().isBlank()) {
                throw new BusinessException("节点编码不能为空");
            }
            nodeCodes.add(node.getNodeCode());
        }
        for (WfTransitionDef transition : transitions) {
            if (!nodeCodes.contains(transition.getFromNodeCode()) || !nodeCodes.contains(transition.getToNodeCode())) {
                throw new BusinessException("流转定义引用了不存在的节点");
            }
        }
    }

    private WorkflowDefinitionDto loadDetail(WfDefinition definition) {
        List<WorkflowNodeDto> nodes = wfNodeDefMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                        .eq(WfNodeDef::getWfId, definition.getId())
                        .eq(WfNodeDef::getDeleted, 0)
                        .orderByAsc(WfNodeDef::getSortNo)
                        .orderByAsc(WfNodeDef::getId))
                .stream()
                .map(this::toNodeDto)
                .toList();
        List<WorkflowTransitionDto> transitions = wfTransitionDefMapper.selectList(new LambdaQueryWrapper<WfTransitionDef>()
                        .eq(WfTransitionDef::getWfId, definition.getId())
                        .eq(WfTransitionDef::getDeleted, 0)
                        .orderByAsc(WfTransitionDef::getSortNo)
                        .orderByAsc(WfTransitionDef::getId))
                .stream()
                .map(this::toTransitionDto)
                .toList();
        return new WorkflowDefinitionDto(
                definition.getId(),
                definition.getWfCode(),
                definition.getWfName(),
                definition.getWfType(),
                definition.getFormCode(),
                definition.getVersionNo(),
                definition.getEnabled(),
                definition.getPublishStatus(),
                definition.getRemark(),
                definition.getDefinitionJson(),
                definition.getSourceWfId(),
                definition.getPublishedBy(),
                definition.getPublishedTime(),
                definition.getImmutableFlag(),
                definition.getCreatedTime(),
                definition.getUpdatedTime(),
                nodes,
                transitions
        );
    }

    private WorkflowDefinitionDto toDetail(WfDefinition definition) {
        return loadDetail(definition);
    }

    private WorkflowVersionDto toVersionDto(WfDefinition definition) {
        return new WorkflowVersionDto(
                definition.getId(),
                definition.getWfCode(),
                definition.getWfName(),
                definition.getWfType(),
                definition.getFormCode(),
                definition.getVersionNo(),
                definition.getEnabled(),
                definition.getPublishStatus(),
                definition.getRemark(),
                definition.getDefinitionJson(),
                definition.getSourceWfId(),
                definition.getPublishedBy(),
                definition.getPublishedTime(),
                definition.getImmutableFlag(),
                definition.getCreatedTime(),
                definition.getUpdatedTime()
        );
    }

    private WorkflowNodeDto toNodeDto(WfNodeDef node) {
        return new WorkflowNodeDto(
                node.getId(),
                node.getWfId(),
                node.getNodeCode(),
                node.getNodeName(),
                node.getNodeType(),
                node.getTaskType(),
                node.getCaseStatus(),
                node.getHandlerDeptRule(),
                node.getHandlerPostRule(),
                node.getHandlerRoleRule(),
                node.getAllowManualAssign(),
                node.getTimeoutHours(),
                node.getConfigJson(),
                node.getAssigneeRuleJson(),
                node.getFormRuleJson(),
                node.getPermissionJson(),
                node.getSortNo(),
                node.getEnabled(),
                node.getCreatedTime(),
                node.getUpdatedTime()
        );
    }

    private WorkflowTransitionDto toTransitionDto(WfTransitionDef transition) {
        return new WorkflowTransitionDto(
                transition.getId(),
                transition.getWfId(),
                transition.getFromNodeCode(),
                transition.getToNodeCode(),
                transition.getActionCode(),
                transition.getActionName(),
                transition.getRequireReason(),
                transition.getRequireOpinion(),
                transition.getConditionExpression(),
                transition.getTransitionConfigJson(),
                transition.getEnabled(),
                transition.getSortNo(),
                transition.getCreatedTime(),
                transition.getUpdatedTime()
        );
    }

    private WfDefinition cloneDefinition(WfDefinition source) {
        WfDefinition clone = new WfDefinition();
        clone.setWfCode(source.getWfCode());
        clone.setWfName(source.getWfName());
        clone.setWfType(source.getWfType());
        clone.setFormCode(source.getFormCode());
        clone.setRemark(source.getRemark());
        clone.setDefinitionJson(source.getDefinitionJson());
        clone.setSourceWfId(source.getSourceWfId());
        return clone;
    }

    private Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserInfo userInfo)) {
            return null;
        }
        return userInfo.id();
    }
}
