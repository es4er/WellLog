package com.upc.wms.agent.workflow;

import com.upc.wms.agent.messaging.AgentMessage;
import com.upc.wms.agent.tool.ToolRegistry;

import java.util.List;
import java.util.Map;

public record WorkflowNodeContext(String executionId, WorkflowNode node, Map<String, Object> state,
                                  List<AgentMessage> inbox, ToolRegistry tools) {
}
