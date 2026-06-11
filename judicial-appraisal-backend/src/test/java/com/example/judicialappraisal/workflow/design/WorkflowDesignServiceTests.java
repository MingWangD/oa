package com.example.judicialappraisal.workflow.design;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowDesignServiceTests {

    private final WfDefinitionMapper wfDefinitionMapper = mock(WfDefinitionMapper.class);
    private final WfNodeDefMapper wfNodeDefMapper = mock(WfNodeDefMapper.class);
    private final WfTransitionDefMapper wfTransitionDefMapper = mock(WfTransitionDefMapper.class);
    private final WorkflowDesignService service = new WorkflowDesignService(
            wfDefinitionMapper,
            wfNodeDefMapper,
            wfTransitionDefMapper
    );

    @Test
    void publishRejectsGraphWithoutEndNode() {
        WfDefinition draft = new WfDefinition();
        draft.setId(21L);
        draft.setWfCode("bad-flow");
        draft.setWfName("坏流程");
        draft.setVersionNo(0);
        draft.setPublishStatus("draft");

        WfNodeDef start = node("START", "start");
        WfNodeDef task = node("TASK", "task");
        WfTransitionDef transition = transition("START", "TASK");

        when(wfDefinitionMapper.selectOne(any())).thenReturn(draft);
        when(wfNodeDefMapper.selectList(any())).thenReturn(List.of(start, task));
        when(wfTransitionDefMapper.selectList(any())).thenReturn(List.of(transition));

        assertThatThrownBy(() -> service.publish("bad-flow"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束节点");
        verify(wfDefinitionMapper, never()).insert(any(WfDefinition.class));
    }

    private WfNodeDef node(String code, String type) {
        WfNodeDef node = new WfNodeDef();
        node.setNodeCode(code);
        node.setNodeName(code);
        node.setNodeType(type);
        node.setEnabled(1);
        return node;
    }

    private WfTransitionDef transition(String from, String to) {
        WfTransitionDef transition = new WfTransitionDef();
        transition.setFromNodeCode(from);
        transition.setToNodeCode(to);
        transition.setActionCode("APPROVE");
        transition.setActionName("通过");
        return transition;
    }
}
