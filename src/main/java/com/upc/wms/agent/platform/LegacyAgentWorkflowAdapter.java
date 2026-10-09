package com.upc.wms.agent.platform;

import com.upc.wms.agent.core.Agent;
import com.upc.wms.agent.core.AgentContext;
import com.upc.wms.agent.core.AgentNames;
import com.upc.wms.agent.core.AgentResult;
import com.upc.wms.agent.core.AgentStatus;
import com.upc.wms.agent.workflow.NodeExecutionResult;
import com.upc.wms.agent.workflow.WorkflowNodeHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LegacyAgentWorkflowAdapter {

    private final Map<String, Agent> agents;

    public LegacyAgentWorkflowAdapter(List<Agent> discoveredAgents) {
        this.agents = discoveredAgents.stream().collect(Collectors.toUnmodifiableMap(
                Agent::getName, Function.identity()));
    }

    public WorkflowNodeHandler handler(AgentContext taskContext) {
        return nodeContext -> {
            Agent agent = agents.get(nodeContext.node().agentName());
            if (agent == null) {
                return NodeExecutionResult.failure("Unknown legacy agent: " + nodeContext.node().agentName(), false);
            }
            if (!agent.support(taskContext.getTaskType()) && !AgentNames.AUDIT.equals(agent.getName())) {
                return NodeExecutionResult.failure("Agent does not support task type: "
                        + taskContext.getTaskType(), false);
            }

            AgentContext isolated = copyContext(taskContext, nodeContext.state());
            isolated.setCurrentAgent(agent.getName());
            AgentResult result = agent.handle(isolated);
            if (result == null) {
                return NodeExecutionResult.failure("Agent returned no result", false);
            }
            if (AgentStatus.MANUAL_REQUIRED.name().equals(result.getStatus())) {
                return NodeExecutionResult.manual(result.getMessage());
            }
            if (!Boolean.TRUE.equals(result.getSuccess())) {
                return NodeExecutionResult.failure(result.getMessage(), true);
            }

            Map<String, Object> outputs = new LinkedHashMap<>();
            if (result.getResultData() != null) outputs.putAll(result.getResultData());
            if (result.getBusinessNo() != null) outputs.put("businessNo", result.getBusinessNo());
            if (result.getNextAgent() != null) {
                outputs.put("_nextAgent", result.getNextAgent());
                outputs.put("_nextAgent." + nodeContext.node().id(), result.getNextAgent());
            }
            outputs.put("_lastAgent", agent.getName());
            return NodeExecutionResult.success(outputs, result.getMessage());
        };
    }

    private AgentContext copyContext(AgentContext source, Map<String, Object> state) {
        AgentContext copy = new AgentContext();
        copy.setTaskId(source.getTaskId());
        copy.setTaskNo(source.getTaskNo());
        copy.setTaskType(source.getTaskType());
        copy.setBusinessNo(source.getBusinessNo());
        copy.setSourceAgent(source.getSourceAgent());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setData(new HashMap<>(state));
        return copy;
    }
}
