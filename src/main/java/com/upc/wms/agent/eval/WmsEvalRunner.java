package com.upc.wms.agent.eval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WmsEvalRunner {

    private final StateGrader grader;

    public WmsEvalRunner(StateGrader grader) {
        this.grader = grader;
    }

    public WmsEvalReport run(List<WmsEvalCase> cases, EvalScenarioExecutor scenarioExecutor) {
        List<WmsEvalRun> runs = new ArrayList<>();
        Map<String, Boolean> allPassedByCase = new HashMap<>();
        for (WmsEvalCase evalCase : cases) {
            allPassedByCase.put(evalCase.id(), true);
            for (int repetition = 1; repetition <= evalCase.repetitions(); repetition++) {
                long started = System.nanoTime();
                Map<String, Object> actual;
                StateGrade grade;
                try {
                    actual = scenarioExecutor.execute(evalCase);
                    actual = actual == null ? Map.of() : Map.copyOf(actual);
                    grade = grader.grade(evalCase, actual);
                } catch (Exception error) {
                    actual = Map.of();
                    grade = new StateGrade(false, 0.0,
                            List.of("Execution error: " + (error.getMessage() == null ? error : error.getMessage())));
                }
                long latencyMs = (System.nanoTime() - started) / 1_000_000;
                runs.add(new WmsEvalRun(evalCase.id(), evalCase.category(), repetition, grade.passed(),
                        grade.stateAccuracy(), grade.violations(), actual, latencyMs));
                if (!grade.passed()) allPassedByCase.put(evalCase.id(), false);
            }
        }

        if (runs.isEmpty()) return new WmsEvalReport(List.of(), 0, 0, 0, 0);
        double success = runs.stream().filter(WmsEvalRun::passed).count() / (double) runs.size();
        double accuracy = runs.stream().mapToDouble(WmsEvalRun::stateAccuracy).average().orElse(0);
        double safetyViolations = runs.stream().filter(run -> run.violations().stream()
                .anyMatch(v -> v.startsWith("Forbidden safety flag"))).count() / (double) runs.size();
        double passAll = allPassedByCase.values().stream().filter(Boolean::booleanValue).count()
                / (double) allPassedByCase.size();
        return new WmsEvalReport(List.copyOf(runs), success, accuracy, safetyViolations, passAll);
    }

    public WmsEvalReport run(List<WmsEvalCase> cases, WmsEvalEnvironment environment) {
        return run(cases, evalCase -> {
            environment.reset(evalCase);
            environment.execute(evalCase);
            return environment.snapshot(evalCase);
        });
    }
}
