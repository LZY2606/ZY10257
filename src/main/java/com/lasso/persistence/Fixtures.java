package com.lasso.persistence;

import com.lasso.model.Counterexample;
import com.lasso.model.Model;
import com.lasso.model.ModelState;
import com.lasso.model.Transition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置固定夹具（随代码交付），用于演示与自动测试；可一键恢复。
 *
 * M1 / CE1：
 *  - 同一条轨迹提供两个“合法”循环入口 s4 与 s6；另有同值入口 s4b 但无返回转移（非法）。
 *  - 公平集 FAIR 只在 s5 出现：s4 的环包含 s5，s6 的环不含（s5 只在其前缀出现）。
 *  - 前缀存在两条等长跨边链，状态缩减在 s4 上产生并列候选。
 *  - 变量 w、flag 不被任何套索见证卫式引用，变量缩减将其删除。
 *
 * M2 / CE2：
 *  - 循环展开为多个周期，迭代缩减折叠到一份规范周期。
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static Model modelOne() {
        List<ModelState> states = List.of(
                state("M1", "s0", 0, vars(0, 0, 0, 0, false), List.of(), List.of()),
                state("M1", "s1", 1, vars(1, 0, 0, 0, false), List.of(), List.of()),
                state("M1", "s2", 2, vars(0, 1, 0, 0, false), List.of(), List.of()),
                state("M1", "s3", 3, vars(1, 1, 0, 0, false), List.of(), List.of()),
                state("M1", "s4", 4, vars(1, 1, 0, 0, false), List.of(), List.of()),
                state("M1", "s4b", 5, vars(1, 1, 0, 0, false), List.of(), List.of()),
                state("M1", "s5", 6, vars(1, 1, 1, 0, false), List.of(), List.of("FAIR")),
                state("M1", "s6", 7, vars(1, 1, 1, 0, false), List.of(), List.of()),
                state("M1", "s7", 8, vars(1, 1, 1, 1, false), List.of("ACC"), List.of()),
                state("M1", "s8", 9, vars(1, 1, 1, 1, false), List.of("ACC"), List.of()));

        List<Transition> transitions = List.of(
                t("M1", 1, "s0", "s1", "x == 0", "初始进入分支 A"),
                t("M1", 2, "s0", "s2", "y == 0", "初始进入分支 B"),
                t("M1", 3, "s1", "s3", "x >= 1 && y == 0", "分支 A 汇合"),
                t("M1", 4, "s2", "s3", "y >= 1", "分支 B 汇合"),
                t("M1", 5, "s3", "s4", "x == 1", "进入循环准备"),
                t("M1", 6, "s0", "s2", "y == 0 && flag", "永远关闭的跨边（引用死变量 flag）"),
                t("M1", 7, "s4", "s5", "", "进入公平状态"),
                t("M1", 8, "s4", "s6", "z == 0 && w == 0", "跨边：跳过公平状态 s5（缩减候选之一）"),
                t("M1", 9, "s5", "s6", "z >= 1", "经公平状态推进（原始轨迹见证）"),
                t("M1", 10, "s6", "s7", "", "到达接受状态 s7"),
                t("M1", 11, "s6", "s8", "z >= 1", "到达另一个接受状态 s8（并列缩减候选）"),
                t("M1", 12, "s7", "s4", "y == 1", "返回入口 s4 的闭合边"),
                t("M1", 13, "s8", "s4", "y == 1", "另一条返回入口 s4 的闭合边（与 s7 链等长）"),
                t("M1", 14, "s7", "s6", "y == 1 && x == 1", "返回入口 s6 的闭合边"),
                t("M1", 15, "s7", "s8", "z >= 1", "轨迹中从 s7 继续到 s8"),
                t("M1", 16, "s8", "s6", "z >= 1 && x == 1", "从 s8 直接闭合回入口 s6（环只含 s6/s7/s8，不含公平状态 s5）"));

        return new Model("M1", "双入口活性模型",
                "一条轨迹含两个合法循环入口；FAIR 仅在其中一个环上无限满足。",
                states, transitions);
    }

    public static Counterexample ceOne() {
        List<String> trace = List.of("s0", "s1", "s3", "s4", "s5", "s6", "s7", "s8");
        List<Counterexample.EntryCandidate> entries = List.of(
                new Counterexample.EntryCandidate("s4", true, "环 s4-s5-s6-s7，含 ACC 与 FAIR"),
                new Counterexample.EntryCandidate("s6", true, "环 s6-s7，含 ACC，但 FAIR 只在其前缀 s5 出现"),
                new Counterexample.EntryCandidate("s4b", false, "变量赋值与 s4 相同，但模型中没有任何进入/返回它的转移"));
        return new Counterexample("CE1", "M1", "进度性质 <>crit 的反例",
                "liveness",
                "前缀 s0-s1-s3；两个合法入口对 ACC 都覆盖，公平性只在 s4 的环无限满足。",
                trace, entries);
    }

    public static Model modelTwo() {
        List<ModelState> states = List.of(
                state("M2", "a0", 0, vars(0, 0, 0, 0, false), List.of(), List.of()),
                state("M2", "a1", 1, vars(1, 0, 0, 0, false), List.of("ACC"), List.of()),
                state("M2", "a2", 2, vars(1, 1, 0, 0, false), List.of(), List.of()));
        List<Transition> transitions = List.of(
                t("M2", 1, "a0", "a1", "x == 0", "进入循环"),
                t("M2", 2, "a1", "a2", "x >= 1", "周期第二步"),
                t("M2", 3, "a2", "a1", "y >= 1", "返回周期入口"));
        return new Model("M2", "循环迭代模型", "展开多个周期用于迭代缩减演示。",
                states, transitions);
    }

    public static Counterexample ceTwo() {
        List<String> trace = List.of("a0", "a1", "a2", "a1", "a2");
        List<Counterexample.EntryCandidate> entries = List.of(
                new Counterexample.EntryCandidate("a1", true, "周期 a1-a2 重复出现"));
        return new Counterexample("CE2", "M2", "响应性质的展开反例", "liveness",
                "同周期被展开两次；迭代缩减折叠为 a0-a1-a2。", trace, entries);
    }

    private static ModelState state(String modelId, String id, int ord,
                                    Map<String, Object> variables,
                                    List<String> accept, List<String> fair) {
        return new ModelState(modelId, id, ord, variables, accept, fair);
    }

    private static Transition t(String modelId, int ord, String source, String target,
                                String guard, String desc) {
        return new Transition(0, modelId, ord, source, target, guard, desc);
    }

    private static Map<String, Object> vars(int x, int y, int z, int w, boolean flag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("w", w);
        map.put("flag", flag);
        return map;
    }
}
