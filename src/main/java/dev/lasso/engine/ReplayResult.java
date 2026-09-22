package dev.lasso.engine;

import java.util.List;
import java.util.Map;

public record ReplayResult(
        String scenarioId,
        LassoView lasso,
        boolean fairnessEnabled,
        List<StepCheck> steps,
        List<SetCoverage> acceptanceCoverage,
        List<SetCoverage> fairnessCoverage,
        boolean replayed,
        boolean violationReproduced,
        String verdict,
        String explanation,
        int explanationLength,
        Map<String, Object> deletedElements
) {
}
