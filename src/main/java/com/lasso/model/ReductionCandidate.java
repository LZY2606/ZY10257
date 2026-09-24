package com.lasso.model;

import java.util.List;

public record ReductionCandidate(
        String key,
        String explanation,
        List<String> keptStates,
        List<String> deletedStates,
        List<String> keptVariables,
        List<String> deletedVariables,
        int loopIterations,
        CheckResult replay
) {
}
