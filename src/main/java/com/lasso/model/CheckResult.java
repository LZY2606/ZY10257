package com.lasso.model;

import java.util.List;

public record CheckResult(
        String ceId,
        String entry,
        boolean fairnessAssumed,
        ReplayResult replay,
        List<String> acceptUniverse,
        List<String> acceptCovered,
        List<String> acceptMissing,
        List<String> fairUniverse,
        List<String> fairSatisfied,
        List<String> fairUnsatisfied,
        boolean violationReproduced,
        String verdict
) {
}
