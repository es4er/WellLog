package com.upc.wms.agent.workflow;

import java.util.Map;

public record NodeExecutionResult(boolean success, boolean manualRequired, boolean retryable,
                                  Map<String, Object> outputs, String message) {
    public NodeExecutionResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }

    public static NodeExecutionResult success(Map<String, Object> outputs, String message) {
        return new NodeExecutionResult(true, false, false, outputs, message);
    }

    public static NodeExecutionResult failure(String message, boolean retryable) {
        return new NodeExecutionResult(false, false, retryable, Map.of(), message);
    }

    public static NodeExecutionResult manual(String message) {
        return new NodeExecutionResult(false, true, false, Map.of(), message);
    }
}
