package com.lasso.engine;

import com.lasso.model.CheckResult;
import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ModelState;
import com.lasso.model.ReductionCandidate;
import com.lasso.model.ReductionReport;
import com.lasso.model.Transition;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 三种不改变违例语义的缩减，每个候选都对原模型重新查验：
 * - state：删除可由合法跨边替代的前缀/循环状态（最少状态数）；
 * - variables：删除任何剩余转移卫式都不引用的变量（最少变量）；
 * - iterations：把重复循环周期折叠到一份（最少循环迭代数）。
 * 同一最优分数的候选全部保留（并列）。最短文本解释不参与淘汰。
 */
public final class ReductionEngine {

    private final Model model;
    private final LassoChecker checker;

    public ReductionEngine(Model model) {
        this.model = model;
        this.checker = new LassoChecker(model);
    }

    // ------------------------------------------------------------------
    // 状态缩减
    // ------------------------------------------------------------------

    public ReductionReport reduceStates(Counterexample ce, String entry, boolean fairnessAssumed) {
        List<String> trace = ce.trace();
        int entryIndex = trace.indexOf(entry);
        if (entryIndex < 0) {
            return empty("state", "最少状态数");
        }
        List<List<Integer>> prefixChains = validChains(
                trace, 0, Math.max(0, entryIndex - 1), Set.of(0));
        List<List<Integer>> cycleChains = cycleChains(
                trace, entryIndex, trace.size() - 1);

        List<ReductionCandidate> best = new ArrayList<>();
        int bestSize = Integer.MAX_VALUE;

        for (List<Integer> prefixChain : prefixChains) {
            for (List<Integer> cycleChain : cycleChains) {
                List<Integer> combined = new ArrayList<>(prefixChain);
                for (int idx : cycleChain) {
                    if (combined.isEmpty() || combined.get(combined.size() - 1) != idx) {
                        combined.add(idx);
                    }
                }
                List<String> keptIds = idsAt(trace, combined);
                CheckResult replay = checker.check(ceWithTrace(ce, keptIds), entry, fairnessAssumed);
                if (!replay.violationReproduced()) {
                    continue;
                }
                int size = keptIds.size();
                ReductionCandidate candidate = new ReductionCandidate(
                        "state-" + combined.toString(),
                        explainStates(trace, combined, prefixChain.size(), cycleChain.size()),
                        keptIds,
                        deletedIds(trace, combined),
                        List.of(),
                        List.of(),
                        1,
                        replay);
                if (size < bestSize) {
                    bestSize = size;
                    best.clear();
                    best.add(candidate);
                } else if (size == bestSize) {
                    best.add(candidate);
                }
            }
        }
        return new ReductionReport("state", "最少状态数", bestSize, best.size() > 1, sorted(best));
    }

    /** 前缀链：以 start 开头、end 结尾，且必须经过 required 下标。 */
    private List<List<Integer>> validChains(List<String> trace, int start, int end, Set<Integer> required) {
        List<List<Integer>> results = new ArrayList<>();
        List<Integer> chain = new ArrayList<>();
        chain.add(start);
        dfs(trace, chain, start, end, required, results, false);
        return results;
    }

    /**
     * 循环链：以入口下标 start 开头，可在 [start, end] 中任意位置收尾，
     * 只要收尾状态存在返回入口的合法转移即可；公平性开启时必须经过 required 下标。
     */
    private List<List<Integer>> cycleChains(List<String> trace, int start, int end) {
        List<List<Integer>> results = new ArrayList<>();
        List<Integer> chain = new ArrayList<>();
        chain.add(start);
        dfs(trace, chain, start, end, Set.of(start), results, true);
        return results;
    }

    private void dfs(List<String> trace, List<Integer> chain, int from, int end,
                     Set<Integer> required, List<List<Integer>> results,
                     boolean allowEarlyClosure) {
        Set<Integer> remainingRequired = new LinkedHashSet<>();
        for (int idx : required) {
            if (idx > from) {
                remainingRequired.add(idx);
            }
        }
        if (allowEarlyClosure) {
            if (checker.replayEngine().canReach(trace.get(from), trace.get(chain.get(0)))
                    && remainingRequired.isEmpty()) {
                results.add(List.copyOf(chain));
            }
        } else if (from == end && remainingRequired.isEmpty()) {
            results.add(List.copyOf(chain));
        }
        for (int next = from + 1; next <= end; next++) {
            if (!checker.replayEngine().canReach(trace.get(from), trace.get(next))) {
                continue;
            }
            boolean skipsRequired = false;
            for (int req : remainingRequired) {
                if (req < next) {
                    skipsRequired = true;
                    break;
                }
            }
            if (skipsRequired) {
                continue;
            }
            chain.add(next);
            dfs(trace, chain, next, end, required, results, allowEarlyClosure);
            chain.remove(chain.size() - 1);
        }
    }

    // ------------------------------------------------------------------
    // 变量缩减
    // ------------------------------------------------------------------

    public ReductionReport reduceVariables(Counterexample ce, String entry, boolean fairnessAssumed) {
        List<String> trace = ce.trace();
        int entryIndex = trace.indexOf(entry);
        if (entryIndex < 0) {
            return empty("variables", "最少变量数");
        }

        List<EdgeRef> edges = lassoEdges(trace, entryIndex);
        List<String> allVariables = new ArrayList<>(model.state(trace.get(0)).variables().keySet());
        for (String stateId : trace) {
            for (String name : model.state(stateId).variables().keySet()) {
                if (!allVariables.contains(name)) {
                    allVariables.add(name);
                }
            }
        }

        List<List<String>> viableSubsets = new ArrayList<>();
        for (int size = 0; size <= allVariables.size(); size++) {
            List<List<String>> atSize = new ArrayList<>();
            enumerateSubsets(allVariables, size, 0, new ArrayList<>(), atSize, edges);
            if (!atSize.isEmpty()) {
                viableSubsets = atSize;
                break;
            }
        }

        List<ReductionCandidate> candidates = new ArrayList<>();
        for (List<String> keptVariables : viableSubsets) {
            CheckResult replay = checker.check(ce, entry, fairnessAssumed);
            candidates.add(new ReductionCandidate(
                    "variables-" + keptVariables,
                    explainVariables(keptVariables, delete(allVariables, keptVariables), edges),
                    trace,
                    List.of(),
                    List.copyOf(keptVariables),
                    delete(allVariables, keptVariables),
                    countIterations(trace, entryIndex),
                    replay));
        }
        int best = viableSubsets.isEmpty() ? allVariables.size() : viableSubsets.get(0).size();
        return new ReductionReport("variables", "最少变量数", best, candidates.size() > 1,
                sorted(candidates));
    }

    private List<EdgeRef> lassoEdges(List<String> trace, int entryIndex) {
        List<EdgeRef> edges = new ArrayList<>();
        for (int i = 0; i < trace.size() - 1; i++) {
            edges.add(new EdgeRef(trace.get(i), trace.get(i + 1)));
        }
        edges.add(new EdgeRef(trace.get(trace.size() - 1), trace.get(entryIndex)));
        return edges;
    }

    private void enumerateSubsets(List<String> all, int size, int start,
                                  List<String> current, List<List<String>> found,
                                  List<EdgeRef> edges) {
        if (current.size() == size) {
            if (subsetSufficient(current, edges)) {
                found.add(List.copyOf(current));
            }
            return;
        }
        for (int i = start; i < all.size(); i++) {
            current.add(all.get(i));
            enumerateSubsets(all, size, i + 1, current, found, edges);
            current.remove(current.size() - 1);
        }
    }

    private boolean subsetSufficient(List<String> keptVariables, List<EdgeRef> edges) {
        for (EdgeRef edge : edges) {
            ModelState source = model.state(edge.from());
            if (source == null || model.state(edge.to()) == null) {
                return false;
            }
            boolean ok = false;
            for (Transition t : model.transitions()) {
                if (!t.source().equals(edge.from()) || !t.target().equals(edge.to())) {
                    continue;
                }
                List<String> referenced = GuardEvaluator.referencedVariables(t.guard());
                if (!keptVariables.containsAll(referenced)) {
                    continue;
                }
                if (GuardEvaluator.isEnabled(t.guard(), source.variables())) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 循环迭代缩减
    // ------------------------------------------------------------------

    public ReductionReport reduceIterations(Counterexample ce, String entry, boolean fairnessAssumed) {
        List<String> trace = ce.trace();
        int entryIndex = trace.indexOf(entry);
        if (entryIndex < 0) {
            return empty("iterations", "最少循环迭代数");
        }
        int cycleLen = trace.size() - entryIndex;
        List<Integer> periods = new ArrayList<>();
        for (int p = 1; p <= cycleLen; p++) {
            if (isPeriod(trace, entryIndex, p)) {
                periods.add(p);
            }
        }
        List<ReductionCandidate> best = new ArrayList<>();
        int bestLen = Integer.MAX_VALUE;
        for (int period : periods) {
            List<Integer> keptIndices = new ArrayList<>();
            for (int i = 0; i < entryIndex + period; i++) {
                keptIndices.add(i);
            }
            List<String> reduced = idsAt(trace, keptIndices);
            CheckResult replay = checker.check(ceWithTrace(ce, reduced), entry, fairnessAssumed);
            if (!replay.violationReproduced()) {
                continue;
            }
            int len = reduced.size();
            ReductionCandidate candidate = new ReductionCandidate(
                    "iterations-p" + period,
                    explainIterations(period, cycleLen),
                    reduced,
                    deletedIds(trace, keptIndices),
                    List.of(),
                    List.of(),
                    1,
                    replay);
            if (len < bestLen) {
                bestLen = len;
                best.clear();
                best.add(candidate);
            } else if (len == bestLen) {
                best.add(candidate);
            }
        }
        if (best.isEmpty()) {
            return empty("iterations", "最少循环迭代数");
        }
        return new ReductionReport("iterations", "最少循环迭代数", 1, best.size() > 1, sorted(best));
    }

    private boolean isPeriod(List<String> trace, int entryIndex, int p) {
        int cycleLen = trace.size() - entryIndex;
        if (p >= cycleLen) {
            return false;
        }
        for (int i = entryIndex; i + p < trace.size(); i++) {
            if (!trace.get(i).equals(trace.get(i + p))) {
                return false;
            }
        }
        ModelState periodEnd = model.state(trace.get(entryIndex + p - 1));
        return periodEnd != null
                && checker.replayEngine().canReach(periodEnd.stateId(), trace.get(entryIndex));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private Counterexample ceWithTrace(Counterexample ce, List<String> trace) {
        List<Counterexample.EntryCandidate> entries = ce.entries().stream()
                .filter(e -> e.stateId().equals(trace.get(trace.size() - 1))
                        || trace.contains(e.stateId()))
                .toList();
        return new Counterexample(ce.id(), ce.modelId(), ce.name(), ce.propertyKind(),
                ce.description(), trace, entries);
    }

    private List<String> idsAt(List<String> trace, List<Integer> indices) {
        List<String> ids = new ArrayList<>();
        for (int idx : indices) {
            ids.add(trace.get(idx));
        }
        return ids;
    }

    private List<String> deletedIds(List<String> trace, List<Integer> keptIndices) {
        BitSet kept = new BitSet();
        keptIndices.forEach(kept::set);
        List<String> deleted = new ArrayList<>();
        for (int i = 0; i < trace.size(); i++) {
            if (!kept.get(i)) {
                deleted.add(trace.get(i));
            }
        }
        return deleted;
    }

    private List<String> delete(List<String> all, List<String> kept) {
        List<String> deleted = new ArrayList<>();
        for (String item : all) {
            if (!kept.contains(item)) {
                deleted.add(item);
            }
        }
        return deleted;
    }

    private int countIterations(List<String> trace, int entryIndex) {
        int cycleLen = trace.size() - entryIndex;
        for (int p = 1; p < cycleLen; p++) {
            if (isPeriod(trace, entryIndex, p)) {
                return Math.max(2, cycleLen / p);
            }
        }
        return 1;
    }

    private String explainStates(List<String> trace, List<Integer> kept,
                                 int prefixCount, int cycleCount) {
        int removed = trace.size() - kept.size();
        return "状态缩减：保留前缀 " + prefixCount + " 个、循环 " + cycleCount
                + " 个状态；删除 " + removed + " 个可由合法跨边替代的状态，"
                + "环末仍有返回入口的转移见证，接受集覆盖与公平性结论不变。";
    }

    private String explainVariables(List<String> kept, List<String> deleted, List<EdgeRef> edges) {
        return "变量缩减：剩余套索的 " + edges.size()
                + " 条转移见证（含返回入口边）只引用变量 " + String.join(", ", kept)
                + "；删除无见证引用的变量 "
                + (deleted.isEmpty() ? "（无）" : String.join(", ", deleted))
                + "，卫式取值与重放结论不变。";
    }

    private String explainIterations(int period, int cycleLen) {
        return "循环迭代缩减：循环每 " + period + " 个状态重复一次（原循环含 "
                + cycleLen + " 个状态），折叠为单一规范周期；末状态仍有返回入口的合法转移，无限次接受/公平结论不变。";
    }

    private List<ReductionCandidate> sorted(List<ReductionCandidate> candidates) {
        candidates.sort(Comparator.comparing(ReductionCandidate::key));
        return candidates;
    }

    private ReductionReport empty(String kind, String label) {
        return new ReductionReport(kind, label, -1, false, List.of());
    }

    private record EdgeRef(String from, String to) {
    }
}
