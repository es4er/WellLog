package com.upc.wms.agent.tool;

import java.util.Set;

public record ToolCallContext(
        String agentName,
        Set<String> permissions,
        String correlationId,
        String idempotencyKey) {

    public ToolCallContext {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permission == null || permission.isBlank() || permissions.contains(permission);
    }
}
