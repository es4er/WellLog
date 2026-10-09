package com.upc.wms.agent.tool;

public interface AgentTool<I, O> {
    String name();

    Class<I> inputType();

    Class<O> outputType();

    String requiredPermission();

    default boolean mutating() {
        return false;
    }

    O execute(I input, ToolCallContext context);
}
