package com.upc.wms.agent.workflow;

import java.util.Map;
import java.util.Objects;

public record WorkflowEdge(String from, String to, String conditionKey, Object expectedValue) {
    public WorkflowEdge {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
    }

    public static WorkflowEdge always(String from, String to) {
        return new WorkflowEdge(from, to, null, null);
    }

    public static WorkflowEdge when(String from, String to, String conditionKey, Object expectedValue) {
        return new WorkflowEdge(from, to, conditionKey, expectedValue);
    }

    public boolean matches(Map<String, Object> state) {
        return conditionKey == null || Objects.equals(expectedValue, state.get(conditionKey));
    }
}
