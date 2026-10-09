package com.upc.wms.agent.platform;

import com.upc.wms.agent.workflow.WorkflowExecution;
import com.upc.wms.entity.AgentTask;

public record GraphTaskLaunch(AgentTask task, WorkflowExecution execution) {
}
