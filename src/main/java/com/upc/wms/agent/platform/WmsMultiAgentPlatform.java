package com.upc.wms.agent.platform;

import com.upc.wms.agent.capability.OrchestratorPlan;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.messaging.AgentMessage;
import com.upc.wms.agent.messaging.AgentMessageBus;
import com.upc.wms.agent.tool.ToolDescriptor;
import com.upc.wms.agent.tool.ToolRegistry;
import com.upc.wms.agent.workflow.WorkflowExecution;
import com.upc.wms.agent.workflow.WorkflowEdge;
import com.upc.wms.agent.workflow.WorkflowGraph;
import com.upc.wms.agent.workflow.WorkflowGraphExecutor;
import com.upc.wms.agent.workflow.WorkflowNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WmsMultiAgentPlatform {

    private final WorkflowGraphExecutor executor;
    private final LegacyAgentWorkflowAdapter legacyAdapter;
    private final ToolRegistry toolRegistry;
    private final AgentMessageBus messageBus;
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();

    public WmsMultiAgentPlatform(WorkflowGraphExecutor executor, LegacyAgentWorkflowAdapter legacyAdapter,
                                 ToolRegistry toolRegistry, AgentMessageBus messageBus) {
        this.executor = executor;
        this.legacyAdapter = legacyAdapter;
        this.toolRegistry = toolRegistry;
        this.messageBus = messageBus;
    }

    public WorkflowGraph graphFromPlan(String graphId, OrchestratorPlan plan) {
        if (plan == null || plan.getPlannedChain() == null || plan.getPlannedChain().isEmpty()) {
            throw new IllegalArgumentException("Orchestrator plan must contain at least one agent");
        }
        List<String> chain = plan.getPlannedChain();
        List<WorkflowNode> nodes = new ArrayList<>();
        for (int i = 0; i < chain.size(); i++) {
            nodes.add(WorkflowNode.of("step-" + (i + 1) + "-" + chain.get(i), chain.get(i)));
        }
        List<WorkflowEdge> edges = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            WorkflowNode source = nodes.get(i);
            Set<String> routedAgents = new LinkedHashSet<>();
            for (int j = i + 1; j < nodes.size(); j++) {
                WorkflowNode target = nodes.get(j);
                if (routedAgents.add(target.agentName())) {
                    edges.add(WorkflowEdge.when(source.id(), target.id(),
                            "_nextAgent." + source.id(), target.agentName()));
                }
            }
        }
        return new WorkflowGraph(graphId == null ? UUID.randomUUID().toString() : graphId, nodes, edges);
    }

    public WorkflowExecution execute(WorkflowGraph graph, AgentContext context) {
        Map<String, Object> initialState = context.getData() == null ? Map.of() : context.getData();
        WorkflowExecution execution = executor.execute(graph, initialState, legacyAdapter.handler(context));
        executions.put(execution.getExecutionId(), execution);
        return execution;
    }

    public List<ToolDescriptor> tools() {
        return toolRegistry.descriptors();
    }

    public List<AgentMessage> trace(String executionId) {
        return messageBus.history(executionId);
    }

    public WorkflowExecution execution(String executionId) {
        return executions.get(executionId);
    }
}
