package com.upc.wms.agent.supervision;

import com.upc.wms.agent.workflow.NodeExecutionResult;
import com.upc.wms.agent.workflow.WorkflowNode;

public interface WorkflowSupervisor {
    SupervisorDecision decide(WorkflowNode node, int attempt, NodeExecutionResult result,
                              VerificationResult verification);
}
