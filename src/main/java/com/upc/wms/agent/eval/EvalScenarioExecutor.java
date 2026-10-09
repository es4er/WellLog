package com.upc.wms.agent.eval;

import java.util.Map;

@FunctionalInterface
public interface EvalScenarioExecutor {
    Map<String, Object> execute(WmsEvalCase evalCase);
}
