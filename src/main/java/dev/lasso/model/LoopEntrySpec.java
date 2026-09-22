package dev.lasso.model;

public record LoopEntrySpec(int index, int loopPeriod, String stateId, String label,
                            String backEdgeTransitionId) {
}
