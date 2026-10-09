package com.upc.wms.agent.workflow;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class WorkflowExecution {

    private final String executionId = UUID.randomUUID().toString();
    private final String graphId;
    private final Instant startedAt = Instant.now();
    private Instant finishedAt;
    private ExecutionStatus status = ExecutionStatus.RUNNING;
    private final Map<String, NodeStatus> nodeStatuses = new LinkedHashMap<>();
    private final Map<String, Integer> attempts = new LinkedHashMap<>();
    private final Map<String, NodeExecutionResult> results = new LinkedHashMap<>();
    private final Map<String, Object> state = new LinkedHashMap<>();
    private String errorMessage;

    public WorkflowExecution(WorkflowGraph graph, Map<String, Object> initialState) {
        this.graphId = graph.id();
        graph.nodes().forEach(node -> {
            nodeStatuses.put(node.id(), NodeStatus.PENDING);
            attempts.put(node.id(), 0);
        });
        if (initialState != null) state.putAll(initialState);
    }

    public synchronized Map<String, Object> stateSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }

    public synchronized void merge(Map<String, Object> outputs) {
        state.putAll(outputs);
    }

    public synchronized int incrementAttempt(String nodeId) {
        int next = attempts.get(nodeId) + 1;
        attempts.put(nodeId, next);
        return next;
    }

    public synchronized void mark(String nodeId, NodeStatus nodeStatus, NodeExecutionResult result) {
        nodeStatuses.put(nodeId, nodeStatus);
        if (result != null) results.put(nodeId, result);
    }

    public synchronized void finish(ExecutionStatus finalStatus, String error) {
        this.status = finalStatus;
        this.errorMessage = error;
        this.finishedAt = Instant.now();
    }

    public String getExecutionId() { return executionId; }
    public String getGraphId() { return graphId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public ExecutionStatus getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public synchronized Map<String, NodeStatus> getNodeStatuses() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(nodeStatuses));
    }
    public synchronized Map<String, Integer> getAttempts() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(attempts));
    }
    public synchronized Map<String, NodeExecutionResult> getResults() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(results));
    }
    public Map<String, Object> getState() { return stateSnapshot(); }
}
