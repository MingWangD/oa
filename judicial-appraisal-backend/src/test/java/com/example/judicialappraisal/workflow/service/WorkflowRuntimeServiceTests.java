package com.example.judicialappraisal.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.judicialappraisal.caseinfo.entity.CaseInfo;
import com.example.judicialappraisal.caseinfo.mapper.CaseInfoMapper;
import com.example.judicialappraisal.common.enums.CaseStatus;
import com.example.judicialappraisal.common.exception.BusinessException;
import com.example.judicialappraisal.knowledge.service.KnowledgeService;
import com.example.judicialappraisal.workflow.entity.CaseNodeInstance;
import com.example.judicialappraisal.workflow.entity.CaseSubflowInstance;
import com.example.judicialappraisal.workflow.entity.CaseTask;
import com.example.judicialappraisal.workflow.entity.CaseTaskCandidate;
import com.example.judicialappraisal.workflow.entity.CaseWfInstance;
import com.example.judicialappraisal.workflow.entity.WfDefinition;
import com.example.judicialappraisal.workflow.entity.WfNodeDef;
import com.example.judicialappraisal.workflow.entity.WfTransitionDef;
import com.example.judicialappraisal.workflow.dto.WorkflowActionRequest;
import com.example.judicialappraisal.common.enums.ActionCode;
import com.example.judicialappraisal.workflow.design.FormVersion;
import com.example.judicialappraisal.workflow.design.FormVersionMapper;
import com.example.judicialappraisal.workflow.mapper.CaseNodeInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseSubflowInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskCandidateMapper;
import com.example.judicialappraisal.workflow.mapper.CaseTaskMapper;
import com.example.judicialappraisal.workflow.mapper.CaseWfInstanceMapper;
import com.example.judicialappraisal.workflow.mapper.WfDefinitionMapper;
import com.example.judicialappraisal.workflow.mapper.WfNodeDefMapper;
import com.example.judicialappraisal.workflow.mapper.WfTransitionDefMapper;
import com.example.judicialappraisal.organization.entity.SysRole;
import com.example.judicialappraisal.organization.mapper.SysRoleMapper;
import com.example.judicialappraisal.organization.mapper.SysUserRoleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowRuntimeServiceTests {

    private final CaseInfoMapper caseInfoMapper = mock(CaseInfoMapper.class);
    private final CaseWfInstanceMapper caseWfInstanceMapper = mock(CaseWfInstanceMapper.class);
    private final CaseSubflowInstanceMapper caseSubflowInstanceMapper = mock(CaseSubflowInstanceMapper.class);
    private final CaseNodeInstanceMapper caseNodeInstanceMapper = mock(CaseNodeInstanceMapper.class);
    private final CaseTaskMapper caseTaskMapper = mock(CaseTaskMapper.class);
    private final WfDefinitionMapper wfDefinitionMapper = mock(WfDefinitionMapper.class);
    private final WfNodeDefMapper wfNodeDefMapper = mock(WfNodeDefMapper.class);
    private final WfTransitionDefMapper wfTransitionDefMapper = mock(WfTransitionDefMapper.class);
    private final FormVersionMapper formVersionMapper = mock(FormVersionMapper.class);
    private final CaseTaskCandidateMapper caseTaskCandidateMapper = mock(CaseTaskCandidateMapper.class);
    private final SysRoleMapper sysRoleMapper = mock(SysRoleMapper.class);
    private final SysUserRoleMapper sysUserRoleMapper = mock(SysUserRoleMapper.class);
    private final KnowledgeService knowledgeService = mock(KnowledgeService.class);

    private final WorkflowRuntimeService service = new WorkflowRuntimeService(
            caseInfoMapper,
            caseWfInstanceMapper,
            caseSubflowInstanceMapper,
            caseNodeInstanceMapper,
            caseTaskMapper,
            wfDefinitionMapper,
            wfNodeDefMapper,
            wfTransitionDefMapper,
            formVersionMapper,
            caseTaskCandidateMapper,
            sysRoleMapper,
            sysUserRoleMapper,
            knowledgeService,
            new ObjectMapper()
    );

    @Test
    void submitCaseBindsLatestPublishedWorkflowDefinition() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("测试案件");
        caseInfo.setCaseStatus(CaseStatus.DRAFT.name());

        WfDefinition definition = new WfDefinition();
        definition.setId(77L);
        definition.setWfCode("received-entrust");
        definition.setWfName("收到委托书 v2");
        definition.setVersionNo(2);
        definition.setPublishStatus("published");

        WfNodeDef firstNode = new WfNodeDef();
        firstNode.setNodeCode("INIT_FILL");
        firstNode.setNodeName("发起者填写委托信息");
        firstNode.setNodeType("task");
        firstNode.setEnabled(1);

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(null);
        when(wfDefinitionMapper.selectOne(any())).thenReturn(definition);
        when(wfNodeDefMapper.selectOne(any())).thenReturn(firstNode);
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

        service.submitCase(88L, new WorkflowActionRequest(null, ActionCode.SUBMIT, "提交", null, 9L, "管理员", null, null), 9L, "管理员");

        ArgumentCaptor<CaseWfInstance> captor = ArgumentCaptor.forClass(CaseWfInstance.class);
        verify(caseWfInstanceMapper).insert(captor.capture());
        assertThat(captor.getValue().getWfId()).isEqualTo(77L);
        assertThat(captor.getValue().getWfName()).isEqualTo("收到委托书 v2");
        assertThat(caseInfo.getCurrentNodeCode()).isEqualTo("INIT_FILL");
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

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
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

        complete(new WorkflowActionRequest(701L, ActionCode.APPROVE, "通过", null, null, null, null, null));

        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getNodeCode()).isEqualTo("DYNAMIC_REVIEW");
        assertThat(caseInfo.getCurrentNodeCode()).isEqualTo("DYNAMIC_REVIEW");
    }

    @Test
    void completingTaskCreatesCandidateTaskWhenTargetNodeHasRoleRuleEvenIfRequestHasAssignee() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("角色候选测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("CURRENT");
        currentTask.setNodeName("当前节点");
        currentTask.setAssigneeId(9L);
        currentTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setStatus("running");

        WfTransitionDef transition = new WfTransitionDef();
        transition.setToNodeCode("FINANCE_REVIEW");
        transition.setActionCode("APPROVE");

        WfNodeDef targetNode = new WfNodeDef();
        targetNode.setNodeCode("FINANCE_REVIEW");
        targetNode.setNodeName("财务审核");
        targetNode.setNodeType("task");
        targetNode.setTaskType("candidate");
        targetNode.setHandlerRoleRule("FINANCE");
        targetNode.setEnabled(1);

        SysRole financeRole = new SysRole();
        financeRole.setId(22L);
        financeRole.setRoleCode("FINANCE");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(transition));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(targetNode);
        when(sysRoleMapper.selectList(any())).thenReturn(java.util.List.of(financeRole));
        when(sysUserRoleMapper.selectEnabledUserRoleCandidates(java.util.List.of(22L)))
                .thenReturn(java.util.List.of(new SysUserRoleMapper.UserRoleCandidateRow(18L, 22L)));
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

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                9L,
                "当前用户",
                Map.of(),
                null));

        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper).insert(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getTaskType()).isEqualTo("candidate");
        assertThat(taskCaptor.getValue().getAssigneeId()).isNull();

        ArgumentCaptor<CaseTaskCandidate> candidateCaptor = ArgumentCaptor.forClass(CaseTaskCandidate.class);
        verify(caseTaskCandidateMapper).insert(candidateCaptor.capture());
        assertThat(candidateCaptor.getValue().getCandidateUserId()).isEqualTo(18L);
        assertThat(candidateCaptor.getValue().getCandidateRoleId()).isEqualTo(22L);
    }

    @Test
    void manualNextAssigneeMustMatchTargetNodeCandidateRole() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("人工指定测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("CURRENT");
        currentTask.setNodeName("当前节点");
        currentTask.setAssigneeId(9L);
        currentTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setStatus("running");

        WfTransitionDef transition = new WfTransitionDef();
        transition.setToNodeCode("FINANCE_REVIEW");
        transition.setActionCode("APPROVE");

        WfNodeDef targetNode = new WfNodeDef();
        targetNode.setNodeCode("FINANCE_REVIEW");
        targetNode.setNodeName("财务审核");
        targetNode.setNodeType("task");
        targetNode.setHandlerRoleRule("FINANCE");
        targetNode.setAllowManualAssign(1);
        targetNode.setEnabled(1);

        SysRole financeRole = new SysRole();
        financeRole.setId(22L);
        financeRole.setRoleCode("FINANCE");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(transition));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(targetNode);
        when(sysRoleMapper.selectList(any())).thenReturn(java.util.List.of(financeRole));
        when(sysUserRoleMapper.selectEnabledRoleIdsByUserId(9L)).thenReturn(java.util.List.of());
        doAnswer(invocation -> {
            CaseNodeInstance node = invocation.getArgument(0);
            node.setId(602L);
            return 1;
        }).when(caseNodeInstanceMapper).insert(any(CaseNodeInstance.class));

        assertThatThrownBy(() -> complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                null,
                null,
                9L,
                "指定财务",
                Map.of(),
                null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("指定承办人不在目标节点候选角色范围内");
    }

    @Test
    void currentUserCannotCompleteAssignedTaskWhenRequestHasNoAssigneeId() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setAssigneeId(10L);
        currentTask.setStatus("pending");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);

        assertThatThrownBy(() -> complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户不是任务主办人");
    }

    @Test
    void forgedAssigneeIdCannotBypassCurrentUserPermission() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setAssigneeId(10L);
        currentTask.setStatus("pending");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);

        assertThatThrownBy(() -> complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                10L,
                "任务负责人",
                null,
                null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户不是任务主办人");
    }

    @Test
    void currentUserCannotCompleteCandidateTaskOutsideCandidateScope() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setAssigneeId(null);
        currentTask.setStatus("pending");

        CaseTaskCandidate candidate = new CaseTaskCandidate();
        candidate.setTaskId(701L);
        candidate.setCandidateRoleId(22L);

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseTaskCandidateMapper.selectList(any())).thenReturn(java.util.List.of(candidate));
        when(sysUserRoleMapper.selectEnabledRoleIdsByUserId(9L)).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                9L,
                "非候选用户",
                null,
                null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前用户不在任务候选范围内");
    }

    @Test
    void assignedCurrentUserCanCompleteTaskWithoutOperatorInRequest() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("CUSTOM_NODE");
        currentTask.setNodeName("自定义节点");
        currentTask.setAssigneeId(9L);
        currentTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setStatus("running");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of());

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                null,
                null,
                null,
                null));

        assertThat(currentTask.getStatus()).isEqualTo("completed");
    }

    @Test
    void candidateUserCanClaimAndCompleteCandidateTask() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("CANDIDATE_NODE");
        currentTask.setNodeName("候选节点");
        currentTask.setAssigneeId(null);
        currentTask.setStatus("pending");

        CaseTaskCandidate candidate = new CaseTaskCandidate();
        candidate.setTaskId(701L);
        candidate.setCandidateRoleId(22L);

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setStatus("running");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of());
        when(caseTaskCandidateMapper.selectList(any())).thenReturn(java.util.List.of(candidate));
        when(sysUserRoleMapper.selectEnabledRoleIdsByUserId(9L)).thenReturn(java.util.List.of(22L));

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.CLAIM,
                "认领",
                null,
                null,
                null,
                null,
                null));

        assertThat(currentTask.getStatus()).isEqualTo("claimed");
        assertThat(currentTask.getAssigneeId()).isEqualTo(9L);
        assertThat(currentTask.getClaimedBy()).isEqualTo(9L);

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                null,
                null,
                null,
                null));

        assertThat(currentTask.getStatus()).isEqualTo("completed");
    }

    @Test
    void completeTaskRejectsMissingRequiredFieldsFromPublishedFormSchema() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("测试案件");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("INIT_FILL");
        currentTask.setNodeName("发起者填写委托信息");
        currentTask.setAssigneeId(9L);
        currentTask.setStatus("pending");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setStatus("running");

        WfDefinition definition = new WfDefinition();
        definition.setId(77L);
        definition.setFormCode("received-entrust");

        FormVersion formVersion = new FormVersion();
        formVersion.setFormCode("received-entrust");
        formVersion.setStatus("published");
        formVersion.setFieldSchemaJson("""
                [
                  {"field":"caseNo","label":"案件号","type":"text","required":true},
                  {"field":"entrustAccepted","label":"委托审查是否受理","type":"boolean","required":true}
                ]
                """);

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfDefinitionMapper.selectById(77L)).thenReturn(definition);
        when(formVersionMapper.selectOne(any())).thenReturn(formVersion);

        assertThatThrownBy(() -> complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "通过",
                null,
                null,
                null,
                Map.of("entrustAccepted", true),
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必填字段未填写：案件号");
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

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
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

        complete(new WorkflowActionRequest(
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

    @Test
    void configuredTransitionCanLaunchSubflowAndAttachTaskToIt() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("收到委托书测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("PROJECT_DECISION");
        currentTask.setNodeName("项目负责人决策");
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

        WfTransitionDef transition = new WfTransitionDef();
        transition.setToNodeCode("PRELIMINARY_SURVEY");
        transition.setActionCode("APPROVE");
        transition.setActionName("进入初步勘验");
        transition.setConditionExpression("form.preliminarySurveyRequired == true");
        transition.setTransitionConfigJson("""
                {"archiveOnLeave":1,"launchSubflow":true,"subflowCode":"preliminary-survey","reason":"需要初步勘验"}
                """);

        WfNodeDef targetNode = new WfNodeDef();
        targetNode.setNodeCode("PRELIMINARY_SURVEY");
        targetNode.setNodeName("进入初步勘验");
        targetNode.setNodeType("task");
        targetNode.setTaskType("single");
        targetNode.setEnabled(1);

        WfDefinition subflowDefinition = new WfDefinition();
        subflowDefinition.setId(177L);
        subflowDefinition.setWfCode("preliminary-survey");
        subflowDefinition.setWfName("初步勘验");
        subflowDefinition.setPublishStatus("published");

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(transition));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(targetNode);
        when(caseSubflowInstanceMapper.selectOne(any())).thenReturn(null);
        when(wfDefinitionMapper.selectOne(any())).thenReturn(subflowDefinition);
        doAnswer(invocation -> {
            CaseSubflowInstance subflow = invocation.getArgument(0);
            subflow.setId(801L);
            return 1;
        }).when(caseSubflowInstanceMapper).insert(any(CaseSubflowInstance.class));
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

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "进入初勘",
                null,
                null,
                null,
                Map.of("preliminarySurveyRequired", true),
                null));

        ArgumentCaptor<CaseSubflowInstance> subflowCaptor = ArgumentCaptor.forClass(CaseSubflowInstance.class);
        verify(caseSubflowInstanceMapper).insert(subflowCaptor.capture());
        assertThat(subflowCaptor.getValue().getWfCode()).isEqualTo("preliminary-survey");
        assertThat(subflowCaptor.getValue().getParentTaskId()).isEqualTo(702L);
        assertThat(subflowCaptor.getValue().getParentNodeCode()).isEqualTo("PRELIMINARY_SURVEY");

        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper, org.mockito.Mockito.times(2)).insert(taskCaptor.capture());
        java.util.List<CaseTask> createdTasks = taskCaptor.getAllValues();
        assertThat(createdTasks.get(0).getNodeCode()).isEqualTo("PRELIMINARY_SURVEY");
        assertThat(createdTasks.get(0).getStatus()).isEqualTo("subflow_running");
        assertThat(createdTasks.get(1).getSubflowInstanceId()).isEqualTo(801L);
        assertThat(createdTasks.get(1).getNodeCode()).isEqualTo("PRELIMINARY_SURVEY");
    }

    @Test
    void completingSubflowBranchEndWritesBackToRemainingParentTaskInsteadOfCompletingCase() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("收到委托书测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(702L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setSubflowInstanceId(801L);
        currentTask.setNodeInstanceId(602L);
        currentTask.setNodeCode("PRELIMINARY_SURVEY");
        currentTask.setNodeName("进入初步勘验");
        currentTask.setAssigneeId(9L);
        currentTask.setAssigneeName("管理员");
        currentTask.setStatus("pending");

        CaseTask remainingTask = new CaseTask();
        remainingTask.setId(703L);
        remainingTask.setCaseId(88L);
        remainingTask.setWfInstanceId(501L);
        remainingTask.setNodeInstanceId(603L);
        remainingTask.setNodeCode("ASSISTANT_NOTICE");
        remainingTask.setNodeName("告知项目辅助人");
        remainingTask.setAssigneeId(18L);
        remainingTask.setAssigneeName("辅助人");
        remainingTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(602L);
        currentNode.setCaseId(88L);
        currentNode.setSubflowInstanceId(801L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setWfCode("received-entrust");
        wfInstance.setWfName("收到委托书");
        wfInstance.setStatus("running");

        CaseSubflowInstance subflowInstance = new CaseSubflowInstance();
        subflowInstance.setId(801L);
        subflowInstance.setCaseId(88L);
        subflowInstance.setWfId(177L);
        subflowInstance.setStatus("running");

        WfTransitionDef transition = new WfTransitionDef();
        transition.setToNodeCode("END");
        transition.setActionCode("COMPLETE");
        transition.setActionName("初勘结束");

        WfNodeDef endNode = new WfNodeDef();
        endNode.setNodeCode("END");
        endNode.setNodeName("流程结束");
        endNode.setNodeType("end");
        endNode.setEnabled(1);

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(702L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(602L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);
        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(transition));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(endNode);
        when(caseSubflowInstanceMapper.selectById(801L)).thenReturn(subflowInstance);
        when(caseTaskMapper.selectOne(any())).thenReturn(remainingTask);

        complete(new WorkflowActionRequest(
                702L,
                ActionCode.COMPLETE,
                "初勘结束",
                null,
                null,
                null,
                null,
                null));

        ArgumentCaptor<CaseSubflowInstance> subflowCaptor = ArgumentCaptor.forClass(CaseSubflowInstance.class);
        verify(caseSubflowInstanceMapper).updateById(subflowCaptor.capture());
        assertThat(subflowCaptor.getValue().getStatus()).isEqualTo("completed");

        assertThat(caseInfo.getCaseStatus()).isEqualTo(CaseStatus.PROCESSING.name());
        assertThat(caseInfo.getCurrentNodeCode()).isEqualTo("ASSISTANT_NOTICE");
        assertThat(caseInfo.getCurrentNodeName()).isEqualTo("告知项目辅助人");
        assertThat(caseInfo.getCurrentHandlerId()).isEqualTo(18L);
        assertThat(caseInfo.getCompletedTime()).isNull();

        ArgumentCaptor<CaseWfInstance> wfCaptor = ArgumentCaptor.forClass(CaseWfInstance.class);
        verify(caseWfInstanceMapper).updateById(wfCaptor.capture());
        assertThat(wfCaptor.getValue().getStatus()).isEqualTo("running");
        assertThat(wfCaptor.getValue().getCurrentNodeCode()).isEqualTo("ASSISTANT_NOTICE");
    }

    @Test
    void parallelGatewaySplitsExecutionAndInclusiveGatewayWaitsForCompletion() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("并行网关测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("PROJECT_SUPPLEMENT");
        currentTask.setNodeName("项目负责人补充承诺书与复核意见");
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
        wfInstance.setWfCode("issue-opinion");
        wfInstance.setWfName("出具鉴定意见书");
        wfInstance.setStatus("running");

        WfTransitionDef transitionToGateway = new WfTransitionDef();
        transitionToGateway.setToNodeCode("PARALLEL_GATEWAY_SPLIT");
        transitionToGateway.setActionCode("APPROVE");
        transitionToGateway.setActionName("进入并行");

        WfNodeDef gatewaySplitNode = new WfNodeDef();
        gatewaySplitNode.setNodeCode("PARALLEL_GATEWAY_SPLIT");
        gatewaySplitNode.setNodeName("并行分支");
        gatewaySplitNode.setNodeType("gateway");
        gatewaySplitNode.setTaskType("parallel");
        gatewaySplitNode.setEnabled(1);

        WfTransitionDef splitToA = new WfTransitionDef();
        splitToA.setFromNodeCode("PARALLEL_GATEWAY_SPLIT");
        splitToA.setToNodeCode("TASK_A");
        splitToA.setActionCode("APPROVE");
        splitToA.setConditionExpression("true");

        WfTransitionDef splitToB = new WfTransitionDef();
        splitToB.setFromNodeCode("PARALLEL_GATEWAY_SPLIT");
        splitToB.setToNodeCode("TASK_B");
        splitToB.setActionCode("APPROVE");
        splitToB.setConditionExpression("true");

        WfNodeDef taskANode = new WfNodeDef();
        taskANode.setNodeCode("TASK_A");
        taskANode.setNodeName("任务A");
        taskANode.setNodeType("task");
        taskANode.setTaskType("single");
        taskANode.setEnabled(1);

        WfNodeDef taskBNode = new WfNodeDef();
        taskBNode.setNodeCode("TASK_B");
        taskBNode.setNodeName("任务B");
        taskBNode.setNodeType("task");
        taskBNode.setTaskType("single");
        taskBNode.setEnabled(1);

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);

        when(wfTransitionDefMapper.selectList(any()))
            .thenReturn(java.util.List.of(transitionToGateway))
            .thenReturn(java.util.List.of(splitToA, splitToB));

        when(wfNodeDefMapper.selectOne(any()))
            .thenReturn(gatewaySplitNode)
            .thenReturn(taskANode)
            .thenReturn(taskBNode);

        // Mock counting active tasks to 0 (for gateway check, though parallel split doesn't wait)
        when(caseTaskMapper.selectCount(any())).thenReturn(0L);

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "同意",
                null,
                null,
                null,
                Map.of(),
                null));

        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper, org.mockito.Mockito.times(2)).insert(taskCaptor.capture());
        
        java.util.List<CaseTask> createdTasks = taskCaptor.getAllValues();
        assertThat(createdTasks).hasSize(2);
        assertThat(createdTasks).extracting(CaseTask::getNodeCode).containsExactlyInAnyOrder("TASK_A", "TASK_B");
    }

    @Test
    void inclusiveGatewayWaitsForOtherActiveTasksBeforeProceeding() {
        CaseInfo caseInfo = new CaseInfo();
        caseInfo.setId(88L);
        caseInfo.setCaseTitle("汇聚网关测试");
        caseInfo.setCaseStatus(CaseStatus.PROCESSING.name());

        CaseTask currentTask = new CaseTask();
        currentTask.setId(701L);
        currentTask.setCaseId(88L);
        currentTask.setWfInstanceId(501L);
        currentTask.setNodeInstanceId(601L);
        currentTask.setNodeCode("SEALED_UPLOAD");
        currentTask.setNodeName("档案管理员回传盖章件");
        currentTask.setAssigneeId(9L);
        currentTask.setStatus("pending");

        CaseNodeInstance currentNode = new CaseNodeInstance();
        currentNode.setId(601L);
        currentNode.setCaseId(88L);
        currentNode.setStatus("running");

        CaseWfInstance wfInstance = new CaseWfInstance();
        wfInstance.setId(501L);
        wfInstance.setCaseId(88L);
        wfInstance.setWfId(77L);
        wfInstance.setStatus("running");

        WfTransitionDef transitionToGateway = new WfTransitionDef();
        transitionToGateway.setToNodeCode("PARALLEL_GATEWAY_JOIN");
        transitionToGateway.setActionCode("APPROVE");

        WfNodeDef gatewayJoinNode = new WfNodeDef();
        gatewayJoinNode.setNodeCode("PARALLEL_GATEWAY_JOIN");
        gatewayJoinNode.setNodeName("并行汇聚");
        gatewayJoinNode.setNodeType("gateway");
        gatewayJoinNode.setTaskType("inclusive");
        gatewayJoinNode.setEnabled(1);

        when(caseInfoMapper.selectRawById(88L)).thenReturn(caseInfo);
        when(caseTaskMapper.selectById(701L)).thenReturn(currentTask);
        when(caseNodeInstanceMapper.selectById(601L)).thenReturn(currentNode);
        when(caseWfInstanceMapper.selectOne(any())).thenReturn(wfInstance);

        when(wfTransitionDefMapper.selectList(any())).thenReturn(java.util.List.of(transitionToGateway));
        when(wfNodeDefMapper.selectOne(any())).thenReturn(gatewayJoinNode);

        // Mock counting active tasks to 1 (meaning another task like FINANCE_INVOICE is still running)
        when(caseTaskMapper.selectCount(any())).thenReturn(1L);

        complete(new WorkflowActionRequest(
                701L,
                ActionCode.APPROVE,
                "同意",
                null,
                null,
                null,
                Map.of(),
                null));

        // It should NOT create any new tasks because it's waiting
        verify(caseTaskMapper, org.mockito.Mockito.never()).insert(any(CaseTask.class));
        // The task itself should be completed
        ArgumentCaptor<CaseTask> taskCaptor = ArgumentCaptor.forClass(CaseTask.class);
        verify(caseTaskMapper).updateById(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getStatus()).isEqualTo("completed");
    }

    private void complete(WorkflowActionRequest request) {
        service.completeTask(88L, request, 9L, "当前用户");
    }
}
