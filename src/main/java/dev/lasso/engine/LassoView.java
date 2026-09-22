package dev.lasso.engine;

import java.util.List;

public record LassoView(
        List<String> stateIds,
        int entryIndex,
        String backEdgeTransitionId,
        int loopIterations,
        List<String> keptVariables
) {
}
