package com.upc.wms.agent.tool;

import java.util.Map;

public record SafetyCheckRequest(Map<String, Object> state) {
    public SafetyCheckRequest {
        state = state == null ? Map.of() : Map.copyOf(state);
    }
}
