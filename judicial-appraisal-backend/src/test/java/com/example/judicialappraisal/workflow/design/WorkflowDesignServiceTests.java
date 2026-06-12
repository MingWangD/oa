package com.example.judicialappraisal.workflow.design;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.workflow.design.dto.WorkflowDesignRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowNodeRequest;
import com.example.judicialappraisal.workflow.design.dto.WorkflowTransitionRequest;
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

    @Test
    void saveDraftPhysicallyClearsExistingChildrenBeforeReinsert() {
        WfDefinition draft = new WfDefinition();
        draft.setId(21L);
        draft.setWfCode("received-entrust");
        draft.setWfName("收到委托书");
        draft.setVersionNo(0);
        draft.setPublishStatus("draft");

        when(wfDefinitionMapper.selectOne(any())).thenReturn(draft);
        when(wfNodeDefMapper.selectList(any())).thenReturn(List.of());
        when(wfTransitionDefMapper.selectList(any())).thenReturn(List.of());

        WorkflowDesignRequest request = new WorkflowDesignRequest(
                "received-entrust",
                "收到委托书",
                "judicial",
                "received-entrust",
                "高保真草稿",
                "{}",
                List.of(
                        nodeRequest("START", "开始", "start", 0),
                        nodeRequest("INIT_FILL", "发起者填写委托信息", "task", 10),
                        nodeRequest("END", "流程结束", "end", 20)
                ),
                List.of(
                        transitionRequest("START", "INIT_FILL"),
                        transitionRequest("INIT_FILL", "END")
                )
        );

        service.saveDraft(request);

        verify(wfNodeDefMapper).physicalDeleteByWfId(21L);
        verify(wfTransitionDefMapper).physicalDeleteByWfId(21L);
        verify(wfNodeDefMapper, never()).delete(any());
        verify(wfTransitionDefMapper, never()).delete(any());
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

    private WorkflowNodeRequest nodeRequest(String code, String name, String type, int sortNo) {
        return new WorkflowNodeRequest(
                code,
                name,
                type,
                "single",
                "PROCESSING",
                null,
                null,
                null,
                0,
                24,
                "{}",
                "{}",
                "{}",
                "{}",
                sortNo,
                1
        );
    }

    private WorkflowTransitionRequest transitionRequest(String from, String to) {
        return new WorkflowTransitionRequest(
                from,
                to,
                "APPROVE",
                "通过",
                0,
                1,
                null,
                "{}",
                1,
                10
        );
    }
}
