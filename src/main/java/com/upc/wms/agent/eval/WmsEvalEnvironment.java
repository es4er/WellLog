package com.upc.wms.agent.eval;

import java.util.Map;

/**
 * Adapter for reproducible evaluation against a real database or isolated test container.
 * Every repetition resets the fixture before execution and grades a fresh final snapshot.
 */
public interface WmsEvalEnvironment {
    void reset(WmsEvalCase evalCase);

    void execute(WmsEvalCase evalCase);

    Map<String, Object> snapshot(WmsEvalCase evalCase);
}
