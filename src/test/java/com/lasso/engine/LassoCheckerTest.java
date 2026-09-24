package com.lasso.engine;

import com.lasso.model.CheckResult;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.persistence.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LassoCheckerTest {

    private Model model;
    private Counterexample ce;
    private LassoChecker checker;

    @BeforeEach
    void setUp() {
        model = Fixtures.modelOne();
        ce = Fixtures.ceOne();
        checker = new LassoChecker(model);
    }

    @Test
    @DisplayName("入口 s4：无公平性与启用公平性都复现违例")
    void entryS4ReproducesViolation() {
        CheckResult withoutFairness = checker.check(ce, "s4", false);
        assertTrue(withoutFairness.replay().valid());
        assertTrue(withoutFairness.acceptCovered().contains("ACC"));
        assertTrue(withoutFairness.violationReproduced());

        CheckResult withFairness = checker.check(ce, "s4", true);
        assertTrue(withFairness.fairSatisfied().contains("FAIR"));
        assertTrue(withFairness.violationReproduced());
    }

    @Test
    @DisplayName("入口 s6：无公平性成立；启用公平性后 FAIR 仅前缀出现，不算无限满足")
    void entryS6PrefixFairnessDoesNotCount() {
        CheckResult withoutFairness = checker.check(ce, "s6", false);
        assertTrue(withoutFairness.violationReproduced());

        CheckResult withFairness = checker.check(ce, "s6", true);
        assertFalse(withFairness.violationReproduced());
        assertTrue(withFairness.fairUnsatisfied().contains("FAIR"));
        assertTrue(withFairness.verdict().contains("前缀出现不计"));
    }

    @Test
    @DisplayName("环末必须存在返回入口的合法转移；状态值相同不能替代见证（s4b 非法）")
    void equalValuesDoNotReplaceTransitionWitness() {
        CheckResult result = checker.check(ce, "s4b", false);
        assertFalse(result.replay().valid());
        assertFalse(result.violationReproduced());
        assertTrue(result.replay().failureReason().contains("循环入口")
                || result.replay().failureReason().contains("转移"));

        // 手工构造以 s4b 为入口的轨迹：变量赋值与 s4 相同，但没有任何转移连到/回到 s4b
        Counterexample fake = new Counterexample(
                ce.id(), ce.modelId(), ce.name(), ce.propertyKind(), ce.description(),
                List.of("s0", "s1", "s3", "s4b", "s5", "s6", "s7"), ce.entries());
        CheckResult noClosure = new LassoChecker(model).check(fake, "s4b", false);
        assertFalse(noClosure.replay().valid());
        assertTrue(noClosure.replay().missingEdges().stream().anyMatch(e -> e.contains("s3->s4b"))
                || noClosure.replay().failureReason().contains("s3"));
    }

    @Test
    @DisplayName("未知入口直接判定无效")
    void unknownEntryIsInvalid() {
        CheckResult result = checker.check(ce, "nope", false);
        assertFalse(result.violationReproduced());
        assertFalse(result.replay().valid());
    }
}
