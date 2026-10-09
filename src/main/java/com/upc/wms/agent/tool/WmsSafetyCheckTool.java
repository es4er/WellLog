package com.upc.wms.agent.tool;

import com.upc.wms.agent.supervision.InvariantResult;
import com.upc.wms.agent.supervision.WmsSafetyInvariant;
import org.springframework.stereotype.Component;

@Component
public class WmsSafetyCheckTool implements AgentTool<SafetyCheckRequest, InvariantResult> {

    private final WmsSafetyInvariant invariant;

    public WmsSafetyCheckTool(WmsSafetyInvariant invariant) {
        this.invariant = invariant;
    }

    @Override public String name() { return "wms.safety.verify"; }
    @Override public Class<SafetyCheckRequest> inputType() { return SafetyCheckRequest.class; }
    @Override public Class<InvariantResult> outputType() { return InvariantResult.class; }
    @Override public String requiredPermission() { return "wms:verify"; }

    @Override
    public InvariantResult execute(SafetyCheckRequest input, ToolCallContext context) {
        return invariant.verify(input.state());
    }
}
