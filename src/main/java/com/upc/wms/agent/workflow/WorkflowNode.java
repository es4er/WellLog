package com.upc.wms.agent.workflow;

import java.time.Duration;
import java.util.Objects;

public record WorkflowNode(String id, String agentName, int maxAttempts, Duration timeout) {
    public WorkflowNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(agentName, "agentName");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
    }

    public static WorkflowNode of(String id, String agentName) {
        return new WorkflowNode(id, agentName, 2, Duration.ofSeconds(30));
    }
}
