package dev.lasso.engine;

public record SetCoverage(
        String setId,
        String name,
        String kind,
        java.util.List<String> prefixHits,
        java.util.List<String> loopHits,
        String status,
        boolean satisfied
) {
}
