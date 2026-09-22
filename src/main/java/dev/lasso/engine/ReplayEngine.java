package dev.lasso.engine;

import dev.lasso.model.Model;
import dev.lasso.model.PropositionSet;
import dev.lasso.model.Transition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReplayEngine {

    private final Model model;

    public ReplayEngine(Model model) {
        this.model = model;
    }

    public Model model() {
        return model;
    }

    public ReplayResult replay(String scenarioId, List<String> stateIds, int entryIndex, int loopEndIndex,
                               String preferredBackEdgeId, boolean fairnessEnabled,
                               List<String> keptVariables, int loopIterations,
                               Map<String, Object> deletedElements) {
        List<StepCheck> steps = new ArrayList<>();
        boolean replayed = true;

        for (int i = 0; i + 1 < loopEndIndex; i++) {
            StepCheck step = checkEdge(stateIds, i, i + 1, false, null, keptVariables);
            steps.add(step);
            if (!step.legal()) {
                replayed = false;
            }
        }
        for (int i = loopEndIndex; i < stateIds.size(); i++) {
            steps.add(new StepCheck(i, stateIds.get(i), null, false, null, null, "TRAILING_NOTE",
                    true, "步 " + i + "：循环段之后的额外记录（" + stateIds.get(i)
                            + "），不参与该入口的循环见证与集合覆盖。"));
        }
        StepCheck back = checkEdge(stateIds, loopEndIndex - 1, entryIndex, true, preferredBackEdgeId, keptVariables);
        steps.add(back);
        if (!back.legal()) {
            replayed = false;
        }

        List<String> effective = stateIds.subList(0, loopEndIndex);
        List<SetCoverage> acceptance = coverage(effective, entryIndex, model.acceptanceSets());
        List<SetCoverage> fairness = coverage(effective, entryIndex, model.fairnessSets());

        boolean accepting = acceptance.stream().anyMatch(SetCoverage::satisfied);
        boolean fair = fairness.stream().allMatch(SetCoverage::satisfied);

        String verdict;
        String explanation;
        boolean violation;
        if (!replayed) {
            verdict = "REPLAY_FAILURE";
            violation = false;
            explanation = "重放失败：前缀或循环回边至少一处缺少模型中的合法转移见证，"
                    + "状态取值相同不能代替转移见证，因此该套索不是合法反例。";
        } else if (!accepting) {
            verdict = "ACCEPTANCE_NOT_INFINITE";
            violation = false;
            boolean seenInPrefix = acceptance.stream().anyMatch(c -> !c.prefixHits().isEmpty());
            explanation = seenInPrefix
                    ? "重放成功，但接受状态只在循环开始前的前缀中出现；前缀中曾经出现不代表无限次命中，活性违例未重现。"
                    : "重放成功，但接受集在该循环上没有任何命中，活性违例未重现。";
        } else if (fairnessEnabled && !fair) {
            verdict = "FAIRNESS_VIOLATED";
            violation = false;
            explanation = "重放成功且接受状态在循环上无限次出现，但启用公平性假设后存在公平集在循环上不被无限次满足"
                    + "（仅前缀出现不算），该执行不是公平执行，反例不成立。";
        } else {
            verdict = "VIOLATION_REPRODUCED";
            violation = true;
            explanation = fairnessEnabled
                    ? "重放成功：回边合法，接受状态在循环上无限次出现，且所有公平集都在循环上被无限次满足，活性违例在公平执行下重现。"
                    : "重放成功：回边合法，接受状态在循环上无限次出现；未启用公平性假设，活性违例重现。";
        }

        LassoView view = new LassoView(List.copyOf(effective), entryIndex, back.witnessTransitionId(),
                loopIterations, keptVariables == null ? null : List.copyOf(keptVariables));
        Map<String, Object> deleted = deletedElements == null ? Map.of() : new LinkedHashMap<>(deletedElements);
        return new ReplayResult(scenarioId, view, fairnessEnabled, steps, acceptance, fairness,
                replayed, violation, verdict, explanation, explanation.length(), deleted);
    }

    private StepCheck checkEdge(List<String> stateIds, int from, int to,
                                boolean backEdge, String preferredId, List<String> keptVariables) {
        String sourceId = stateIds.get(from);
        String targetId = stateIds.get(to);
        Map<String, Object> values = projectedValues(sourceId, keptVariables);

        List<Transition> candidates = new ArrayList<>();
        for (Transition t : model.transitions()) {
            if (t.source().equals(sourceId) && t.target().equals(targetId)) {
                candidates.add(t);
            }
        }

        List<String> enabledWitness = new ArrayList<>();
        Transition chosen = null;
        for (Transition t : candidates) {
            GuardEvaluator.Tri tri = GuardEvaluator.eval(t.guard(), values);
            if (tri == GuardEvaluator.Tri.TRUE) {
                enabledWitness.add(t.id());
                if (chosen == null || t.id().equals(preferredId)) {
                    chosen = t;
                }
            }
        }

        String indexText = backEdge ? "回边" + sourceId + "→" + targetId : "步 " + from + "→" + (from + 1);
        if (chosen != null) {
            return new StepCheck(from, sourceId, targetId, backEdge, chosen.id(), chosen.guard(),
                    "TRUE", true, indexText + "：合法，模型转移 " + chosen.id()
                            + "（卫式 " + chosen.guard() + "）成立。");
        }
        if (candidates.isEmpty()) {
            return new StepCheck(from, sourceId, targetId, backEdge, null, null, "NO_EDGE", false,
                    indexText + "：模型中不存在从 " + sourceId + " 到 " + targetId + " 的转移，状态值相同不能替代转移见证。");
        }
        Transition sample = candidates.get(0);
        GuardEvaluator.Tri tri = GuardEvaluator.eval(sample.guard(), values);
        String status = tri == GuardEvaluator.Tri.UNKNOWN ? "UNKNOWN" : "FALSE";
        String why = tri == GuardEvaluator.Tri.UNKNOWN
                ? "卫式依赖的变量已被删除，剩余变量不足以使其成立"
                : "卫式不成立";
        return new StepCheck(from, sourceId, targetId, backEdge, null, sample.guard(), status, false,
                indexText + "：存在转移 " + sample.id() + "，但" + why + "（卫式 " + sample.guard() + "）。");
    }

    private Map<String, Object> projectedValues(String stateId, List<String> keptVariables) {
        return model.states().stream()
                .filter(s -> s.id().equals(stateId))
                .findFirst()
                .map(s -> {
                    Map<String, Object> all = new LinkedHashMap<>(s.values());
                    if (keptVariables == null) {
                        return all;
                    }
                    all.keySet().retainAll(keptVariables);
                    return all;
                })
                .orElse(Map.of());
    }

    private List<SetCoverage> coverage(List<String> stateIds, int entryIndex, List<PropositionSet> sets) {
        List<SetCoverage> result = new ArrayList<>();
        for (PropositionSet set : sets) {
            List<String> prefixHits = new ArrayList<>();
            List<String> loopHits = new ArrayList<>();
            for (int i = 0; i < stateIds.size(); i++) {
                if (set.memberStateIds().contains(stateIds.get(i))) {
                    if (i >= entryIndex) {
                        if (!loopHits.contains(stateIds.get(i))) {
                            loopHits.add(stateIds.get(i));
                        }
                    } else if (!prefixHits.contains(stateIds.get(i))) {
                        prefixHits.add(stateIds.get(i));
                    }
                }
            }
            String status;
            boolean satisfied;
            if (!loopHits.isEmpty()) {
                status = "INFINITELY_OFTEN";
                satisfied = true;
            } else if (!prefixHits.isEmpty()) {
                status = "PREFIX_ONLY";
                satisfied = false;
            } else {
                status = "NEVER";
                satisfied = false;
            }
            result.add(new SetCoverage(set.id(), set.name(), set.kind(), prefixHits, loopHits, status, satisfied));
        }
        return result;
    }
}
