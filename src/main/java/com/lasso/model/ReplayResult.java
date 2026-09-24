package com.lasso.model;

import java.util.List;

public record ReplayResult(
        boolean valid,
        List<String> stateSequence,
        List<String> witnesses,
        List<String> missingEdges,
        String failureReason
) {
    public static ReplayResult ok(List<String> states, List<String> witnesses) {
        return new ReplayResult(true, states, witnesses, List.of(), null);
    }

    public static ReplayResult fail(List<String> states, List<String> witnesses,
                                    List<String> missingEdges, String reason) {
        return new ReplayResult(false, states, witnesses, missingEdges, reason);
    }
}
