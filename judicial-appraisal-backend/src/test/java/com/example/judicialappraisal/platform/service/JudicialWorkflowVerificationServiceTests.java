package com.example.judicialappraisal.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.platform.dto.JudicialCatalogDto;
import com.example.judicialappraisal.platform.dto.JudicialFormDefinitionDto;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowDefinitionDto;
import com.example.judicialappraisal.platform.dto.JudicialWorkflowVerificationReportDto;
import com.example.judicialappraisal.workflow.design.FormDefinition;
import com.example.judicialappraisal.workflow.design.FormDefinitionMapper;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudicialWorkflowVerificationServiceTests {

    @Mock
    private PlatformCatalogService platformCatalogService;
    @Mock
    private WfDefinitionMapper wfDefinitionMapper;
    @Mock
    private WfNodeDefMapper wfNodeDefMapper;
    @Mock
    private WfTransitionDefMapper wfTransitionDefMapper;
    @Mock
    private FormDefinitionMapper formDefinitionMapper;

    private JudicialWorkflowVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new JudicialWorkflowVerificationService(platformCatalogService, wfDefinitionMapper,
                wfNodeDefMapper, wfTransitionDefMapper, formDefinitionMapper, new ObjectMapper());
    }

    @Test
    void verificationFailsWhenLaunchedSubflowIsNotPublished() {
        when(platformCatalogService.judicialCatalog()).thenReturn(catalog());
        when(wfDefinitionMapper.selectOne(any())).thenReturn(definition(10L, "received-entrust", 3), (WfDefinition) null);
        when(formDefinitionMapper.selectOne(any())).thenReturn(form("received-entrust"));
        when(wfNodeDefMapper.selectList(any())).thenReturn(nodes());
        when(wfTransitionDefMapper.selectList(any())).thenReturn(List.of(
                transition("START", "INIT_FILL", "APPROVE", null),
                transition("INIT_FILL", "START", "RETURN", null),
                transition("INIT_FILL", "PRELIMINARY_SURVEY", "APPROVE", """
                        {"launchSubflow":true,"subflowCode":"preliminary-survey"}
                        """),
                transition("PRELIMINARY_SURVEY", "END", "COMPLETE", null)
        ));

        JudicialWorkflowVerificationReportDto report = verificationService.verifyCatalog();

        assertThat(report.failedWorkflowCount()).isEqualTo(1);
        assertThat(report.workflows().get(0).missingSubflowTargets()).containsExactly("preliminary-survey");
        assertThat(report.workflows().get(0).issues()).contains("子流程目标未发布：preliminary-survey");
        assertThat(report.workflows().get(0).passed()).isFalse();
    }

    @Test
    void verificationPassesForPublishedWorkflowWithNodesTransitionsAndSubflowTarget() {
        when(platformCatalogService.judicialCatalog()).thenReturn(catalog());
        when(wfDefinitionMapper.selectOne(any())).thenReturn(
                definition(10L, "received-entrust", 3),
                definition(20L, "preliminary-survey", 1)
        );
        when(formDefinitionMapper.selectOne(any())).thenReturn(form("received-entrust"));
        when(wfNodeDefMapper.selectList(any())).thenReturn(nodes());
        when(wfTransitionDefMapper.selectList(any())).thenReturn(List.of(
                transition("START", "INIT_FILL", "APPROVE", null),
                transition("INIT_FILL", "START", "RETURN", null),
                transition("INIT_FILL", "PRELIMINARY_SURVEY", "APPROVE", """
                        {"launchSubflow":true,"subflowCode":"preliminary-survey"}
                        """),
                transition("PRELIMINARY_SURVEY", "END", "COMPLETE", null)
        ));

        JudicialWorkflowVerificationReportDto report = verificationService.verifyCatalog();

        assertThat(report.passedWorkflowCount()).isEqualTo(1);
        assertThat(report.workflows().get(0).publishedVersion()).isEqualTo(3);
        assertThat(report.workflows().get(0).subflowTargets()).containsExactly("preliminary-survey");
        assertThat(report.workflows().get(0).issues()).isEmpty();
        assertThat(report.workflows().get(0).passed()).isTrue();
    }

    private JudicialCatalogDto catalog() {
        JudicialWorkflowDefinitionDto workflow = new JudicialWorkflowDefinitionDto(
                "received-entrust",
                "收到委托书",
                "received-entrust",
                "subflow",
                List.of("项目负责人"),
                List.of("测试规则"),
                List.of("初步勘验")
        );
        JudicialFormDefinitionDto form = new JudicialFormDefinitionDto("received-entrust", "收到委托书", "", List.of(), List.of(), List.of());
        return new JudicialCatalogDto(1, 1, List.of("项目负责人"), List.of(workflow), List.of(form));
    }

    private WfDefinition definition(Long id, String code, Integer version) {
        WfDefinition definition = new WfDefinition();
        definition.setId(id);
        definition.setWfCode(code);
        definition.setVersionNo(version);
        definition.setPublishStatus("published");
        definition.setDeleted(0);
        return definition;
    }

    private FormDefinition form(String code) {
        FormDefinition form = new FormDefinition();
        form.setId(1L);
        form.setFormCode(code);
        form.setCurrentPublishedVersion(1);
        form.setDeleted(0);
        return form;
    }

    private List<WfNodeDef> nodes() {
        return List.of(
                node("START", "start"),
                node("INIT_FILL", "task"),
                node("PRELIMINARY_SURVEY", "task"),
                node("END", "end")
        );
    }

    private WfNodeDef node(String code, String type) {
        WfNodeDef node = new WfNodeDef();
        node.setNodeCode(code);
        node.setNodeType(type);
        node.setDeleted(0);
        return node;
    }

    private WfTransitionDef transition(String from, String to, String action, String config) {
        WfTransitionDef transition = new WfTransitionDef();
        transition.setFromNodeCode(from);
        transition.setToNodeCode(to);
        transition.setActionCode(action);
        transition.setActionName(action);
        transition.setTransitionConfigJson(config);
        transition.setDeleted(0);
        return transition;
    }
}
