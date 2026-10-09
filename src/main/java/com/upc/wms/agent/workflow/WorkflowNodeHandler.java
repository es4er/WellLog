package com.upc.wms.agent.workflow;

@FunctionalInterface
public interface WorkflowNodeHandler {
    NodeExecutionResult execute(WorkflowNodeContext context) throws Exception;
}
