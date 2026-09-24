# 反例套索查验台（Lasso Counterexample Checker）

验证模型检查器针对**活性（liveness）性质**给出的“前缀 + 循环（lasso）”反例是否真正重现违例，并在**不改变违例语义**的前提下缩减解释。

技术栈：Java 21 字节码（可在 JDK 17+，含当前机器的 JDK 25/26 上运行）、Spring Boot 3.5、SQLite（`sqlite-jdbc`）、服务端生成 SVG。无前端构建步骤，页面为原生 HTML/JS。

## 安装与演示

```bash
# 安装（跳过测试打包）
mvn -q -DskipTests package

# 演示：先跑自动化测试，再以 5597 端口启动
mvn -q test && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5597
```

浏览器访问 <http://127.0.0.1:5597>，页面标题为 **反例套索查验台**。

SQLite 数据文件位于 `data/lasso.db`（首次启动自动建表并写入固定 fixture）。可用 `sqlite3 data/lasso.db` 直接查看。

## 数据口径（判定语义）

状态包含：`stateId`、变量赋值（JSON）、接受集（accept sets）、公平性集（fairness sets）。一条轨迹可声明多个循环入口。

查验一个入口时，按以下口径判定，任一不满足则“未复现违例”：

1. **原模型重放（转移见证）**：轨迹相邻状态之间，必须在模型转移表中存在一条 `源 → 目标` 且卫式（guard）在源状态变量下为真的转移。**状态变量赋值相同不能替代转移见证**——判定依据是 stateId 与模型转移表。
2. **环必须闭合**：环的最后一个状态必须存在一条返回所选入口的合法转移（卫式成立）。没有该边就不是套索。
3. **接受集覆盖**：模型中出现过的每个接受集，都必须在**循环状态**上至少出现一次。
4. **公平性只在循环上评估**：开启公平性假设后，每个公平集必须在循环状态中至少出现一次（代表“无限次满足”）。该集合只在前缀中出现**不算**无限次满足。

空卫式视为恒真；卫式引用未定义变量时按“未启用”处理（fail-closed）。卫式支持整数/布尔字面量、`+ - * / %`、`< <= > >= == !=`、`&& || !` 与括号。

## 固定 fixture

fixture 随代码交付（`src/main/java/com/lasso/persistence/Fixtures.java`），可一键恢复。

**M1 / CE1 —— 两个合法循环入口，公平性只在其中一个环无限满足**

- 轨迹：`s0 s1 s3 s4 s5 s6 s7 s8`；声明入口 `s4`、`s6`（均合法）与 `s4b`（非法）。
- 接受集 `ACC` 出现在 `s7`、`s8`；公平集 `FAIR` 只出现在 `s5`。
- 入口 `s4` 的环经过 `s5`，开启公平性后仍是违例；入口 `s6` 的环不含 `s5`（`s5` 只在其前缀出现），开启公平性后**不是**违例。
- `s4b` 的变量赋值与 `s4` 完全相同，但模型中没有进入/返回它的转移，用于验证“同值不能替代转移见证”。
- 存在两条等长的最短循环链（分别以接受状态 `s7` 或 `s8` 收尾并闭合回 `s4`），**状态缩减**因此产生两个并列最优候选。
- 变量 `w`、`flag` 不被任何套索见证卫式引用，**变量缩减**保留 `{x,y,z}` 并删除 `w/flag`。

**M2 / CE2 —— 循环迭代缩减**

- 轨迹 `a0 a1 a2 a1 a2`，入口 `a1`，周期 `a1 a2` 被展开两次；迭代缩减折叠为 `a0 a1 a2`，`a2 → a1` 闭合边保留。

## 三种缩减

每次缩减都会在服务端用**缩减后的序列对原模型重新查验**，并返回被删元素与重放结论；保留不了违例的候选不会入选。

- **状态缩减（最少状态数）**：在轨迹下标子序列中枚举合法跨边链，前缀必须从原起点出发、循环必须能闭合回入口；接受覆盖/公平结论必须保持。取最少状态数，**同分数候选全部保留（并列）**。
- **变量缩减（最少变量）**：按当前套索（含闭合边）见证所引用的变量集合，枚举最小充分变量子集；删除其余变量后所有见证卫式取值不变。同分数子集全部保留。
- **循环迭代缩减（最少迭代数）**：检测入口之后的重复状态周期，把多份周期折叠为一份规范周期，并验证折叠后仍可重放、闭合、覆盖接受集。

“最少状态数”“最少变量”“最短文本解释”是三个不同目标：解释文本只用于说明，不参与候选淘汰；指标相同即并列。

## 页面操作

- 选择反例与循环入口，开关“启用公平性假设”，点击“查验反例”。
- 查看 SVG（状态、卫式、入口标记、接受 ◇/公平 ♢ 标签、返回入口的回边）、状态变量表与逐步转移见证。
- 对当前入口分别执行状态 / 变量 / 循环迭代缩减；并列候选逐个展示，可在图上高亮“已删除状态/仅保留变量”。
- 运行记录（查验、缩减、导入导出）写入 `run_logs`，可在页面查看。
- “导出全部（JSON/复制）”生成快照；清空数据库后，将快照粘贴到导入框并“清空数据库并重新导入”，可再次复核同一判定。也可“恢复固定 fixture”。

## HTTP 接口

| 方法 路径 | 说明 |
| --- | --- |
| `GET /` | 操作页面 |
| `GET /api/overview` | 模型、状态、转移、反例与入口 |
| `POST /api/verify` | `{ceId,entry,fairness}` 查验并返回重放、覆盖、公平、判定 |
| `POST /api/reduce` | `{ceId,entry,fairness,kind}`，`kind ∈ state|variables|iterations` |
| `POST /api/diagram` | 返回套索 SVG 与判定文案（可带删除状态/保留变量高亮） |
| `GET /api/runs?limit=` | 运行记录 |
| `GET|POST /api/export` | 导出快照（GET 返回可下载 JSON 文件） |
| `POST /api/import` | 清空后导入导出快照 |
| `POST /api/reset` | 清空并恢复固定 fixture |

### 命令行重放示例

```bash
curl -s -X POST http://127.0.0.1:5597/api/verify \
  -H 'Content-Type: application/json' \
  -d '{"ceId":"CE1","entry":"s6","fairness":true}'
# violationReproduced=false，fairUnsatisfied=["FAIR"]，说明前缀出现不计无限满足

curl -s -X POST http://127.0.0.1:5597/api/reduce \
  -H 'Content-Type: application/json' \
  -d '{"ceId":"CE1","entry":"s4","fairness":false,"kind":"state"}'
# bestMetric=6，tie=true，两个候选分别 ...s6,s7 与 ...s6,s8
```

## 清空数据库后重新导入复核

```bash
curl -s -X POST http://127.0.0.1:5597/api/export -o lasso-export.json
sqlite3 data/lasso.db "DELETE FROM run_logs; DELETE FROM ce_entries; DELETE FROM ce_traces;
 DELETE FROM counterexamples; DELETE FROM transitions; DELETE FROM states; DELETE FROM models;"
curl -s -X POST http://127.0.0.1:5597/api/import \
  -H 'Content-Type: application/json' --data-binary @lasso-export.json
# 再调用 /api/verify、/api/reduce，判定与缩减指标与导入前一致
```

导出 JSON 顶层格式为 `{"format":"lasso-checker-export/v1", ...}`，包含全部模型/状态/转移/反例/轨迹/入口与最近运行记录。

## 自动化测试

`mvn test` 共 5 个测试类、17 个用例，覆盖：

- 卫式求值（比较/布尔/算术、未定义变量 fail-closed、引用变量收集）；
- 两个入口在公平性开关下的判定、`s4b` 同值无转移见证、环末闭合；
- 三种缩减的最优性、并列候选保留、对原模型重放仍复现违例；
- 导出 → 清空 → 导入后判定与缩减指标一致；
- Web 层页面标题、查验/缩减/SVG/运行记录全链路。

测试使用独立数据库 `target/test-lasso.db`，不污染 `data/lasso.db`。

## 目录结构

```
src/main/java/com/lasso
  LassoCheckerApplication.java     # Spring Boot 入口
  model/                           # 状态/转移/反例/查验与缩减结果记录
  engine/                          # 卫式求值、重放、套索查验、三种缩减
  persistence/                     # JDBC 仓库、schema 初始化、固定 fixture
  web/                             # REST、页面、SVG 服务
src/main/resources
  application.properties schema.sql static/index.html
src/test/...                       # 单元/集成/Web 测试
data/lasso.db                      # 运行期 SQLite（自动生成）
```
