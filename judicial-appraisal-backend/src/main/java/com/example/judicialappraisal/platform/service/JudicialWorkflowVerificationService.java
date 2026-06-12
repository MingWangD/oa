package com.example.judicialappraisal.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowDefinitionDto;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowVerificationDto;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowVerificationReportDto;
import com.example.judicialappraisal.workflow.design.FormDefinition;
import com.example.judicialappraisal.workflow.design.FormDefinitionMapper;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class JudicialWorkflowVerificationService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PlatformCatalogService platformCatalogService;
    private final WfDefinitionMapper wfDefinitionMapper;
    private final WfNodeDefMapper wfNodeDefMapper;
    private final WfTransitionDefMapper wfTransitionDefMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ObjectMapper objectMapper;

    public JudicialWorkflowVerificationService(PlatformCatalogService platformCatalogService,
                                               WfDefinitionMapper wfDefinitionMapper,
                                               WfNodeDefMapper wfNodeDefMapper,
                                               WfTransitionDefMapper wfTransitionDefMapper,
                                               FormDefinitionMapper formDefinitionMapper,
                                               ObjectMapper objectMapper) {
        this.platformCatalogService = platformCatalogService;
        this.wfDefinitionMapper = wfDefinitionMapper;
        this.wfNodeDefMapper = wfNodeDefMapper;
        this.wfTransitionDefMapper = wfTransitionDefMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.objectMapper = objectMapper;
    }

    public JudicialWorkflowVerificationReportDto verifyCatalog() {
        var catalog = platformCatalogService.judicialCatalog();
        Set<String> expectedFormCodes = catalog.forms().stream()
                .map(item -> item.code())
                .collect(Collectors.toSet());
        List<JudicialWorkflowVerificationDto> workflows = catalog.workflows().stream()
                .map(workflow -> verifyWorkflow(workflow, expectedFormCodes))
                .toList();
        int passed = (int) workflows.stream().filter(JudicialWorkflowVerificationDto::passed).count();
        return new JudicialWorkflowVerificationReportDto(
                workflows.size(),
                workflows.size(),
                passed,
                workflows.size() - passed,
                workflows
        );
    }

    private JudicialWorkflowVerificationDto verifyWorkflow(JudicialWorkflowDefinitionDto catalogWorkflow, Set<String> expectedFormCodes) {
        WfDefinition definition = latestPublishedWorkflow(catalogWorkflow.code());
        boolean expectsPublishedForm = expectedFormCodes.contains(catalogWorkflow.formCode());
        FormDefinition formDefinition = expectsPublishedForm ? publishedForm(catalogWorkflow.formCode()) : null;
        List<String> issues = new ArrayList<>();
        Set<String> subflowTargets = new LinkedHashSet<>();
        Set<String> missingSubflowTargets = new LinkedHashSet<>();

        if (definition == null) {
            issues.add("未找到已发布流程版本");
            if (expectsPublishedForm && formDefinition == null) {
                issues.add("关联表单未发布：" + catalogWorkflow.formCode());
            }
            return dto(catalogWorkflow, null, formDefinition, !expectsPublishedForm, List.of(), List.of(), subflowTargets, missingSubflowTargets, issues);
        }
        if (expectsPublishedForm && formDefinition == null) {
            issues.add("关联表单未发布：" + catalogWorkflow.formCode());
        }

        List<WfNodeDef> nodes = nodes(definition.getId());
        List<WfTransitionDef> transitions = transitions(definition.getId());
        Set<String> nodeCodes = nodes.stream().map(WfNodeDef::getNodeCode).collect(LinkedHashSet::new, Set::add, Set::addAll);

        boolean hasStart = nodes.stream().anyMatch(item -> "start".equalsIgnoreCase(item.getNodeType()));
        boolean hasEnd = nodes.stream().anyMatch(item -> "end".equalsIgnoreCase(item.getNodeType()));
        boolean hasActionableNode = nodes.stream().anyMatch(item -> !"start".equalsIgnoreCase(item.getNodeType())
                && !"end".equalsIgnoreCase(item.getNodeType()));
        boolean hasReturnPath = transitions.stream().anyMatch(item -> "RETURN".equalsIgnoreCase(item.getActionCode())
                || safeContains(item.getActionName(), "退回"));
        boolean hasEndPath = transitions.stream().anyMatch(item -> nodeCodes.contains(item.getFromNodeCode())
                && ("END".equalsIgnoreCase(item.getToNodeCode()) || isEndNode(nodes, item.getToNodeCode())));

        if (!hasStart) {
            issues.add("缺少开始节点");
        }
        if (!hasEnd) {
            issues.add("缺少结束节点");
        }
        if (!hasActionableNode) {
            issues.add("缺少可办理节点");
        }
        if (!hasReturnPath && requiresReturnPath(catalogWorkflow)) {
            issues.add("缺少退回路径");
        }
        if (!hasEndPath) {
            issues.add("缺少结束路径");
        }

        for (WfTransitionDef transition : transitions) {
            if (!nodeCodes.contains(transition.getFromNodeCode())) {
                issues.add("连线来源节点不存在：" + transition.getFromNodeCode());
            }
            if (!nodeCodes.contains(transition.getToNodeCode())) {
                issues.add("连线目标节点不存在：" + transition.getToNodeCode());
            }
            String subflowCode = subflowCode(transition.getTransitionConfigJson());
            if (subflowCode != null) {
                subflowTargets.add(subflowCode);
                if (latestPublishedWorkflow(subflowCode) == null) {
                    missingSubflowTargets.add(subflowCode);
                    issues.add("子流程目标未发布：" + subflowCode);
                }
            }
        }

        return dto(catalogWorkflow, definition, formDefinition, !expectsPublishedForm, nodes, transitions, subflowTargets, missingSubflowTargets, issues);
    }

    private JudicialWorkflowVerificationDto dto(JudicialWorkflowDefinitionDto catalogWorkflow,
                                                WfDefinition definition,
                                                FormDefinition formDefinition,
                                                boolean formNotRequired,
                                                List<WfNodeDef> nodes,
                                                List<WfTransitionDef> transitions,
                                                Set<String> subflowTargets,
                                                Set<String> missingSubflowTargets,
                                                List<String> issues) {
        boolean hasStart = nodes.stream().anyMatch(item -> "start".equalsIgnoreCase(item.getNodeType()));
        boolean hasEnd = nodes.stream().anyMatch(item -> "end".equalsIgnoreCase(item.getNodeType()));
        boolean hasActionableNode = nodes.stream().anyMatch(item -> !"start".equalsIgnoreCase(item.getNodeType())
                && !"end".equalsIgnoreCase(item.getNodeType()));
        boolean hasReturnPath = transitions.stream().anyMatch(item -> "RETURN".equalsIgnoreCase(item.getActionCode())
                || safeContains(item.getActionName(), "退回"));
        boolean hasEndPath = transitions.stream().anyMatch(item -> "END".equalsIgnoreCase(item.getToNodeCode()));
        return new JudicialWorkflowVerificationDto(
                catalogWorkflow.code(),
                catalogWorkflow.name(),
                catalogWorkflow.formCode(),
                catalogWorkflow.entryMode(),
                definition != null,
                definition == null ? null : definition.getVersionNo(),
                formDefinition != null || formNotRequired,
                nodes.size(),
                transitions.size(),
                hasStart,
                hasEnd,
                hasActionableNode,
                hasReturnPath,
                hasEndPath,
                new ArrayList<>(subflowTargets),
                new ArrayList<>(missingSubflowTargets),
                issues,
                issues.isEmpty()
        );
    }

    private WfDefinition latestPublishedWorkflow(String wfCode) {
        return wfDefinitionMapper.selectOne(new LambdaQueryWrapper<WfDefinition>()
                .eq(WfDefinition::getWfCode, wfCode)
                .eq(WfDefinition::getPublishStatus, "published")
                .eq(WfDefinition::getDeleted, 0)
                .orderByDesc(WfDefinition::getVersionNo)
                .orderByDesc(WfDefinition::getId)
                .last("limit 1"));
    }

    private FormDefinition publishedForm(String formCode) {
        return formDefinitionMapper.selectOne(new LambdaQueryWrapper<FormDefinition>()
                .eq(FormDefinition::getFormCode, formCode)
                .eq(FormDefinition::getDeleted, 0)
                .gt(FormDefinition::getCurrentPublishedVersion, 0)
                .orderByDesc(FormDefinition::getCurrentPublishedVersion)
                .orderByDesc(FormDefinition::getId)
                .last("limit 1"));
    }

    private List<WfNodeDef> nodes(Long wfId) {
        return wfNodeDefMapper.selectList(new LambdaQueryWrapper<WfNodeDef>()
                .eq(WfNodeDef::getWfId, wfId)
                .eq(WfNodeDef::getDeleted, 0)
                .orderByAsc(WfNodeDef::getSortNo)
                .orderByAsc(WfNodeDef::getId));
    }

    private List<WfTransitionDef> transitions(Long wfId) {
        return wfTransitionDefMapper.selectList(new LambdaQueryWrapper<WfTransitionDef>()
                .eq(WfTransitionDef::getWfId, wfId)
                .eq(WfTransitionDef::getDeleted, 0)
                .orderByAsc(WfTransitionDef::getSortNo)
                .orderByAsc(WfTransitionDef::getId));
    }

    private boolean isEndNode(List<WfNodeDef> nodes, String nodeCode) {
        return nodes.stream().anyMatch(item -> item.getNodeCode().equals(nodeCode) && "end".equalsIgnoreCase(item.getNodeType()));
    }

    private boolean requiresReturnPath(JudicialWorkflowDefinitionDto workflow) {
        return !"direct".equalsIgnoreCase(workflow.entryMode())
                || workflow.roles().stream().anyMatch(role -> role.contains("负责人") || role.contains("管理员"));
    }

    private String subflowCode(String transitionConfigJson) {
        if (transitionConfigJson == null || transitionConfigJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(transitionConfigJson, MAP_TYPE);
            if (Boolean.TRUE.equals(config.get("launchSubflow")) && config.get("subflowCode") != null) {
                return String.valueOf(config.get("subflowCode"));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean safeContains(String value, String part) {
        return value != null && value.contains(part);
    }
}
