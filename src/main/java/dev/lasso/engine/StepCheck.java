package dev.lasso.engine;

public record StepCheck(
        int index,
        String sourceStateId,
        String targetStateId,
        boolean backEdge,
        String witnessTransitionId,
        String guard,
        String guardStatus,
        boolean legal,
        String detail
) {
}
