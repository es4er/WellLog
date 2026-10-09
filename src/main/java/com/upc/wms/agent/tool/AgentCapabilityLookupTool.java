package com.upc.wms.agent.tool;

import com.upc.wms.agent.capability.AgentCapability;
import com.upc.wms.agent.capability.AgentCapabilityCatalog;
import org.springframework.stereotype.Component;

@Component
public class AgentCapabilityLookupTool implements AgentTool<CapabilityQuery, AgentCapability> {

    private final AgentCapabilityCatalog catalog;

    public AgentCapabilityLookupTool(AgentCapabilityCatalog catalog) {
        this.catalog = catalog;
    }

    @Override public String name() { return "platform.agent.capability"; }
    @Override public Class<CapabilityQuery> inputType() { return CapabilityQuery.class; }
    @Override public Class<AgentCapability> outputType() { return AgentCapability.class; }
    @Override public String requiredPermission() { return "agent:read"; }

    @Override
    public AgentCapability execute(CapabilityQuery input, ToolCallContext context) {
        AgentCapability capability = catalog.get(input.agentName());
        if (capability == null) {
            throw new IllegalArgumentException("Unknown agent: " + input.agentName());
        }
        return capability;
    }
}
