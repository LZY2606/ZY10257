package com.lasso.engine;

import com.lasso.model.Model;
import com.lasso.model.ModelState;
import com.lasso.model.ReplayResult;
import com.lasso.model.Transition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对状态序列做“原模型重放”。
 * 相邻状态之间必须在原模型中存在一条源/目标匹配且卫式在源状态成立的转移；
 * 状态值相同并不能替代转移见证（依据是 stateId 与模型转移表）。
 */
public final class ReplayEngine {

    private final Model model;

    public ReplayEngine(Model model) {
        this.model = model;
    }

    public ReplayResult replay(List<String> sequence) {
        return replay(sequence, new LinkedHashSet<>());
    }

    /**
     * @param closureTargets 允许序列最后一个状态回到这些目标的“收尾边”候选。
     *                       套索查验时传入 {entry}，要求末状态存在返回入口的合法转移。
     */
    public ReplayResult replay(List<String> sequence, Set<String> closureTargets) {
        List<String> witnesses = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (sequence.isEmpty()) {
            return ReplayResult.fail(sequence, witnesses, missing, "状态序列为空");
        }
        ModelState first = model.state(sequence.get(0));
        if (first == null) {
            return ReplayResult.fail(sequence, witnesses, missing,
                    "起始状态不在原模型中: " + sequence.get(0));
        }

        for (int i = 0; i < sequence.size(); i++) {
            ModelState source = model.state(sequence.get(i));
            if (source == null) {
                return ReplayResult.fail(sequence, witnesses, missing,
                        "状态不在原模型中: " + sequence.get(i));
            }
            if (i + 1 < sequence.size()) {
                Transition witness = findEnabled(source, sequence.get(i + 1));
                if (witness == null) {
                    String edge = sequence.get(i) + "->" + sequence.get(i + 1);
                    missing.add(edge);
                    return ReplayResult.fail(sequence, witnesses, missing,
                            "缺少从 " + source.stateId() + " 到 " + sequence.get(i + 1)
                                    + " 的合法转移（卫式不成立或转移不存在）");
                }
                witnesses.add(witness.source() + "->" + witness.target()
                        + " [" + witness.guard() + "]");
            } else if (!closureTargets.isEmpty()) {
                Transition closure = null;
                String closureTarget = null;
                for (String target : closureTargets) {
                    Transition candidate = findEnabled(source, target);
                    if (candidate != null) {
                        closure = candidate;
                        closureTarget = target;
                        break;
                    }
                    missing.add(source.stateId() + "->" + target);
                }
                if (closure == null) {
                    return ReplayResult.fail(sequence, witnesses, missing,
                            "环的最后状态 " + source.stateId()
                                    + " 不存在返回入口的合法转移，状态值相同不能替代转移见证，套索不闭合");
                }
                witnesses.add(closure.source() + "->" + closureTarget
                        + " [" + closure.guard() + "]  // 返回循环入口");
            }
        }
        return ReplayResult.ok(sequence, witnesses);
    }

    /** 只问可达性，不记录见证明细。 */
    public boolean canReach(String fromState, String toState) {
        ModelState source = model.state(fromState);
        if (source == null) {
            return false;
        }
        return findEnabled(source, toState) != null;
    }

    /** 返回从 source 到 target 的所有可触发转移（用于变量缩减枚举备选见证）。 */
    public List<Transition> enabledEdges(String fromState, String toState) {
        ModelState source = model.state(fromState);
        List<Transition> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Transition t : model.transitions()) {
            if (t.source().equals(fromState) && t.target().equals(toState)
                    && GuardEvaluator.isEnabled(t.guard(), source.variables())) {
                result.add(t);
            }
        }
        return result;
    }

    private Transition findEnabled(ModelState source, String target) {
        Transition fallback = null;
        for (Transition t : model.transitions()) {
            if (!t.source().equals(source.stateId()) || !t.target().equals(target)) {
                continue;
            }
            boolean enabled = GuardEvaluator.isEnabled(t.guard(), source.variables());
            if (enabled && t.guard() != null && !t.guard().isBlank()) {
                return t;
            }
            if (enabled && fallback == null) {
                fallback = t;
            }
        }
        return fallback;
    }
}
