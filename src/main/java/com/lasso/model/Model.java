package com.lasso.model;

import java.util.List;

public record Model(
        String id,
        String name,
        String description,
        List<ModelState> states,
        List<Transition> transitions
) {
    public ModelState state(String stateId) {
        return states.stream()
                .filter(s -> s.stateId().equals(stateId))
                .findFirst()
                .orElse(null);
    }
}
