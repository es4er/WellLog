package com.upc.wms.agent.platform;

import com.upc.wms.agent.messaging.InMemoryAgentMessageBus;
import com.upc.wms.agent.supervision.CompositeWorkflowVerifier;
import com.upc.wms.agent.supervision.DefaultWorkflowSupervisor;
import com.upc.wms.agent.supervision.WmsSafetyInvariant;
import com.upc.wms.agent.tool.ToolRegistry;
import com.upc.wms.agent.workflow.ExecutionStatus;
import com.upc.wms.agent.workflow.NodeExecutionResult;
import com.upc.wms.agent.workflow.NodeStatus;
import com.upc.wms.agent.workflow.WorkflowEdge;
import com.upc.wms.agent.workflow.WorkflowExecution;
import com.upc.wms.agent.workflow.WorkflowGraph;
import com.upc.wms.agent.workflow.WorkflowGraphExecutor;
import com.upc.wms.agent.workflow.WorkflowNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowGraphExecutorTest {

    private final InMemoryAgentMessageBus messageBus = new InMemoryAgentMessageBus();
    private final WorkflowGraphExecutor executor = new WorkflowGraphExecutor(
            messageBus,
            new ToolRegistry(List.of()),
            new DefaultWorkflowSupervisor(),
            new CompositeWorkflowVerifier(List.of(new WmsSafetyInvariant())));

    @AfterEach
    void closeExecutor() {
        executor.shutdown();
    }

    @Test
    void executesParallelDagAndJoin() {
        WorkflowNode plan = WorkflowNode.of("plan", "CoordinatorAgent");
        WorkflowNode inventory = WorkflowNode.of("inventory", "InventoryAgent");
        WorkflowNode quality = WorkflowNode.of("quality", "QualityAgent");
        WorkflowNode verify = WorkflowNode.of("verify", "VerifierAgent");
        WorkflowGraph graph = new WorkflowGraph("outbound-eval",
                List.of(plan, inventory, quality, verify),
                List.of(
                        WorkflowEdge.always("plan", "inventory"),
                        WorkflowEdge.always("plan", "quality"),
                        WorkflowEdge.always("inventory", "verify"),
                        WorkflowEdge.always("quality", "verify")));

        WorkflowExecution execution = executor.execute(graph, Map.of(), context -> switch (context.node().id()) {
            case "plan" -> NodeExecutionResult.success(Map.of("planned", true), "planned");
            case "inventory" -> NodeExecutionResult.success(Map.of("inventoryChecked", true), "checked");
            case "quality" -> NodeExecutionResult.success(Map.of("qualityChecked", true), "checked");
            default -> NodeExecutionResult.success(Map.of("verified", true), "verified");
        });

        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertTrue(Boolean.TRUE.equals(execution.getState().get("inventoryChecked")));
        assertTrue(Boolean.TRUE.equals(execution.getState().get("qualityChecked")));
        assertEquals(4, execution.getNodeStatuses().values().stream()
                .filter(status -> status == NodeStatus.SUCCESS).count());
        assertEquals(8, messageBus.history(execution.getExecutionId()).size());
    }

    @Test
    void retriesTransientFailureAndPasses() {
        AtomicInteger calls = new AtomicInteger();
        WorkflowGraph graph = WorkflowGraph.sequence("retry", List.of("InventoryAgent"));

        WorkflowExecution execution = executor.execute(graph, Map.of(), context ->
                calls.incrementAndGet() == 1
                        ? NodeExecutionResult.failure("temporary timeout", true)
                        : NodeExecutionResult.success(Map.of("recovered", true), "ok"));

        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        assertEquals(2, calls.get());
        assertTrue(Boolean.TRUE.equals(execution.getState().get("recovered")));
    }

    @Test
    void sendsCriticalSafetyViolationToHumanReview() {
        WorkflowGraph graph = WorkflowGraph.sequence("safety", List.of("OutboundAgent"));
        WorkflowExecution execution = executor.execute(graph, Map.of(), context ->
                NodeExecutionResult.success(Map.of("negativeInventory", true), "unsafe result"));

        assertEquals(ExecutionStatus.MANUAL_REQUIRED, execution.getStatus());
        assertTrue(execution.getErrorMessage().contains("unsafe result")
                || execution.getErrorMessage().contains("safety"));
    }

    @Test
    void rejectsCyclesBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowGraph("cycle",
                List.of(WorkflowNode.of("a", "A"), WorkflowNode.of("b", "B")),
                List.of(WorkflowEdge.always("a", "b"), WorkflowEdge.always("b", "a"))));
    }
}
