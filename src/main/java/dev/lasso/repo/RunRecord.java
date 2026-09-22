package dev.lasso.repo;

public record RunRecord(
        Long id,
        String createdAt,
        String action,
        int entryIndex,
        boolean fairnessEnabled,
        String reductionKind,
        String verdict,
        boolean violationReproduced,
        String payloadJson,
        String source
) {
}
