package com.lasso.engine;

import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ReductionCandidate;
import com.lasso.model.ReductionReport;
import com.lasso.persistence.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReductionEngineTest {

    private final Model m1 = Fixtures.modelOne();
    private final Counterexample ce1 = Fixtures.ceOne();
    private final Model m2 = Fixtures.modelTwo();
    private final Counterexample ce2 = Fixtures.ceTwo();

    @Test
    @DisplayName("状态缩减：s4 上最少状态候选严格变短、仍复现违例，且并列候选全部保留")
    void stateReductionKeepsTiedCandidates() {
        ReductionReport report = new ReductionEngine(m1).reduceStates(ce1, "s4", false);
        assertTrue(report.candidates().size() >= 2, "两条等长跨边链应产生并列候选");
        assertTrue(report.tie());
        assertTrue(report.bestMetric() < ce1.trace().size());
        for (ReductionCandidate c : report.candidates()) {
            assertTrue(c.replay().violationReproduced());
            assertTrue(c.keptStates().contains("s4"));
            // 两个等长循环链分别以接受状态 s7 或 s8 收尾，都覆盖 ACC
            assertTrue(c.keptStates().contains("s7") || c.keptStates().contains("s8"));
            assertEquals(c.keptStates().size(), report.bestMetric());
            // 环末必须保留返回入口 s4 的闭合转移见证（s7 或 s8 收尾）
            assertTrue(c.replay().replay().witnesses().stream()
                    .anyMatch(w -> w.startsWith("s7->s4 ") || w.startsWith("s8->s4 ")));
            assertTrue(c.replay().replay().witnesses().stream()
                    .anyMatch(w -> w.contains("返回循环入口")));
        }
        // 并列候选的删除集合不同（走不同前缀链）
        long distinctDeletions = report.candidates().stream()
                .map(c -> String.join(",", c.deletedStates())).distinct().count();
        assertTrue(distinctDeletions >= 2);
    }

    @Test
    @DisplayName("状态缩减尊重公平性：入口 s6 开启公平性后不存在保持违例的缩减候选")
    void stateReductionUnderFairness() {
        ReductionReport ok = new ReductionEngine(m1).reduceStates(ce1, "s6", false);
        assertTrue(ok.bestMetric() > 0);

        ReductionReport unfair = new ReductionEngine(m1).reduceStates(ce1, "s6", true);
        assertTrue(unfair.candidates().isEmpty(),
                "FAIR 只在 s6 前缀出现，开启公平性后任何缩减都不能复现违例");
    }

    @Test
    @DisplayName("变量缩减：删除任何见证卫式都不引用的变量 w/flag，保留最少变量候选")
    void variableReductionRemovesDeadVariables() {
        ReductionReport report = new ReductionEngine(m1).reduceVariables(ce1, "s4", false);
        assertFalse(report.candidates().isEmpty());
        for (ReductionCandidate c : report.candidates()) {
            assertTrue(c.replay().violationReproduced());
            assertFalse(c.keptVariables().contains("w"),
                    "w 不被任何套索见证引用，应被删除");
            assertFalse(c.keptVariables().contains("flag"));
            assertTrue(c.keptVariables().containsAll(List.of("x", "y")));
            assertTrue(c.deletedVariables().contains("w"));
        }
        assertTrue(report.bestMetric() < 5);
    }

    @Test
    @DisplayName("迭代缩减：CE2 的重复周期折叠为 a0-a1-a2，且仍复现违例")
    void iterationReductionCollapsesRepeatedPeriod() {
        ReductionReport report = new ReductionEngine(m2).reduceIterations(ce2, "a1", false);
        assertEquals(1, report.candidates().size());
        ReductionCandidate c = report.candidates().get(0);
        assertEquals(List.of("a0", "a1", "a2"), c.keptStates());
        assertEquals(List.of("a1", "a2"), c.deletedStates());
        assertTrue(c.replay().violationReproduced());
        assertTrue(c.replay().replay().witnesses().stream()
                .anyMatch(w -> w.startsWith("a2->a1")));
        assertEquals(1, c.loopIterations());
    }
}
