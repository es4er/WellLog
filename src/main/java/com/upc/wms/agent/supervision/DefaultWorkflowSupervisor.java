package com.upc.wms.agent.supervision;

import com.upc.wms.agent.workflow.NodeExecutionResult;
import com.upc.wms.agent.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class DefaultWorkflowSupervisor implements WorkflowSupervisor {
    @Override
    public SupervisorDecision decide(WorkflowNode node, int attempt, NodeExecutionResult result,
                                     VerificationResult verification) {
        if (result.manualRequired()) {
            return SupervisorDecision.REQUIRE_HUMAN;
        }
        if (result.success() && verification.passed()) {
            return SupervisorDecision.ACCEPT;
        }
        if (result.retryable() && attempt < node.maxAttempts()) {
            return SupervisorDecision.RETRY;
        }
        if (!verification.passed()) {
            return SupervisorDecision.REQUIRE_HUMAN;
        }
        return SupervisorDecision.FAIL;
    }
}
