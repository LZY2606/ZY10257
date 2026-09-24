package com.lasso.model;

import java.util.List;

public record Counterexample(
        String id,
        String modelId,
        String name,
        String propertyKind,
        String description,
        List<String> trace,
        List<EntryCandidate> entries
) {
    public record EntryCandidate(String stateId, boolean legal, String note) {
    }
}
