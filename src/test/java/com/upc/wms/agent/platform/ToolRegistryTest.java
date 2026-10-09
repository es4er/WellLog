package com.upc.wms.agent.platform;

import com.upc.wms.agent.tool.AgentTool;
import com.upc.wms.agent.tool.ToolCallContext;
import com.upc.wms.agent.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolRegistryTest {

    @Test
    void enforcesPermissionAndIdempotencyForMutation() {
        AtomicInteger writes = new AtomicInteger();
        AgentTool<String, Integer> tool = new AgentTool<>() {
            @Override public String name() { return "inventory.reserve"; }
            @Override public Class<String> inputType() { return String.class; }
            @Override public Class<Integer> outputType() { return Integer.class; }
            @Override public String requiredPermission() { return "inventory:write"; }
            @Override public boolean mutating() { return true; }
            @Override public Integer execute(String input, ToolCallContext context) {
                return writes.incrementAndGet();
            }
        };
        ToolRegistry registry = new ToolRegistry(List.of(tool));

        assertThrows(SecurityException.class, () -> registry.invoke("inventory.reserve", "REQ-1",
                new ToolCallContext("InventoryAgent", Set.of(), "c1", "id-1")));

        ToolCallContext allowed = new ToolCallContext("InventoryAgent", Set.of("inventory:write"),
                "c1", "id-1");
        assertEquals(1, registry.invoke("inventory.reserve", "REQ-1", allowed));
        assertEquals(1, registry.invoke("inventory.reserve", "REQ-1", allowed));
        assertEquals(1, writes.get());
    }
}
