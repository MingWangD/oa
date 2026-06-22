package com.example.judicialappraisal.workflow.dto;

import java.util.List;

public record CaseWorkflowViewResponse(
        String workflowCode,
        String workflowName,
        String currentNodeCode,
        List<Node> nodes,
        List<Transition> transitions,
        List<Transition> nextTransitions
) {
    public CaseWorkflowViewResponse {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        nextTransitions = nextTransitions == null ? List.of() : List.copyOf(nextTransitions);
    }

    public record Node(String nodeCode, String nodeName, String nodeType, Integer sortNo) {
    }

    public record Transition(
            String fromNodeCode,
            String toNodeCode,
            String actionCode,
            String actionName,
            String conditionExpression,
            Integer sortNo
    ) {
    }
}
