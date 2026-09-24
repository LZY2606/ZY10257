package com.lasso.model;

public record Transition(
        long id,
        String modelId,
        int ord,
        String source,
        String target,
        String guard,
        String description
) {
}
