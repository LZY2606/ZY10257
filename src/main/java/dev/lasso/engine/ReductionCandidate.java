package dev.lasso.engine;

public record ReductionCandidate(
        ReductionKind kind,
        int score,
        boolean shortestExplanation,
        ReplayResult replay
) {
}
