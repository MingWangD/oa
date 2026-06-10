# 司法鉴定流程系统核心状态机设计与伪代码

> 本文档是 `judicial-appraisal-workflow-design.md` 的后续细化，重点描述第一期司法鉴定流程系统的状态机、动作处理模型、任务生成规则和核心伪代码。

---

## 1. 设计目标

第一期流程系统不做通用 BPM 引擎，而是实现一个面向司法鉴定业务的受控状态机。

核心目标：

1. 保证案件流转动作合法。
2. 保证每个节点的办理人、任务、意见、日志可追溯。
3. 支持主流程 + 少量受控子流程。
4. 支持撤回、重开、终止、退回等逆向动作。
5. 支持单人任务、候选任务、并行任务。
6. 保持后续可升级到更强流程引擎的可能性。

---

## 2. 核心概念

## 2.1 案件状态 CaseStatus

案件状态用于业务展示、查询、统计。

```text
DRAFT                  草稿
TO_ACCEPT              待受理
ACCEPT_REVIEWING       受理审核中
REJECTED_ACCEPTANCE    不受理
CORRECTION_PENDING     待补正 / 待补充
PROCESSING             办理中
REVIEWING              复核 / 审核中
DOC_ISSUING            文书签发中
COMPLETED              已办结
ARCHIVED               已归档
TERMINATED             已终止
```

## 2.2 流程实例状态 WorkflowInstanceStatus

流程实例状态用于控制主流程或子流程运行。

```text
RUNNING       运行中
COMPLETED     已完成
TERMINATED    已终止
WITHDRAWN     已撤回
CANCELLED     已取消
```

## 2.3 节点实例状态 NodeInstanceStatus

```text
PENDING       待开始
PROCESSING    办理中
COMPLETED     已完成
RETURNED      已退回
WITHDRAWN     已撤回
TERMINATED    已终止
CANCELLED     已取消
```

## 2.4 任务状态 TaskStatus

```text
PENDING       待处理
CLAIMED       已认领
PROCESSING    办理中
COMPLETED     已完成
CANCELLED     已取消
```

## 2.5 任务类型 TaskType

```text
SINGLE        单人任务
CANDIDATE     候选任务
PARALLEL      并行任务
```

## 2.6 流程动作 ActionCode

### 推进类动作

```text
SUBMIT        提交
APPROVE       通过
PASS          通过
COMPLETE      完成
ISSUE         签发
ARCHIVE       归档
```

### 控制类动作

```text
RETURN        退回
WITHDRAW      撤回
TERMINATE     终止
REOPEN        重开
```

### 协同类动作

```text
ASSIGN        指派
CLAIM         认领
TRANSFER      转办，第一期可后置
ADD_SIGN      加签，第一期可后置
```

### 子流程动作

```text
START_CORRECTION             发起补正
START_MATERIAL_SUPPLEMENT    发起补充材料
START_DOC_ISSUE              发起文书签发
START_REWORK                 发起退回重办
```

---

## 3. 状态机总入口

所有流程动作统一进入一个服务方法。

```java
ProcessResult processAction(Long caseId, Long taskId, String actionCode, ActionPayload payload, CurrentUser user)
```

该方法负责：

1. 加载案件。
2. 加载任务。
3. 加载流程实例。
4. 加载当前节点实例。
5. 校验用户权限。
6. 校验任务状态。
7. 校验动作合法性。
8. 执行动作处理器。
9. 生成或取消任务。
10. 更新案件摘要状态。
11. 写入日志。
12. 返回最新案件状态和下一步信息。

---

## 4. 总体伪代码

```java
@Transactional
public ProcessResult processAction(Long caseId, Long taskId, ActionCode actionCode, ActionPayload payload, CurrentUser user) {
    CaseInfo caseInfo = caseRepository.getById(caseId);
    assertCaseExists(caseInfo);

    if (caseInfo.isArchived()) {
        throw new BusinessException("已归档案件不可办理");
    }

    if (caseInfo.isTerminated() && actionCode != ActionCode.REOPEN) {
        throw new BusinessException("已终止案件不可继续流转");
    }

    CaseTask task = null;
    if (taskId != null) {
        task = taskRepository.getById(taskId);
        assertTaskBelongsToCase(task, caseId);
    }

    CaseWorkflowInstance wfInstance = workflowInstanceRepository.getRunningInstance(caseId);
    CaseNodeInstance nodeInstance = resolveCurrentNodeInstance(caseInfo, task, wfInstance);

    WorkflowNodeDef nodeDef = nodeDefRepository.getByCode(wfInstance.getWfId(), nodeInstance.getNodeCode());

    permissionService.checkActionPermission(user, caseInfo, task, nodeDef, actionCode);
    taskValidator.validateTaskStatus(task, actionCode, user);
    actionValidator.validateActionAllowed(caseInfo, wfInstance, nodeInstance, nodeDef, actionCode, payload);

    ActionHandler handler = actionHandlerFactory.getHandler(actionCode);
    ProcessContext context = new ProcessContext(caseInfo, wfInstance, nodeInstance, task, nodeDef, payload, user);

    ProcessResult result = handler.handle(context);

    auditService.writeActionLog(context, result);
    auditService.writeStatusLogIfChanged(context, result);

    return result;
}
```

---

## 5. 动作校验规则

## 5.1 通用校验

```java
void validateActionAllowed(CaseInfo caseInfo, CaseWorkflowInstance wfInstance, CaseNodeInstance nodeInstance, WorkflowNodeDef nodeDef, ActionCode actionCode, ActionPayload payload) {
    if (!nodeDef.supports(actionCode)) {
        throw new BusinessException("当前节点不支持该操作");
    }

    if (requiresReason(actionCode) && isBlank(payload.getReason())) {
        throw new BusinessException("该操作必须填写原因");
    }

    if (actionCode == RETURN) {
        validateReturnTarget(nodeDef, payload.getTargetNodeCode());
    }

    if (actionCode == TERMINATE) {
        validateTerminateAllowed(caseInfo, nodeDef);
    }

    if (actionCode == REOPEN) {
        validateReopenAllowed(caseInfo);
    }

    if (actionCode == WITHDRAW) {
        validateWithdrawAllowed(caseInfo, nodeInstance, payload);
    }
}
```

## 5.2 必须填写原因的动作

```text
RETURN
WITHDRAW
TERMINATE
REOPEN
START_CORRECTION
START_MATERIAL_SUPPLEMENT
START_REWORK
```

---

## 6. 推进类动作处理

推进类动作包括：

- SUBMIT
- APPROVE
- PASS
- COMPLETE
- ISSUE

## 6.1 处理逻辑

```java
class ApproveActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        completeCurrentTask(ctx);
        appendOpinion(ctx);

        if (!isNodeFinished(ctx)) {
            return ProcessResult.waitingForOtherTasks(ctx);
        }

        completeCurrentNode(ctx);

        WorkflowTransitionDef transition = transitionService.resolveTransition(
            ctx.getWorkflowInstance(),
            ctx.getNodeInstance().getNodeCode(),
            ctx.getActionCode(),
            ctx.getPayload()
        );

        if (transition.isEnd()) {
            completeWorkflow(ctx);
            updateCaseStatusByEndNode(ctx);
            return ProcessResult.completed(ctx);
        }

        CaseNodeInstance nextNode = createNextNode(ctx, transition.getToNodeCode());
        List<CaseTask> nextTasks = taskFactory.createTasks(ctx.getCaseInfo(), ctx.getWorkflowInstance(), nextNode);

        updateCaseCurrentSnapshot(ctx.getCaseInfo(), nextNode, nextTasks);

        return ProcessResult.movedTo(nextNode, nextTasks);
    }
}
```

## 6.2 节点完成判断

```java
boolean isNodeFinished(ProcessContext ctx) {
    List<CaseTask> activeTasks = taskRepository.listByNodeInstance(ctx.getNodeInstance().getId());

    if (ctx.getNodeDef().getHandlerMode() == SINGLE) {
        return allTasksCompleted(activeTasks);
    }

    if (ctx.getNodeDef().getHandlerMode() == CANDIDATE) {
        return anyTaskCompleted(activeTasks);
    }

    if (ctx.getNodeDef().getHandlerMode() == PARALLEL) {
        ParallelRule rule = ctx.getNodeDef().getParallelRule();
        if (rule == ALL_REQUIRED) {
            return allTasksCompleted(activeTasks);
        }
        if (rule == PRIMARY_WITH_ASSISTANTS) {
            return primaryTaskCompleted(activeTasks);
        }
    }

    return false;
}
```

---

## 7. 提交案件 SUBMIT

## 7.1 规则

- 只有草稿案件允许提交。
- 提交时生成案件编号。
- 提交后创建主流程实例。
- 创建第一个节点实例。
- 生成第一个待办任务。
- 案件状态变为 `TO_ACCEPT` 或 `ACCEPT_REVIEWING`。

## 7.2 伪代码

```java
class SubmitActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        CaseInfo caseInfo = ctx.getCaseInfo();

        if (caseInfo.getCurrentStatus() != DRAFT) {
            throw new BusinessException("只有草稿案件可以提交");
        }

        if (isBlank(caseInfo.getCaseNo())) {
            caseInfo.setCaseNo(caseNoGenerator.generate());
        }

        WorkflowDef wfDef = workflowDefRepository.getEnabledByCode("JUDICIAL_APPRAISAL_MAIN");
        CaseWorkflowInstance wfInstance = workflowInstanceFactory.createMainInstance(caseInfo, wfDef);

        WorkflowNodeDef startNodeDef = nodeDefRepository.getStartNode(wfDef.getId());
        CaseNodeInstance startNode = nodeInstanceFactory.create(wfInstance, startNodeDef);

        List<CaseTask> tasks = taskFactory.createTasks(caseInfo, wfInstance, startNode);

        caseInfo.setCurrentStatus(TO_ACCEPT);
        caseInfo.setCurrentNodeCode(startNode.getNodeCode());
        caseInfo.setCurrentNodeName(startNode.getNodeName());
        caseInfo.updateCurrentHandlerFromTasks(tasks);

        caseRepository.update(caseInfo);

        return ProcessResult.started(caseInfo, wfInstance, startNode, tasks);
    }
}
```

---

## 8. 退回 RETURN

## 8.1 规则

- 不能任意退回。
- 只能退回到节点规则允许的目标节点。
- 退回必须填写原因。
- 退回后当前节点未完成任务全部取消。
- 创建目标节点的新节点实例和任务。
- 案件状态按目标节点重算。

## 8.2 伪代码

```java
class ReturnActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        String targetNodeCode = ctx.getPayload().getTargetNodeCode();
        String reason = ctx.getPayload().getReason();

        validateReturnTarget(ctx.getNodeDef(), targetNodeCode);

        cancelActiveTasks(ctx.getNodeInstance());
        markNodeReturned(ctx.getNodeInstance(), reason);
        appendOpinion(ctx);

        WorkflowNodeDef targetNodeDef = nodeDefRepository.getByCode(ctx.getWorkflowInstance().getWfId(), targetNodeCode);
        CaseNodeInstance targetNode = nodeInstanceFactory.create(ctx.getWorkflowInstance(), targetNodeDef);
        List<CaseTask> newTasks = taskFactory.createTasks(ctx.getCaseInfo(), ctx.getWorkflowInstance(), targetNode);

        CaseStatus newStatus = caseStatusResolver.resolveByNode(targetNodeDef);
        updateCaseSnapshot(ctx.getCaseInfo(), newStatus, targetNode, newTasks);

        return ProcessResult.returned(targetNode, newTasks);
    }
}
```

---

## 9. 撤回 WITHDRAW

## 9.1 规则

- 撤回允许，但必须受控。
- 只能撤回自己发起或自己有权限撤回的流转。
- 如果下一节点已经完成，原则上不允许撤回。
- 撤回必须填写原因。
- 撤回后取消后续未完成任务。
- 恢复到上一合法节点。

## 9.2 伪代码

```java
class WithdrawActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        String reason = ctx.getPayload().getReason();

        CaseNodeInstance currentNode = ctx.getNodeInstance();
        CaseNodeInstance previousNode = nodeInstanceRepository.findPreviousCompletedNode(ctx.getWorkflowInstance().getId(), currentNode.getId());

        if (previousNode == null) {
            throw new BusinessException("没有可撤回的上一节点");
        }

        if (hasCompletedTaskAfter(previousNode)) {
            throw new BusinessException("后续节点已完成办理，不允许撤回");
        }

        cancelActiveTasks(currentNode);
        markNodeWithdrawn(currentNode, reason);

        WorkflowNodeDef previousNodeDef = nodeDefRepository.getByCode(ctx.getWorkflowInstance().getWfId(), previousNode.getNodeCode());
        CaseNodeInstance restoredNode = nodeInstanceFactory.reopenFrom(previousNode, previousNodeDef);
        List<CaseTask> tasks = taskFactory.createTasks(ctx.getCaseInfo(), ctx.getWorkflowInstance(), restoredNode);

        updateCaseSnapshot(ctx.getCaseInfo(), caseStatusResolver.resolveByNode(previousNodeDef), restoredNode, tasks);
        withdrawLogRepository.insert(ctx, previousNode, restoredNode, reason);

        return ProcessResult.withdrawn(restoredNode, tasks);
    }
}
```

---

## 10. 终止 TERMINATE

## 10.1 规则

- 终止必须填写原因。
- 终止需要权限控制。
- 终止后案件不可继续流转。
- 终止后重新办理必须新建案件。
- 终止时取消所有未完成任务和运行中子流程。

## 10.2 伪代码

```java
class TerminateActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        String reason = ctx.getPayload().getReason();

        permissionService.checkTerminatePermission(ctx.getUser(), ctx.getCaseInfo());

        cancelAllActiveTasks(ctx.getCaseInfo().getId());
        terminateRunningSubflows(ctx.getCaseInfo().getId());
        terminateWorkflowInstance(ctx.getWorkflowInstance(), reason);
        terminateCurrentNode(ctx.getNodeInstance(), reason);

        CaseInfo caseInfo = ctx.getCaseInfo();
        caseInfo.setCurrentStatus(TERMINATED);
        caseInfo.setTerminatedAt(now());
        caseInfo.clearCurrentHandler();
        caseRepository.update(caseInfo);

        return ProcessResult.terminated(caseInfo);
    }
}
```

---

## 11. 重开 REOPEN

## 11.1 规则

- 重开允许。
- 重开必须填写原因。
- 重开不清空历史。
- 重开会创建新的流程轮次或从指定节点重新进入。
- 已归档案件是否允许重开需要业务进一步确认；第一期建议默认不允许归档后重开。

## 11.2 伪代码

```java
class ReopenActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        CaseInfo caseInfo = ctx.getCaseInfo();
        String reason = ctx.getPayload().getReason();

        if (!canReopen(caseInfo)) {
            throw new BusinessException("当前案件状态不允许重开");
        }

        caseInfo.setReopenCount(caseInfo.getReopenCount() + 1);
        caseInfo.setCurrentStatus(PROCESSING);

        WorkflowDef wfDef = workflowDefRepository.getEnabledByCode("JUDICIAL_APPRAISAL_MAIN");
        CaseWorkflowInstance newInstance = workflowInstanceFactory.createReopenInstance(caseInfo, wfDef, caseInfo.getReopenCount());

        WorkflowNodeDef reopenNodeDef = resolveReopenNode(ctx.getPayload(), wfDef);
        CaseNodeInstance nodeInstance = nodeInstanceFactory.create(newInstance, reopenNodeDef);
        List<CaseTask> tasks = taskFactory.createTasks(caseInfo, newInstance, nodeInstance);

        updateCaseSnapshot(caseInfo, caseStatusResolver.resolveByNode(reopenNodeDef), nodeInstance, tasks);
        reopenLogRepository.insert(caseInfo, reason, ctx.getUser());

        return ProcessResult.reopened(caseInfo, newInstance, nodeInstance, tasks);
    }
}
```

---

## 12. 指派 ASSIGN

## 12.1 规则

- 只有允许人工指派的节点可以指派。
- 指派人必须有权限。
- 被指派人必须属于合法部门/岗位/角色范围。
- 指派后生成或更新任务。

## 12.2 伪代码

```java
class AssignActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        Long assigneeUserId = ctx.getPayload().getAssigneeUserId();

        if (!ctx.getNodeDef().isAllowManualAssign()) {
            throw new BusinessException("当前节点不允许人工指派");
        }

        User assignee = userRepository.getById(assigneeUserId);
        handlerRuleService.validateAssignee(ctx.getNodeDef(), assignee);

        CaseTask task = taskFactory.createSingleTask(
            ctx.getCaseInfo(),
            ctx.getWorkflowInstance(),
            ctx.getNodeInstance(),
            assignee
        );

        updateCaseCurrentHandler(ctx.getCaseInfo(), assignee);

        return ProcessResult.assigned(task);
    }
}
```

---

## 13. 认领 CLAIM

## 13.1 规则

- 只有候选任务可以认领。
- 当前用户必须在候选人范围内。
- 一旦认领，其他候选人状态改为 cancelled。
- 任务状态变为 claimed 或 processing。

## 13.2 伪代码

```java
class ClaimActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        CaseTask task = ctx.getTask();

        if (task.getTaskType() != CANDIDATE) {
            throw new BusinessException("当前任务不是候选任务");
        }

        if (!candidateRepository.isCandidate(task.getId(), ctx.getUser().getId())) {
            throw new BusinessException("当前用户不是该任务候选人");
        }

        task.setTaskStatus(CLAIMED);
        task.setClaimedBy(ctx.getUser().getId());
        task.setClaimedAt(now());
        taskRepository.update(task);

        candidateRepository.markClaimed(task.getId(), ctx.getUser().getId());
        candidateRepository.cancelOthers(task.getId(), ctx.getUser().getId());

        updateCaseCurrentHandler(ctx.getCaseInfo(), ctx.getUser());

        return ProcessResult.claimed(task);
    }
}
```

---

## 14. 发起补正子流程 START_CORRECTION

## 14.1 规则

- 发起补正必须填写原因。
- 补正子流程运行期间，主流程可以挂起或停留在等待节点。
- 补正完成后回到主流程等待点。
- 多次补正需要保留轮次记录。

## 14.2 伪代码

```java
class StartCorrectionActionHandler implements ActionHandler {
    public ProcessResult handle(ProcessContext ctx) {
        String reason = ctx.getPayload().getReason();

        pauseMainWorkflowIfRequired(ctx.getWorkflowInstance(), ctx.getNodeInstance());

        CaseSubflowInstance subflow = subflowFactory.create(
            ctx.getCaseInfo(),
            ctx.getWorkflowInstance(),
            SubflowType.CORRECTION,
            ctx.getNodeInstance().getNodeCode(),
            ctx.getUser()
        );

        WorkflowNodeDef startNodeDef = subflowDefinitionService.getStartNode(SubflowType.CORRECTION);
        CaseNodeInstance subNode = nodeInstanceFactory.createForSubflow(subflow, startNodeDef);
        List<CaseTask> tasks = taskFactory.createSubflowTasks(ctx.getCaseInfo(), subflow, subNode);

        ctx.getCaseInfo().setCurrentStatus(CORRECTION_PENDING);
        updateCaseSnapshot(ctx.getCaseInfo(), CORRECTION_PENDING, subNode, tasks);

        return ProcessResult.subflowStarted(subflow, subNode, tasks);
    }
}
```

---

## 15. 子流程完成

## 15.1 规则

- 子流程完成后，恢复主流程等待节点。
- 主流程根据子流程结果决定继续、退回或终止。
- 子流程轨迹必须在案件流程轨迹中可见。

## 15.2 伪代码

```java
void completeSubflow(CaseSubflowInstance subflow, SubflowResult result, CurrentUser user) {
    subflow.setStatus(COMPLETED);
    subflow.setEndedAt(now());
    subflowRepository.update(subflow);

    CaseWorkflowInstance mainInstance = workflowInstanceRepository.getById(subflow.getParentInstanceId());
    CaseNodeInstance waitingNode = nodeInstanceRepository.getWaitingNode(mainInstance.getId(), subflow.getTriggerNodeCode());

    if (result == SUCCESS) {
        resumeMainWorkflow(mainInstance, waitingNode);
        CaseStatus newStatus = caseStatusResolver.resolveByNode(waitingNode.getNodeCode());
        updateCaseStatus(subflow.getCaseId(), newStatus);
    }

    if (result == FAILED) {
        // 根据业务规则决定退回、继续补正或终止
        handleSubflowFailed(subflow, mainInstance, waitingNode);
    }

    auditService.writeSubflowCompletedLog(subflow, result, user);
}
```

---

## 16. 任务生成规则

## 16.1 总入口

```java
List<CaseTask> createTasks(CaseInfo caseInfo, CaseWorkflowInstance wfInstance, CaseNodeInstance nodeInstance) {
    WorkflowNodeDef nodeDef = nodeDefRepository.getByCode(wfInstance.getWfId(), nodeInstance.getNodeCode());

    if (nodeDef.getHandlerMode() == SINGLE) {
        return createSingleTasks(caseInfo, wfInstance, nodeInstance, nodeDef);
    }

    if (nodeDef.getHandlerMode() == CANDIDATE) {
        return createCandidateTask(caseInfo, wfInstance, nodeInstance, nodeDef);
    }

    if (nodeDef.getHandlerMode() == PARALLEL) {
        return createParallelTasks(caseInfo, wfInstance, nodeInstance, nodeDef);
    }

    throw new BusinessException("未知任务模式");
}
```

## 16.2 单人任务

```java
List<CaseTask> createSingleTasks(CaseInfo caseInfo, CaseWorkflowInstance wfInstance, CaseNodeInstance nodeInstance, WorkflowNodeDef nodeDef) {
    User assignee = handlerResolver.resolveSingleHandler(caseInfo, nodeDef);
    CaseTask task = new CaseTask(SINGLE, assignee);
    taskRepository.insert(task);
    return List.of(task);
}
```

## 16.3 候选任务

```java
List<CaseTask> createCandidateTask(CaseInfo caseInfo, CaseWorkflowInstance wfInstance, CaseNodeInstance nodeInstance, WorkflowNodeDef nodeDef) {
    List<User> candidates = handlerResolver.resolveCandidates(caseInfo, nodeDef);

    CaseTask task = new CaseTask(CANDIDATE);
    taskRepository.insert(task);

    for (User candidate : candidates) {
        candidateRepository.insert(task.getId(), candidate.getId());
    }

    return List.of(task);
}
```

## 16.4 并行任务

```java
List<CaseTask> createParallelTasks(CaseInfo caseInfo, CaseWorkflowInstance wfInstance, CaseNodeInstance nodeInstance, WorkflowNodeDef nodeDef) {
    List<User> handlers = handlerResolver.resolveParallelHandlers(caseInfo, nodeDef);
    List<CaseTask> tasks = new ArrayList<>();

    for (User handler : handlers) {
        CaseTask task = new CaseTask(PARALLEL, handler);
        taskRepository.insert(task);
        tasks.add(task);
    }

    return tasks;
}
```

---

## 17. 案件摘要回写规则

每次流程动作完成后，必须回写 `case_info` 的摘要字段，方便列表查询。

字段包括：

- current_status
- current_node_code
- current_node_name
- current_handler_id
- current_handler_name
- deadline_at
- updated_at
- version_no

## 17.1 伪代码

```java
void updateCaseSnapshot(CaseInfo caseInfo, CaseStatus status, CaseNodeInstance node, List<CaseTask> tasks) {
    caseInfo.setCurrentStatus(status);
    caseInfo.setCurrentNodeCode(node.getNodeCode());
    caseInfo.setCurrentNodeName(node.getNodeName());

    CurrentHandlerSnapshot handler = handlerSnapshotResolver.resolve(tasks);
    caseInfo.setCurrentHandlerId(handler.getUserId());
    caseInfo.setCurrentHandlerName(handler.getUserName());
    caseInfo.setDeadlineAt(deadlineResolver.resolve(node, tasks));

    caseRepository.updateWithOptimisticLock(caseInfo);
}
```

---

## 18. 日志写入规则

所有状态机动作都必须写日志。

## 18.1 案件操作日志

每次动作都写入：

- case_id
- wf_instance_id
- node_code
- task_id
- action_code
- action_name
- operator_id
- operator_name
- content
- created_at

## 18.2 状态变更日志

当案件状态发生变化时写入：

- case_id
- from_status
- to_status
- reason
- operator_id
- created_at

## 18.3 专项日志

以下动作需要专项日志：

- WITHDRAW：case_withdraw_log
- REOPEN：case_reopen_log
- 材料/鉴材/文书流转：case_transfer_log

---

## 19. 并发控制

## 19.1 风险场景

- 两个人同时办理同一个任务。
- 候选任务被多人同时认领。
- 一个节点被重复提交。
- 同一案件被同时终止和推进。

## 19.2 建议策略

1. `case_info` 使用 `version_no` 乐观锁。
2. `case_task` 办理时必须校验 `task_status`。
3. 候选任务认领时使用数据库唯一约束或事务锁。
4. 每次动作处理必须在事务中执行。
5. 关键动作可按 `case_id` 做 Redis 短锁。

## 19.3 任务完成伪代码

```java
int updated = taskRepository.completeTask(taskId, expectedStatus, resultCode, opinion, userId);
if (updated == 0) {
    throw new BusinessException("任务状态已变化，请刷新后重试");
}
```

---

## 20. 第一阶段建议先实现的动作

### P0 必须实现

- SUBMIT
- APPROVE
- RETURN
- TERMINATE
- ASSIGN
- CLAIM

### P1 建议实现

- WITHDRAW
- REOPEN
- START_CORRECTION
- START_MATERIAL_SUPPLEMENT
- START_DOC_ISSUE

### P2 后置

- TRANSFER
- ADD_SIGN
- START_REWORK
- ARCHIVE 增强

---

## 21. 下一步建议

基于本文档，下一步可以继续输出：

1. 建表 SQL 初稿。
2. Spring Boot 后端包结构与类设计。
3. 状态机核心类图。
4. 前端页面路由与组件结构。
5. OpenAPI 接口草案。

建议优先级：

1. 建表 SQL 初稿
2. 后端包结构与类设计
3. OpenAPI 接口草案
4. 前端路由与组件结构
