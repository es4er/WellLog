package com.upc.wms.agent.supervision;

import java.util.List;

public record VerificationResult(boolean passed, List<String> violations) {
    public VerificationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static VerificationResult pass() {
        return new VerificationResult(true, List.of());
    }

    public static VerificationResult fail(String violation) {
        return new VerificationResult(false, List.of(violation));
    }
}
