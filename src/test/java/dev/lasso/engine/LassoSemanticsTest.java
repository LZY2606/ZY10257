package dev.lasso.engine;

import dev.lasso.fixture.FixtureService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LassoSemanticsTest {

    private static final int ENTRY_A = 9;
    private static final int ENTRY_B = 6;

    private ReductionEngine reduction;
    private ReplayEngine replay;

    @BeforeAll
    void setUp() {
        FixtureService fixture = new FixtureService();
        fixture.load();
        replay = new ReplayEngine(fixture.getModel());
        reduction = new ReductionEngine(replay);
    }

    @Test
    void entryAWithoutFairnessReproducesViolation() {
        ReplayResult r = reduction.baseline(ENTRY_A, false);
        assertTrue(r.replayed());
        assertTrue(r.violationReproduced());
        assertEquals("VIOLATION_REPRODUCED", r.verdict());
        assertEquals("INFINITELY_OFTEN", r.acceptanceCoverage().get(0).status());
    }

    @Test
    void entryAWithFairnessIsFairAndStillReproduces() {
        ReplayResult r = reduction.baseline(ENTRY_A, true);
        assertTrue(r.replayed());
        assertTrue(r.violationReproduced());
        assertEquals("INFINITELY_OFTEN", r.fairnessCoverage().get(0).status());
    }

    @Test
    void entryBAcceptanceLoopsButFairnessOnlyAppearedInPrefix() {
        ReplayResult r = reduction.baseline(ENTRY_B, true);
        assertTrue(r.replayed());
        assertFalse(r.violationReproduced());
        assertEquals("FAIRNESS_VIOLATED", r.verdict());
        SetCoverage fair = r.fairnessCoverage().get(0);
        assertEquals("PREFIX_ONLY", fair.status());
        assertTrue(fair.prefixHits().contains("L3"));
        assertTrue(fair.loopHits().isEmpty());
    }

    @Test
    void entryBWithoutFairnessReproducesViolation() {
        ReplayResult r = reduction.baseline(ENTRY_B, false);
        assertTrue(r.violationReproduced());
        assertEquals("INFINITELY_OFTEN", r.acceptanceCoverage().get(0).status());
    }

    @Test
    void backEdgeWitnessIsRequiredAndChosenFromModelTransitions() {
        ReplayResult r = replay.replay("witness", new java.util.ArrayList<>(replay.model().trace()), ENTRY_A, 15,
                "e53", false,
                replay.model().variables().stream().map(v -> v.name()).toList(), 2,
                new java.util.LinkedHashMap<>());
        StepCheck back = r.steps().get(r.steps().size() - 1);
        assertTrue(back.backEdge());
        assertTrue(back.legal());
        assertEquals("e53", back.witnessTransitionId());
    }

    @Test
    void missingBackEdgeFailsReplay() {
        java.util.List<String> broken = java.util.List.of("L6", "L0", "L1", "L3");
        ReplayResult r = replay.replay("broken", broken, 0, 4, null, false,
                replay.model().variables().stream().map(v -> v.name()).toList(), 1,
                new java.util.LinkedHashMap<>());
        assertFalse(r.replayed());
        assertEquals("REPLAY_FAILURE", r.verdict());
        StepCheck back = r.steps().get(r.steps().size() - 1);
        assertFalse(back.legal());
        assertEquals("NO_EDGE", back.guardStatus());
    }

    @Test
    void stateReductionKeepsAllTiedMinimumCandidates() {
        ReductionResult result = reduction.reduce(ReductionKind.STATE, ENTRY_A, false);
        assertTrue(result.candidates().size() >= 2, "最少状态数下的并列候选必须保留");
        for (ReductionCandidate c : result.candidates()) {
            assertEquals(14, c.score());
            assertTrue(c.replay().violationReproduced());
            @SuppressWarnings("unchecked")
            java.util.List<String> deleted = (java.util.List<String>)
                    c.replay().deletedElements().get("deletedStates");
            assertEquals(1, deleted.size());
            assertTrue(deleted.get(0).equals("L0") || deleted.get(0).equals("L1"));
        }
    }

    @Test
    void variableReductionKeepsTiedCandidatesAndNoiseIsAlwaysRemoved() {
        ReductionResult result = reduction.reduce(ReductionKind.VARIABLE, ENTRY_A, false);
        assertTrue(result.candidates().size() >= 2);
        for (ReductionCandidate c : result.candidates()) {
            assertEquals(3, c.score());
            java.util.List<String> kept = c.replay().lasso().keptVariables();
            assertTrue(kept.contains("req"));
            assertTrue(kept.contains("tok"));
            assertTrue(kept.contains("a") || kept.contains("b"));
            assertFalse(kept.contains("noise"));
            assertTrue(c.replay().violationReproduced());
        }
    }

    @Test
    void droppingGuardVariableBreaksReplay() {
        java.util.List<String> noReq = java.util.List.of("pc", "tok", "a");
        ReplayResult r = replay.replay("dropped", new java.util.ArrayList<>(replay.model().trace()), ENTRY_A, 15,
                "e53", false, noReq, 2, new java.util.LinkedHashMap<>());
        assertFalse(r.replayed());
        assertTrue(r.steps().stream().anyMatch(s -> "UNKNOWN".equals(s.guardStatus())));
    }

    @Test
    void iterationReductionCollapsesRepeatedLoopBlocks() {
        ReductionResult result = reduction.reduce(ReductionKind.ITERATION, ENTRY_A, false);
        assertEquals(1, result.candidates().size());
        ReductionCandidate c = result.candidates().get(0);
        assertEquals(1, c.score());
        assertEquals(12, c.replay().lasso().stateIds().size());
        assertTrue(c.replay().violationReproduced());
        assertEquals(1, c.replay().deletedElements().get("removedIterationBlocks"));
    }

    @Test
    void iterationReductionUnavailableWhenOnlyOneLoopRecorded() {
        ReductionResult result = reduction.reduce(ReductionKind.ITERATION, ENTRY_B, false);
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void stateReductionForEntryBPreservesFairnessVerdictAndKeepsTies() {
        ReductionResult result = reduction.reduce(ReductionKind.STATE, ENTRY_B, true);
        assertTrue(result.candidates().size() >= 2);
        for (ReductionCandidate c : result.candidates()) {
            assertEquals(8, c.score());
            assertFalse(c.replay().violationReproduced());
            assertEquals("FAIRNESS_VIOLATED", c.replay().verdict());
        }
    }
}
