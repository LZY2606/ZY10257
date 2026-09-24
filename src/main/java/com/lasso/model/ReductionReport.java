package com.lasso.model;

import java.util.List;

public record ReductionReport(
        String kind,
        String metricLabel,
        int bestMetric,
        boolean tie,
        List<ReductionCandidate> candidates
) {
}
