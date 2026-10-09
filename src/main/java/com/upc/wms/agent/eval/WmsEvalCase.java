package com.upc.wms.agent.eval;

import java.util.List;
import java.util.Map;

public record WmsEvalCase(String id, String category, Map<String, Object> initialState,
                          Map<String, Object> expectedState, List<String> forbiddenTrueFlags,
                          int repetitions) {
    public WmsEvalCase {
        initialState = initialState == null ? Map.of() : Map.copyOf(initialState);
        expectedState = expectedState == null ? Map.of() : Map.copyOf(expectedState);
        forbiddenTrueFlags = forbiddenTrueFlags == null ? List.of() : List.copyOf(forbiddenTrueFlags);
        repetitions = repetitions < 1 ? 1 : repetitions;
    }
}
