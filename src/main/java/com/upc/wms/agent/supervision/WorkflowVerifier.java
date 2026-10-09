package com.upc.wms.agent.supervision;

import com.upc.wms.agent.workflow.NodeExecutionResult;
import com.upc.wms.agent.workflow.WorkflowExecution;
import com.upc.wms.agent.workflow.WorkflowNode;

import java.util.Map;

public interface WorkflowVerifier {
    VerificationResult verifyNode(WorkflowNode node, NodeExecutionResult result, Map<String, Object> resultingState);

    VerificationResult verifyWorkflow(WorkflowExecution execution);
}
