package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.knowledge.service.KnowledgeService;
import com.example.judicialappraisal.workflow.entity.CaseNodeInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.workflow.mapper.CaseNodeInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowRuntimeServiceTests {

    private final CaseInfoMapper caseInfoMapper = mock(CaseInfoMapper.class);
    private final CaseWfInstanceMapper caseWfInstanceMapper = mock(CaseWfInstanceMapper.class);
    private final CaseNodeInstanceMapper caseNodeInstanceMapper = mock(CaseNodeInstanceMapper.class);
    private final CaseTaskMapper caseTaskMapper = mock(CaseTaskMapper.class);
    private final WfDefinitionMapper wfDefinitionMapper = mock(WfDefinitionMapper.class);
    private final WfNodeDefMapper wfNodeDefMapper = mock(WfNodeDefMapper.class);
    private final WfTransitionDefMapper wfTransitionDefMapper = mock(WfTransitionDefMapper.class);
    private final CaseTaskCandidateMapper caseTaskCandidateMapper = mock(CaseTaskCandidateMapper.class);
    private final SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);

    private final WorkflowRuntimeService service = new WorkflowRuntimeService(
            caseInfoMapper,
            caseWfInstanceMapper,
            caseNodeInstanceMapper,
            caseTaskMapper,
            wfDefinitionMapper,
            wfNodeDefMapper,
            wfTransitionDefMapper,
            caseTaskCandidateMapper,
            sysRoleMapper,
            sysUserRoleMapper,
            knowledgeService
    );

    @Test
    void submitCaseBindsLatestPublishedWorkflowDefinition() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("测试案件");
        caseInfo.setCaseStatus(CaseStatus.DRAFT.name());

        WfDefinition definition = new WfDefinition();
        definition.setId(77L);
        definition.setWfCode("JUDICIAL_MAIN");
        definition.setWfName("司法鉴定主流程 v2");
        definition.setVersionNo(2);
        definition.setPublishStatus("published");

        when(caseInfoMapper.selectById(88L)).thenReturn(caseInfo);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(null);
        when(wfDefinitionMapper.selectOne(any())).thenReturn(definition);
        doAnswer(invocation -> {
            CaseWfInstance instance = invocation.getArgument(0);
            instance.setId(501L);
            return 1;
        }).when(caseWfInstanceMapper).insert(any(CaseWfInstance.class));
        doAnswer(invocation -> {
            CaseNodeInstance node = invocation.getArgument(0);
            node.setId(601L);
            return 1;
        }).when(caseNodeInstanceMapper).insert(any(CaseNodeInstance.class));
        doAnswer(invocation -> {
            CaseTask task = invocation.getArgument(0);
            task.setId(701L);
            return 1;
        }).when(caseTaskMapper).insert(any(CaseTask.class));

        service.submitCase(88L, 9L, "管理员", "提交");

        ArgumentCaptor<CaseWfInstance> captor = ArgumentCaptor.forClass(CaseWfInstance.class);
        verify(caseWfInstanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getWfId()).isEqualTo(77L);
        assertThat(captor.getValue().getWfName()).isEqualTo("司法鉴定主流程 v2");
    }

    @Test
    void completeTaskUsesConfiguredTransitionBeforeHardcodedFallback() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("测试案件");
        caseInfo.setCaseStatus(CaseStatus.TO_ACCEPT.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("ACCEPT_REVIEW");
        currentTask.setNodeName("受理审查");
        currentTask.setAssigneeId(9L);
        currentTask.setAssigneeName("管理员");
        currentTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setWfCode("JUDICIAL_MAIN");
        wfInstance.setWfName("司法鉴定主流程 v2");
        wfInstance.setStatus("running");

        WfTransitionDef transition = new WfTransitionDef();
        transition.setToNodeCode("DYNAMIC_REVIEW");
        transition.setActionCode("APPROVE");
        transition.setActionName("动态审批");

        WfNodeDef targetNode = new WfNodeDef();
        targetNode.setNodeCode("DYNAMIC_REVIEW");
        targetNode.setNodeName("动态审批");
        targetNode.setNodeType("review");
        targetNode.setTaskType("single");
        targetNode.setEnabled(1);

        when(caseInfoMapper.selectById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(transition));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(targetNode);
        doAnswer(invocation -> {
            CaseNodeInstance node = invocation.getArgument(0);
            node.setId(602L);
            return 1;
        }).when(caseNodeInstanceMapper).insert(any(CaseNodeInstance.class));
        doAnswer(invocation -> {
            CaseTask task = invocation.getArgument(0);
            task.setId(702L);
            return 1;
        }).when(caseTaskMapper).insert(any(CaseTask.class));

        service.completeTask(88L, new WorkflowActionRequest(701L, ActionCode.APPROVE, "通过", null, null, null, null, null));

        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getNodeCode()).isEqualTo("DYNAMIC_REVIEW");
        assertThat(caseInfo.getCurrentNodeCode()).isEqualTo("DYNAMIC_REVIEW");
    }

    @Test
    void completeTaskOnlyAdvancesTransitionsWhoseConditionsMatchFormData() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("收到委托书测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("DEPT_REVIEW");
        currentTask.setNodeName("部门负责人审阅");
        currentTask.setAssigneeId(9L);
        currentTask.setAssigneeName("管理员");
        currentTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setWfCode("received-entrust");
        wfInstance.setWfName("收到委托书");
        wfInstance.setStatus("running");

        WfTransitionDef acceptedTransition = new WfTransitionDef();
        acceptedTransition.setToNodeCode("PROJECT_DECISION");
        acceptedTransition.setActionCode("APPROVE");
        acceptedTransition.setActionName("受理");
        acceptedTransition.setConditionExpression("form.entrustAccepted == true");

        WfTransitionDef rejectedTransition = new WfTransitionDef();
        rejectedTransition.setToNodeCode("REJECT_ACCEPTANCE");
        rejectedTransition.setActionCode("APPROVE");
        rejectedTransition.setActionName("不予受理");
        rejectedTransition.setConditionExpression("form.entrustAccepted == false");

        WfNodeDef rejectNode = new WfNodeDef();
        rejectNode.setNodeCode("REJECT_ACCEPTANCE");
        rejectNode.setNodeName("进入不予受理");
        rejectNode.setNodeType("task");
        rejectNode.setTaskType("single");
        rejectNode.setEnabled(1);

        when(caseInfoMapper.selectById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(acceptedTransition, rejectedTransition));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(rejectNode);
        doAnswer(invocation -> {
            CaseNodeInstance node = invocation.getArgument(0);
            node.setId(602L);
            return 1;
        }).when(caseNodeInstanceMapper).insert(any(CaseNodeInstance.class));
        doAnswer(invocation -> {
            CaseTask task = invocation.getArgument(0);
            task.setId(702L);
            return 1;
        }).when(caseTaskMapper).insert(any(CaseTask.class));

        service.completeTask(88L, new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "不受理",
                null,
                null,
                null,
                Map.of("entrustAccepted", false),
                null));

        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getNodeCode()).isEqualTo("REJECT_ACCEPTANCE");
        assertThat(caseInfo.getCurrentNodeCode()).isEqualTo("REJECT_ACCEPTANCE");
    }
}
