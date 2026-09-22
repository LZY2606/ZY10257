package dev.lasso.engine;

import dev.lasso.model.Model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 三种缩减各自独立打分：
 * STATE=剩余状态数；VARIABLE=保留变量数；ITERATION=剩余循环迭代数。
 * 每种缩减在最优分数下保留全部并列候选；另用最短文本解释长度做独立标注。
 * 每个候选都在原模型（固定夹具）上重新重放，只有 verdict 与基线一致才接受。
 */
public final class ReductionEngine {

    private final ReplayEngine replayEngine;
    private final Model model;

    public ReductionEngine(ReplayEngine replayEngine) {
        this.replayEngine = replayEngine;
        this.model = replayEngine.model();
    }

    public ReplayResult baseline(int entryIndex, boolean fairnessEnabled) {
        return replayBaseline(entryIndex, fairnessEnabled, new ArrayList<>(model.trace()),
                allVariables(), new LinkedHashMap<>());
    }

    public ReductionResult reduce(ReductionKind kind, int entryIndex, boolean fairnessEnabled) {
        ReplayResult base = baseline(entryIndex, fairnessEnabled);
        List<ReductionCandidate> candidates = switch (kind) {
            case STATE -> reduceStates(entryIndex, fairnessEnabled, base);
            case VARIABLE -> reduceVariables(entryIndex, fairnessEnabled, base);
            case ITERATION -> reduceIterations(entryIndex, fairnessEnabled, base);
        };
        markShortest(candidates);
        return new ReductionResult(kind, entryIndex, fairnessEnabled, candidates, base);
    }

    private int loopPeriod(int entryIndex) {
        return model.loopEntries().stream()
                .filter(e -> e.index() == entryIndex).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知循环入口 " + entryIndex))
                .loopPeriod();
    }

    private String backEdgeId(int entryIndex) {
        return model.loopEntries().stream()
                .filter(e -> e.index() == entryIndex).findFirst()
                .map(e -> e.backEdgeTransitionId()).orElse(null);
    }

    private List<String> allVariables() {
        return model.variables().stream().map(v -> v.name()).toList();
    }

    private int recordedIterations(List<String> trace, int entryIndex) {
        return repeatsOfPeriod(trace, entryIndex, loopPeriod(entryIndex));
    }

    private int repeatsOfPeriod(List<String> trace, int entryIndex, int period) {
        List<String> first = trace.subList(entryIndex, entryIndex + period);
        int repeats = 1;
        while (entryIndex + (repeats + 1) * period <= trace.size()) {
            int start = entryIndex + repeats * period;
            if (!trace.subList(start, start + period).equals(first)) {
                break;
            }
            repeats++;
        }
        return repeats;
    }

    private ReplayResult replayBaseline(int entryIndex, boolean fairnessEnabled, List<String> trace,
                                        List<String> keptVariables, Map<String, Object> deleted) {
        int iterations = recordedIterations(trace, entryIndex);
        int period = loopPeriod(entryIndex);
        int loopEnd = entryIndex + period * iterations;
        return replayEngine.replay("replay", trace, entryIndex, loopEnd,
                backEdgeId(entryIndex), fairnessEnabled, keptVariables, iterations, deleted);
    }

    private List<ReductionCandidate> reduceStates(int entryIndex, boolean fairnessEnabled, ReplayResult base) {
        List<Integer> removablePrefix = new ArrayList<>();
        for (int i = 1; i < entryIndex; i++) {
            removablePrefix.add(i);
        }
        List<ReplayResult> accepted = new ArrayList<>();
        for (int size = 1; size <= removablePrefix.size(); size++) {
            if (!accepted.isEmpty()) {
                break;
            }
            for (List<Integer> removed : combinations(removablePrefix, size)) {
                List<String> reduced = new ArrayList<>();
                List<String> deletedStates = new ArrayList<>();
                Set<Integer> removedSet = new LinkedHashSet<>(removed);
                for (int i = 0; i < model.trace().size(); i++) {
                    if (removedSet.contains(i)) {
                        deletedStates.add(model.trace().get(i));
                    } else {
                        reduced.add(model.trace().get(i));
                    }
                }
                int newEntry = entryIndex - (int) removed.stream().filter(i -> i < entryIndex).count();
                int period = loopPeriod(entryIndex);
                int iterations = repeatsOfPeriod(reduced, newEntry, period);
                int loopEnd = newEntry + period * iterations;
                Map<String, Object> deleted = new LinkedHashMap<>();
                deleted.put("deletedStates", deletedStates);
                deleted.put("rule", "仅删除循环开始前的前缀内部状态，循环入口与循环锚点不动；相邻状态之间必须在原模型上有合法转移。");
                ReplayResult r = replayEngine.replay("state-reduction", reduced, newEntry, loopEnd,
                        backEdgeId(entryIndex), fairnessEnabled, allVariables(), iterations, deleted);
                if (sameVerdict(r, base)) {
                    accepted.add(r);
                }
            }
        }
        int best = accepted.stream().mapToInt(r -> r.lasso().stateIds().size()).min().orElse(model.trace().size());
        return wrap(ReductionKind.STATE, accepted, r -> r.lasso().stateIds().size(), best);
    }

    private List<ReductionCandidate> reduceVariables(int entryIndex, boolean fairnessEnabled, ReplayResult base) {
        List<String> variables = allVariables();
        List<ReplayResult> accepted = new ArrayList<>();
        for (int keep = 0; keep <= variables.size(); keep++) {
            for (List<String> kept : combinations(variables, keep)) {
                Map<String, Object> deleted = new LinkedHashMap<>();
                List<String> removed = new ArrayList<>(variables);
                removed.removeAll(kept);
                deleted.put("removedVariables", removed);
                deleted.put("keptVariables", kept);
                deleted.put("rule", "从每个状态取值中移除变量后在原模型上重放；卫式按三值逻辑求值，只有卫式仍确定成立才算合法。");
                ReplayResult r = replayBaseline(entryIndex, fairnessEnabled,
                        new ArrayList<>(model.trace()), kept, deleted);
                if (sameVerdict(r, base)) {
                    accepted.add(r);
                }
            }
            if (!accepted.isEmpty()) {
                break;
            }
        }
        int best = accepted.stream().mapToInt(r -> r.lasso().keptVariables().size()).min().orElse(variables.size());
        return wrap(ReductionKind.VARIABLE, accepted, r -> r.lasso().keptVariables().size(), best);
    }

    private List<ReductionCandidate> reduceIterations(int entryIndex, boolean fairnessEnabled, ReplayResult base) {
        int period = loopPeriod(entryIndex);
        int repeats = recordedIterations(model.trace(), entryIndex);
        List<ReplayResult> accepted = new ArrayList<>();
        for (int target = 1; target < repeats; target++) {
            int loopEnd = entryIndex + period * target;
            List<String> reduced = new ArrayList<>(model.trace().subList(0, loopEnd));
            Map<String, Object> deleted = new LinkedHashMap<>();
            deleted.put("removedIterationBlocks", repeats - target);
            deleted.put("removedStates", new ArrayList<>(model.trace().subList(loopEnd,
                    entryIndex + period * repeats)));
            deleted.put("rule", "循环段记录了多轮完全相同的周期时折叠多余周期；保留至少一轮，回边不变。");
            ReplayResult r = replayBaseline(entryIndex, fairnessEnabled, reduced, allVariables(), deleted);
            if (sameVerdict(r, base)) {
                accepted.add(r);
            }
        }
        int best = accepted.stream().mapToInt(r -> r.lasso().loopIterations()).min().orElse(repeats);
        return wrap(ReductionKind.ITERATION, accepted, r -> r.lasso().loopIterations(), best);
    }

    private static boolean sameVerdict(ReplayResult a, ReplayResult b) {
        return a.verdict().equals(b.verdict())
                && a.violationReproduced() == b.violationReproduced()
                && a.replayed() == b.replayed();
    }

    private static <T> List<List<T>> combinations(List<T> items, int size) {
        List<List<T>> result = new ArrayList<>();
        combine(items, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static <T> void combine(List<T> items, int size, int start, List<T> current, List<List<T>> result) {
        if (current.size() == size) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            combine(items, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private List<ReductionCandidate> wrap(ReductionKind kind, List<ReplayResult> results,
                                          java.util.function.ToIntFunction<ReplayResult> scorer, int best) {
        List<ReductionCandidate> list = new ArrayList<>();
        for (ReplayResult r : results) {
            if (scorer.applyAsInt(r) == best) {
                list.add(new ReductionCandidate(kind, best, false, r));
            }
        }
        return list;
    }

    private void markShortest(List<ReductionCandidate> candidates) {
        if (candidates.isEmpty()) {
            return;
        }
        int shortest = candidates.stream().mapToInt(c -> c.replay().explanationLength()).min().orElseThrow();
        candidates.replaceAll(c -> c.replay().explanationLength() == shortest
                ? new ReductionCandidate(c.kind(), c.score(), true, c.replay())
                : c);
    }
}
