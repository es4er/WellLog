package com.upc.wms.agent.workflow;

import com.upc.wms.agent.messaging.AgentMessage;
import com.upc.wms.agent.messaging.AgentMessageBus;
import com.upc.wms.agent.messaging.MessageType;
import com.upc.wms.agent.supervision.SupervisorDecision;
import com.upc.wms.agent.supervision.VerificationResult;
import com.upc.wms.agent.supervision.WorkflowSupervisor;
import com.upc.wms.agent.supervision.WorkflowVerifier;
import com.upc.wms.agent.tool.ToolRegistry;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class WorkflowGraphExecutor {

    private final AgentMessageBus messageBus;
    private final ToolRegistry toolRegistry;
    private final WorkflowSupervisor supervisor;
    private final WorkflowVerifier verifier;
    private final ExecutorService executor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())));

    public WorkflowGraphExecutor(AgentMessageBus messageBus, ToolRegistry toolRegistry,
                                 WorkflowSupervisor supervisor, WorkflowVerifier verifier) {
        this.messageBus = messageBus;
        this.toolRegistry = toolRegistry;
        this.supervisor = supervisor;
        this.verifier = verifier;
    }

    public WorkflowExecution execute(WorkflowGraph graph, Map<String, Object> initialState,
                                     WorkflowNodeHandler handler) {
        WorkflowExecution execution = new WorkflowExecution(graph, initialState);

        while (hasPending(execution)) {
            List<WorkflowNode> ready = findReadyNodes(graph, execution);
            if (ready.isEmpty()) {
                if (skipBlockedNodes(graph, execution)) {
                    continue;
                }
                execution.finish(ExecutionStatus.FAILED, "No executable node; dependency deadlock detected");
                return execution;
            }

            Map<WorkflowNode, CompletableFuture<NodeRunOutcome>> futures = new LinkedHashMap<>();
            for (WorkflowNode node : ready) {
                execution.mark(node.id(), NodeStatus.RUNNING, null);
                CompletableFuture<NodeRunOutcome> future = CompletableFuture
                        .supplyAsync(() -> runOnce(execution, node, handler), executor)
                        .completeOnTimeout(NodeRunOutcome.timeout(node),
                                node.timeout().toMillis(), TimeUnit.MILLISECONDS)
                        .exceptionally(error -> NodeRunOutcome.failure(node,
                                error.getMessage() == null ? error.toString() : error.getMessage()));
                futures.put(node, future);
            }

            futures.forEach((node, future) -> applyOutcome(execution, node, future.join()));
        }

        finishExecution(execution);
        return execution;
    }

    private NodeRunOutcome runOnce(WorkflowExecution execution, WorkflowNode node,
                                   WorkflowNodeHandler handler) {
        int attempt = execution.incrementAttempt(node.id());
        AgentMessage request = AgentMessage.create(execution.getExecutionId(), "orchestrator",
                node.agentName(), MessageType.REQUEST,
                Map.of("nodeId", node.id(), "attempt", attempt));
        messageBus.send(request);

        NodeExecutionResult result;
        try {
            WorkflowNodeContext context = new WorkflowNodeContext(execution.getExecutionId(), node,
                    execution.stateSnapshot(), messageBus.drain(node.agentName()), toolRegistry);
            result = handler.execute(context);
            if (result == null) {
                result = NodeExecutionResult.failure("Agent returned no result", false);
            }
        } catch (Exception error) {
            result = NodeExecutionResult.failure(
                    error.getMessage() == null ? error.toString() : error.getMessage(), true);
        }

        messageBus.send(request.reply(node.agentName(), result.success() ? MessageType.RESPONSE : MessageType.FAILURE,
                Map.of("nodeId", node.id(), "success", result.success(),
                        "message", result.message() == null ? "" : result.message())));

        VerificationResult verification = verifier.verifyNode(node, result, execution.stateSnapshot());
        SupervisorDecision decision = supervisor.decide(node, attempt, result, verification);
        return new NodeRunOutcome(node, result, verification, decision);
    }

    private void applyOutcome(WorkflowExecution execution, WorkflowNode node, NodeRunOutcome outcome) {
        switch (outcome.decision()) {
            case ACCEPT -> {
                execution.merge(outcome.result().outputs());
                execution.mark(node.id(), NodeStatus.SUCCESS, outcome.result());
            }
            case RETRY -> {
                if (execution.getAttempts().get(node.id()) >= node.maxAttempts()) {
                    execution.mark(node.id(), NodeStatus.FAILED, outcome.result());
                } else {
                    execution.mark(node.id(), NodeStatus.PENDING, outcome.result());
                }
            }
            case REQUIRE_HUMAN -> {
                NodeExecutionResult reviewResult = outcome.result();
                if (!outcome.verification().passed()) {
                    reviewResult = NodeExecutionResult.manual(String.join("; ", outcome.verification().violations()));
                }
                execution.mark(node.id(), NodeStatus.MANUAL_REQUIRED, reviewResult);
            }
            case FAIL -> execution.mark(node.id(), NodeStatus.FAILED, outcome.result());
        }
    }

    private List<WorkflowNode> findReadyNodes(WorkflowGraph graph, WorkflowExecution execution) {
        Map<String, NodeStatus> statuses = execution.getNodeStatuses();
        Map<String, Object> state = execution.stateSnapshot();
        List<WorkflowNode> ready = new ArrayList<>();
        for (WorkflowNode node : graph.nodes()) {
            if (statuses.get(node.id()) != NodeStatus.PENDING) continue;
            Set<String> predecessors = graph.predecessors(node.id());
            if (predecessors.isEmpty()) {
                ready.add(node);
                continue;
            }
            boolean allTerminal = predecessors.stream().allMatch(id -> isTerminal(statuses.get(id)));
            boolean blocked = predecessors.stream().anyMatch(id ->
                    statuses.get(id) == NodeStatus.FAILED || statuses.get(id) == NodeStatus.MANUAL_REQUIRED);
            boolean conditionMatches = graph.incoming(node.id()).stream().anyMatch(edge ->
                    statuses.get(edge.from()) == NodeStatus.SUCCESS && edge.matches(state));
            if (allTerminal && !blocked && conditionMatches) ready.add(node);
        }
        return ready;
    }

    private boolean skipBlockedNodes(WorkflowGraph graph, WorkflowExecution execution) {
        boolean changed = false;
        Map<String, NodeStatus> statuses = execution.getNodeStatuses();
        Map<String, Object> state = execution.stateSnapshot();
        for (WorkflowNode node : graph.nodes()) {
            if (statuses.get(node.id()) != NodeStatus.PENDING) continue;
            Set<String> predecessors = graph.predecessors(node.id());
            if (predecessors.isEmpty()) continue;
            boolean allTerminal = predecessors.stream().allMatch(id -> isTerminal(statuses.get(id)));
            boolean blocked = predecessors.stream().anyMatch(id ->
                    statuses.get(id) == NodeStatus.FAILED || statuses.get(id) == NodeStatus.MANUAL_REQUIRED);
            boolean noConditionMatched = graph.incoming(node.id()).stream().noneMatch(edge ->
                    statuses.get(edge.from()) == NodeStatus.SUCCESS && edge.matches(state));
            if (allTerminal && (blocked || noConditionMatched)) {
                execution.mark(node.id(), NodeStatus.SKIPPED, null);
                changed = true;
            }
        }
        return changed;
    }

    private void finishExecution(WorkflowExecution execution) {
        if (execution.getNodeStatuses().containsValue(NodeStatus.MANUAL_REQUIRED)) {
            execution.finish(ExecutionStatus.MANUAL_REQUIRED, firstError(execution));
            return;
        }
        if (execution.getNodeStatuses().containsValue(NodeStatus.FAILED)) {
            execution.finish(ExecutionStatus.FAILED, firstError(execution));
            return;
        }
        VerificationResult finalVerification = verifier.verifyWorkflow(execution);
        if (!finalVerification.passed()) {
            execution.finish(ExecutionStatus.MANUAL_REQUIRED, String.join("; ", finalVerification.violations()));
            return;
        }
        execution.finish(ExecutionStatus.SUCCESS, null);
    }

    private String firstError(WorkflowExecution execution) {
        return execution.getResults().values().stream()
                .filter(result -> !result.success())
                .map(NodeExecutionResult::message)
                .filter(message -> message != null && !message.isBlank())
                .findFirst().orElse("Workflow execution did not complete");
    }

    private boolean hasPending(WorkflowExecution execution) {
        return execution.getNodeStatuses().containsValue(NodeStatus.PENDING);
    }

    private boolean isTerminal(NodeStatus status) {
        return status == NodeStatus.SUCCESS || status == NodeStatus.FAILED
                || status == NodeStatus.SKIPPED || status == NodeStatus.MANUAL_REQUIRED;
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private record NodeRunOutcome(WorkflowNode node, NodeExecutionResult result,
                                  VerificationResult verification, SupervisorDecision decision) {
        private static NodeRunOutcome timeout(WorkflowNode node) {
            NodeExecutionResult result = NodeExecutionResult.failure("Agent node timed out", true);
            return new NodeRunOutcome(node, result, VerificationResult.fail("Agent node timed out"),
                    SupervisorDecision.RETRY);
        }

        private static NodeRunOutcome failure(WorkflowNode node, String message) {
            NodeExecutionResult result = NodeExecutionResult.failure(message, false);
            return new NodeRunOutcome(node, result, VerificationResult.fail(message), SupervisorDecision.FAIL);
        }
    }
}
