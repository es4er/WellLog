package com.upc.wms.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool<?, ?>> tools = new LinkedHashMap<>();
    private final Map<String, Object> idempotentResults = new ConcurrentHashMap<>();

    public ToolRegistry(List<AgentTool<?, ?>> discoveredTools) {
        if (discoveredTools != null) {
            discoveredTools.forEach(this::register);
        }
    }

    public synchronized void register(AgentTool<?, ?> tool) {
        Objects.requireNonNull(tool, "tool");
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Duplicate tool name: " + tool.name());
        }
    }

    public List<ToolDescriptor> descriptors() {
        return tools.values().stream()
                .map(tool -> new ToolDescriptor(tool.name(), tool.inputType().getSimpleName(),
                        tool.outputType().getSimpleName(), tool.requiredPermission(), tool.mutating()))
                .toList();
    }

    public Object invoke(String name, Object input, ToolCallContext context) {
        AgentTool<?, ?> rawTool = tools.get(name);
        if (rawTool == null) {
            throw new IllegalArgumentException("Unknown agent tool: " + name);
        }
        if (!rawTool.inputType().isInstance(input)) {
            throw new IllegalArgumentException("Tool " + name + " expects "
                    + rawTool.inputType().getName() + " but received "
                    + (input == null ? "null" : input.getClass().getName()));
        }
        if (context == null || !context.hasPermission(rawTool.requiredPermission())) {
            throw new SecurityException("Agent is not allowed to invoke tool: " + name);
        }
        if (rawTool.mutating()) {
            if (context.idempotencyKey() == null || context.idempotencyKey().isBlank()) {
                throw new IllegalArgumentException("Mutating tool requires an idempotency key: " + name);
            }
            String cacheKey = name + ":" + context.idempotencyKey();
            return idempotentResults.computeIfAbsent(cacheKey, ignored -> invokeTyped(rawTool, input, context));
        }
        return invokeTyped(rawTool, input, context);
    }

    @SuppressWarnings("unchecked")
    private <I, O> O invokeTyped(AgentTool<?, ?> rawTool, Object input, ToolCallContext context) {
        AgentTool<I, O> tool = (AgentTool<I, O>) rawTool;
        return tool.execute((I) input, context);
    }
}
