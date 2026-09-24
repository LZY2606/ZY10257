package com.lasso.engine;

import com.lasso.model.CheckResult;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ModelState;
import com.lasso.model.ReplayResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 活性反例（Büchi 套索）查验：
 * 1) 前缀可以沿模型重放，且环末状态存在返回入口的合法转移；
 * 2) 接受集在循环状态上的覆盖必须完整；
 * 3) 开启公平性时，每个公平集必须在循环状态中至少出现一次（代表无限次满足）；
 *    前缀中曾经出现不算无限次满足。
 */
public final class LassoChecker {

    private final Model model;
    private final ReplayEngine replay;

    public LassoChecker(Model model) {
        this.model = model;
        this.replay = new ReplayEngine(model);
    }

    public ReplayEngine replayEngine() {
        return replay;
    }

    public CheckResult check(Counterexample ce, String entry, boolean fairnessAssumed) {
        List<String> trace = ce.trace();
        int entryIndex = trace.indexOf(entry);
        if (entryIndex < 0) {
            return invalid(ce.id(), entry, fairnessAssumed,
                    ReplayResult.fail(trace, List.of(), List.of("entry:" + entry),
                            "循环入口不在反例轨迹中: " + entry),
                    collectUniverse(true), collectUniverse(false),
                    "循环入口 " + entry + " 不在反例轨迹中");
        }

        ReplayResult replayResult = replay.replay(new ArrayList<>(trace),
                new LinkedHashSet<>(List.of(entry)));
        List<String> acceptUniverse = collectUniverse(true);
        List<String> fairUniverse = collectUniverse(false);
        if (!replayResult.valid()) {
            return invalid(ce.id(), entry, fairnessAssumed, replayResult,
                    acceptUniverse, fairUniverse, replayResult.failureReason());
        }

        List<ModelState> cycleStates = trace.subList(entryIndex, trace.size()).stream()
                .map(model::state).toList();

        List<String> acceptCovered = collectCycleSets(cycleStates, true);
        List<String> acceptMissing = subtract(acceptUniverse, acceptCovered);

        List<String> fairCovered = collectCycleSets(cycleStates, false);
        List<String> fairMissing = subtract(fairUniverse, fairCovered);

        List<String> fairShown = fairnessAssumed ? fairCovered : List.of();
        List<String> fairUnsatisfied = fairnessAssumed ? fairMissing : List.of();

        boolean violation = acceptMissing.isEmpty()
                && (!fairnessAssumed || fairMissing.isEmpty());

        String verdict;
        if (!acceptMissing.isEmpty()) {
            verdict = "接受集在循环上覆盖不全（缺少 " + String.join(", ", acceptMissing) + "）";
        } else if (fairnessAssumed && !fairMissing.isEmpty()) {
            verdict = "开启公平性后，公平集 " + String.join(", ", fairMissing)
                    + " 未在循环上无限满足（前缀出现不计），该入口不是违例";
        } else {
            verdict = "反例复现违例：前缀可重放、环末存在返回入口的合法转移、接受集覆盖完整"
                    + (fairnessAssumed ? "、公平性在循环上满足" : "（未启用公平性假设）");
        }

        return new CheckResult(ce.id(), entry, fairnessAssumed, replayResult,
                acceptUniverse, acceptCovered, acceptMissing,
                fairUniverse, fairShown, fairUnsatisfied,
                violation, verdict);
    }

    private CheckResult invalid(String ceId, String entry, boolean fairnessAssumed,
                                ReplayResult replayResult,
                                List<String> acceptUniverse, List<String> fairUniverse,
                                String verdict) {
        return new CheckResult(ceId, entry, fairnessAssumed, replayResult,
                acceptUniverse, List.of(), acceptUniverse,
                fairUniverse, List.of(), fairnessAssumed ? fairUniverse : List.of(),
                false, "重放失败：" + verdict);
    }

    private List<String> collectUniverse(boolean accept) {
        Set<String> all = new LinkedHashSet<>();
        for (ModelState s : model.states()) {
            all.addAll(accept ? s.acceptSets() : s.fairSets());
        }
        return new ArrayList<>(all);
    }

    private List<String> collectCycleSets(List<ModelState> cycleStates, boolean accept) {
        Set<String> covered = new LinkedHashSet<>();
        for (ModelState s : cycleStates) {
            if (s != null) {
                covered.addAll(accept ? s.acceptSets() : s.fairSets());
            }
        }
        return new ArrayList<>(covered);
    }

    private List<String> subtract(List<String> universe, List<String> covered) {
        List<String> result = new ArrayList<>();
        for (String item : universe) {
            if (!covered.contains(item)) {
                result.add(item);
            }
        }
        return result;
    }
}
