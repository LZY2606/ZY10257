package dev.lasso.engine;

import java.util.List;

public record ReductionResult(
        ReductionKind kind,
        int entryIndex,
        boolean fairnessEnabled,
        List<ReductionCandidate> candidates,
        ReplayResult baseline
) {
}
