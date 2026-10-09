package com.upc.wms.agent.supervision;

public record InvariantResult(String name, boolean passed, String detail) {
    public static InvariantResult pass(String name) {
        return new InvariantResult(name, true, null);
    }

    public static InvariantResult fail(String name, String detail) {
        return new InvariantResult(name, false, detail);
    }
}
