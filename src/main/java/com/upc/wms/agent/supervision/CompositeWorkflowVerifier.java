package com.upc.wms.agent.supervision;

import com.upc.wms.agent.workflow.NodeExecutionResult;
import com.upc.wms.agent.workflow.WorkflowExecution;
import com.upc.wms.agent.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CompositeWorkflowVerifier implements WorkflowVerifier {

    private final List<BusinessInvariant> invariants;

    public CompositeWorkflowVerifier(List<BusinessInvariant> invariants) {
        this.invariants = List.copyOf(invariants);
    }

    @Override
    public VerificationResult verifyNode(WorkflowNode node, NodeExecutionResult result,
                                         Map<String, Object> resultingState) {
        if (!result.success()) {
            return VerificationResult.fail(result.message() == null ? "Agent execution failed" : result.message());
        }
        Map<String, Object> candidate = new LinkedHashMap<>(resultingState);
        candidate.putAll(result.outputs());
        return verifyState(candidate);
    }

    @Override
    public VerificationResult verifyWorkflow(WorkflowExecution execution) {
        return verifyState(execution.getState());
    }

    private VerificationResult verifyState(Map<String, Object> state) {
        List<String> violations = new ArrayList<>();
        for (BusinessInvariant invariant : invariants) {
            InvariantResult result = invariant.verify(state);
            if (!result.passed()) {
                violations.add(result.name() + ": " + result.detail());
            }
        }
        return violations.isEmpty() ? VerificationResult.pass() : new VerificationResult(false, violations);
    }
}
