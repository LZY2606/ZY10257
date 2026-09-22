# 反例套索查验台（Counterexample Lasso Bench）

验证模型检查器对活性属性输出的“前缀 + 循环（lasso）”反例是否**真正重现违例**，并在不改变语义的前提下缩减解释。
技术栈：Java 17 / Spring Boot 3.5 / SQLite（sqlite-jdbc）/ 原生 SVG 页面，无前端构建步骤。

## 安装与运行

```bash
# 安装打包（跳过测试）
mvn -q -DskipTests package

# 验收：先跑自动化测试，再启动本地服务
mvn -q test && mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5597
```

访问 <http://127.0.0.1:5597>，页面标题为 **反例套索查验台**。

也可以直接运行打包产物：

```bash
java -jar target/counterexample-lasso-bench-1.0.0.jar --server.port=5597
```

SQLite 数据库默认在 `data/lasso-bench.db`（首次访问自动建表），可用环境变量覆盖：

```bash
LASSO_DB_PATH=/tmp/lasso.db mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=5597
```

## 页面功能

- 状态序列、前缀/循环着色的 **SVG 套索图**、循环入口高亮、回边（虚线）与转移见证。
- 逐步展示**转移卫式**求值结果（TRUE / FALSE / UNKNOWN / NO_EDGE）与接受集、公平集覆盖。
- 切换**公平性假设**、在两个**合法循环入口**间切换。
- 三类缩减：**最少状态（STATE）**、**最少变量（VARIABLE）**、**最少循环迭代（ITERATION）**。
  - 每种缩减在原模型上重放；只有判定（verdict、是否重现违例、是否重放成功）与基线一致才接受。
  - 同一最优分数下的**全部并列候选都会保留**。
  - 每个候选都带**被删元素清单**（删除的状态/变量/迭代块）与该候选自己的重放结果；点击候选卡片即查看其套索图与逐步见证。
  - “最短文本解释”是**独立目标**：候选卡上标记 `最短解释`，它与最少状态数/最少变量数不互相替代。
- 运行记录写入 SQLite，可导出 JSON；清空数据库后可用导出文件**重新导入并由引擎复核**（重新计算判定，逐条给出 verified/mismatched）。

## 数据口径（固定夹具 fixture-1）

夹具固定在 `src/main/resources/fixture.json`，不依赖外部模型文件：

- 模型：状态 L0–L6，变量 `pc, req, tok, a, b, noise`；`noise` 与任何卫式无关，最少变量缩减必被删除。
- 轨迹（15 个状态）：

  ```
  L6 → L0 → L1 → L3 → L4 → L5 → L2 → L4 → L5 → L3 → L4 → L5 → L3 → L4 → L5
  ```

- **入口 B：idx 6（L2）**，周期 3，回边 `e52`（L5→L2，卫式 `!req`）。
  - 循环为 `L2→L4→L5`；接受集 `ACC={L5}` 在循环上无限次出现。
  - 公平集 `FAIR={L3}`：L3 只在 idx 3 的**前缀**出现，循环里没有。
- **入口 A：idx 9（L3）**，周期 3，回边 `e53`（L5→L3，卫式 `!req`），轨迹记录了两轮 `L3→L4→L5`。
  - 接受集与公平集都在循环上无限次满足。
- 两个入口都是**合法入口**：最后状态回到入口必须由模型转移表中真实存在的转移（`e52`/`e53`）作证；
  状态取值相同**不能**替代转移见证。
- 入口 B 的回边 `L5→L2` 不出现在轨迹末尾相邻对里（轨迹末尾是入口 A 的周期），其见证完全来自模型转移表，
  用于显式检查“回边必须是模型中的合法转移”，而不是因为轨迹里出现过相邻状态。

判定矩阵（验收会切换入口与公平性）：

| 入口 | 公平性 | 接受集 | 公平集 | 判定 |
|---|---|---|---|---|
| A (idx 9) | 关 | 循环无限命中 | 不评估 | `VIOLATION_REPRODUCED` |
| A (idx 9) | 开 | 循环无限命中 | 循环无限满足 | `VIOLATION_REPRODUCED` |
| B (idx 6) | 关 | 循环无限命中 | 不评估 | `VIOLATION_REPRODUCED` |
| B (idx 6) | 开 | 循环无限命中 | **仅前缀出现** | `FAIRNESS_VIOLATED`（反例不成立） |

此外，缺失回边/卫式不可证时返回 `REPLAY_FAILURE`；接受集只在前缀出现时返回 `ACCEPTANCE_NOT_INFINITE`。

## 语义约定（重放方式）

1. **前缀转移**与**循环内转移**必须在模型转移表中找到同源、同目标且卫式成立的转移；回边同理。
2. **卫式三值逻辑**：变量被删后记为 UNKNOWN；`UNKNOWN || true = true`、`UNKNOWN && false = false`，
   其余组合为 UNKNOWN。只有确定为 TRUE 的转移可作见证——这保证删掉真正需要的变量会重放失败。
3. **接受集/公平集只在循环段上评估无限次**：前缀中的命中单独标记为 `PREFIX_ONLY`，不算无限次满足。
4. 循环段之后的额外轨迹记录（如入口 B 之后还录了入口 A 的两轮）在该入口下仅作备注，不参与回边见证与集合覆盖。
5. 缩减：
   - STATE 只允许删除**前缀内部**状态（保留模型初始状态 L6 与循环入口），删除后相邻状态之间必须存在合法模型转移；
     夹具中删 L0（经 e61）与删 L1（经 e03）是两个同分并列候选。
   - VARIABLE 枚举保留变量子集，取能保持判定的最小子集；析取卫式 `a || b`（e34）产生“保 a”/“保 b”两个并列候选。
   - ITERATION 折叠完全相同的多余循环周期，保留至少一轮。

## 运行记录的导出与复核

```bash
# 导出
curl -s http://127.0.0.1:5597/api/records/export -o records.json
# 清空（页面按钮或 DELETE）
curl -s -X DELETE http://127.0.0.1:5597/api/records
# 重新导入：后端按当前夹具重算每条记录的判定并统计一致/不一致
curl -s -X POST -H 'Content-Type: application/json' --data @records.json \
  http://127.0.0.1:5597/api/records/import
```

导入后记录的 `source` 为 `IMPORTED_VERIFIED` 或 `IMPORTED_MISMATCH`。

## 自动化测试

```bash
mvn -q test
```

- `LassoSemanticsTest`：四个判定象限、回边见证缺失即失败、前缀出现不算无限满足、三类缩减与并列候选、
  删除卫式变量导致 UNKNOWN 重放失败。
- `BenchControllerTest`：页面标题、API 判定、并列候选、SQLite 导出→清空→导入复核。

## 主要接口

- `GET /api/model`：固定夹具（变量、状态取值、转移与卫式、接受/公平集、轨迹、两个入口）。
- `GET /api/check?entry=9&fairness=true`：对指定入口与公平性假设重放。
- `GET /api/reduce?kind=STATE|VARIABLE|ITERATION&entry=9&fairness=false`：缩减候选。
- `GET|DELETE /api/records`、`GET /api/records/export`、`POST /api/records/import`：运行记录。
