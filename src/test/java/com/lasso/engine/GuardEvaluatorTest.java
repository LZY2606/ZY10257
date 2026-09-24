package com.lasso.engine;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardEvaluatorTest {

    private Map<String, Object> vars(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void evaluatesComparisonsAndBooleans() {
        Map<String, Object> env = vars("x", 1L, "y", 0L, "z", 3L, "flag", false);
        assertTrue(GuardEvaluator.isEnabled("x == 1 && y == 0", env));
        // !flag == !(false) == true；flag 为 false 时整体为 true
        assertTrue(GuardEvaluator.isEnabled("x == 1 && !flag", env));
        assertFalse(GuardEvaluator.isEnabled("x == 1 && flag", env));
        assertTrue(GuardEvaluator.isEnabled("x >= 1 || z < 1", env));
        assertTrue(GuardEvaluator.isEnabled("z == 1 + 2", env));
        assertTrue(GuardEvaluator.isEnabled("!(x != 1)", env));
        assertTrue(GuardEvaluator.isEnabled("", Map.of()));
        assertTrue(GuardEvaluator.isEnabled("   ", Map.of()));
    }

    @Test
    void undefinedVariablesFailClosed() {
        assertFalse(GuardEvaluator.isEnabled("flag == true", Map.of()));
        assertTrue(GuardEvaluator.isEnabled("flag == true", Map.of("flag", true)));
    }

    @Test
    void collectsReferencedVariables() {
        assertEquals(java.util.List.of("x", "y", "flag"),
                GuardEvaluator.referencedVariables("x >= 1 && y == 0 || flag"));
        assertEquals(java.util.List.of(), GuardEvaluator.referencedVariables(""));
    }
}
