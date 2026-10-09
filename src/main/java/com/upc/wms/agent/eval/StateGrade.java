package com.upc.wms.agent.eval;

import java.util.List;

public record StateGrade(boolean passed, double stateAccuracy, List<String> violations) {
}
