package com.upc.wms.agent.eval;

import java.util.List;
import java.util.Map;

public record WmsEvalRun(String caseId, String category, int repetition, boolean passed,
                         double stateAccuracy, List<String> violations,
                         Map<String, Object> actualState, long latencyMs) {
}
