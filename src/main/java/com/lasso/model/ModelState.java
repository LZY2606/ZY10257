package com.lasso.model;

import java.util.List;
import java.util.Map;

public record ModelState(
        String modelId,
        String stateId,
        int ord,
        Map<String, Object> variables,
        List<String> acceptSets,
        List<String> fairSets
) {
}
