package com.upc.wms.agent.eval;

import java.util.List;

public record WmsEvalReport(List<WmsEvalRun> runs, double taskSuccessRate,
                            double stateAccuracy, double safetyViolationRate,
                            double passAllRepetitionsRate) {
}
