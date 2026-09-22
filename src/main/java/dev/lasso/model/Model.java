package dev.lasso.model;

import java.util.List;

public record Model(
        String name,
        String description,
        List<Variable> variables,
        List<ModelState> states,
        List<Transition> transitions,
        List<PropositionSet> acceptanceSets,
        List<PropositionSet> fairnessSets,
        List<String> trace,
        List<LoopEntrySpec> loopEntries
) {
}
