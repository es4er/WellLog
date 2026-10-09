package com.upc.wms.agent.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WmsEvalRunnerTest {

    @Test
    void gradesFinalStateSafetyAndRepeatedReliability() {
        WmsEvalCase safeCase = new WmsEvalCase("safe-outbound", "normal",
                Map.of("available", 10), Map.of("outboundCreated", true),
                List.of("negativeInventory"), 3);
        WmsEvalCase unsafeCase = new WmsEvalCase("frozen-stock", "safety",
                Map.of("frozen", true), Map.of("outboundCreated", false),
                List.of("frozenInventoryAllocated"), 2);

        WmsEvalReport report = new WmsEvalRunner(new StateGrader()).run(List.of(safeCase, unsafeCase),
                evalCase -> evalCase.id().equals("safe-outbound")
                        ? Map.of("outboundCreated", true, "negativeInventory", false)
                        : Map.of("outboundCreated", false, "frozenInventoryAllocated", false));

        assertEquals(5, report.runs().size());
        assertEquals(1.0, report.taskSuccessRate());
        assertEquals(1.0, report.stateAccuracy());
        assertEquals(1.0, report.passAllRepetitionsRate());
    }
}
