package com.upc.wms.agent.tool;

public record ToolDescriptor(String name, String inputType, String outputType,
                             String requiredPermission, boolean mutating) {
}
