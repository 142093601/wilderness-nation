# M1 收尾：环境判定 + 幂等决策（core 侧，离线可测）

> `NATIONS.md` §十七 的 M1 现在只剩这三块：**环境判定 / 幂等决策 / 文本模板**。
> 其中前两块是**纯规则**——设计 §四 与 §10.5 明确写了"规则在 `core`，世界读数由 `integration`
> 提供环境快照"，所以它们能像结算引擎一样离线写完并用 JUnit 钉死。
> 文本模板要等地名池与情报册（计划 4），这里不做。
>
> 为什么插在计划 4 之前：这两块正是计划 4 的**核心机制**
> （"建筑能不能用"决定情报站能不能开外交；"幂等"不做对会生出三个文书），
> 先把规则写死、测穿，计划 4 的 mod 层就只剩"喂快照 + 执行动作"。

---

## 一、环境判定（§10.5 + §十二）

**设计的原话**：条件是"有屋顶 / 封闭度 / 最小体积 / 在领地内"，用来决定"**能不能用 / 到哪一档**"；
并且**刻意不做形状检测**（挪一块砖就失效、跨区块误判、性能、与"地标手建"的自由冲突）。

| 项 | 值 | 出处 |
|---|---|---|
| 有屋顶 | 必须 | §十二 |
| 封闭度 | ≥ 60% | §十二 |
| 内部体积 | ≥ 12 格 | §十二 |
| 在领地内 | 必须 | §十二 |

**接口**：core 收一个 `EnvironmentSnapshot`（世界读数，由 integration 从 mc 层量出来）：

```
{ hasRoof, enclosurePercent, volume, insideClaim }
```

**判定结果**要能回答"到哪一档"，但又不能凭空发明档位 —— 所以做成
**数据驱动的要求表**（`EnvironRequirements`）：每档一条 `Requirement`，core 选出
**满足的最高档**；一档都不满足时把**每一条不满足的原因**逐个列出来
（玩家会看到"还差：封闭度 42% < 60%"这种话，而不是一句"不能用"）。

- 档位与阈值来自数据（`buildings.json`，计划 4），core 不写死"三档"。
- 快照里**没有**任何形状/方块信息 —— 那是刻意的：规则层不该有机会去做形状检测。

## 二、幂等决策（§10.3 + §10.4 + §十二）

**设计的原话**：`WorldState.buildings[]` 记 `{id, type, anchor, staffUUID, materializedAt}`；
区块重载 / 服务器重启后校验"`staffUUID` 存在、是本职村民、且 `buildingId` 匹配"，
匹配则**不生成**；不匹配则**按冷却重生成**；**同一 `seq` 不重复生成**。

同样只吃"观察结果"，输出**决定**，不做任何世界改动：

```
输入：建筑登记 + 观察快照 { staffExists, staffIsRightProfession, staffBuildingId, anchorIntact }
输出：SKIP / RESPAWN / WAIT_COOLDOWN / MARK_INVALID / MARK_STALLED
```

几条要钉死的规则：

| 情形（§10.4） | 决定 |
|---|---|
| 村民在、职业对、buildingId 匹配 | `SKIP`（**幂等**：绝不重新生成） |
| 村民不在（死亡/被感染） | `RESPAWN`（受冷却约束）+ `staff_lost` 事件 |
| 锚点没了 | `MARK_INVALID`（村民保留，可按登记重新安放） |
| 冷却未到 | `WAIT_COOLDOWN` |
| 同一 `seq` 已经实体化过 | `SKIP`（"同一 seq 不重复生成"） |
| 同一次结算最多实体化 1 处，其余排队（§十二） | 由调用方的 `MaterializationThrottle` 决定谁先谁后 |

**"两次间隔 ≥5 分钟"怎么落到 core 的时钟上**：core 只有"累计在线小时"（§七 的时间口径），
所以把 5 分钟换算成 `5/60` 小时放进配置，并用**累计在线小时**比较；
`materializedAt` 仍按 §五 存结算序号（那是给人看的"哪一次结算做的"）。

## 三、模型：`Building`（§五 的字段表已经给全了）

`{ id, type, anchor, level, staffUUID, materializedAt, staffDeadAt }` +
`anchor` 需要维度与坐标。这些字段 §五 都写了，所以**不算发明**；
`buildings[]` 进 `WorldState`。

**schema 说明**：v2 至今**没有写出过任何存档**（实例里装的还是 v1 的 jar），
所以这些字段直接并进 v2，不必再加一级迁移。
**这次之后就不能再这么干了** —— 真机第一次加载会把 v1 存档迁成 v2，
从那时起加字段就得老老实实 `CURRENT_SCHEMA_VERSION + 1` + 补一级迁移。

## 四、任务

| # | 任务 | 验收 |
|---|---|---|
| 1 | `Building` + `WorldState.buildings`（并入 v2 + 迁移补默认值） | v1 老档迁完 field 齐全、往返一致 |
| 2 | `env/EnvironmentSnapshot` + `EnvironRequirements` + `EnvironmentRules` | 阈值边界（59.9% vs 60%、11 vs 12 格）；多条不满足时**全部**列出来；档位取"满足的最高档"；**快照里没有形状信息**（结构性保证不做形状检测） |
| 3 | `building/StaffObservation` + `MaterializationDecision` + `IdempotencyRules` | §10.3/§10.4 六种情形逐条；**同一 seq 不重复生成**；冷却生效 |
| 4 | `MaterializationThrottle`（每次结算最多 1 处 + 5 分钟间隔，其余排队） | 排队顺序确定；同 seed 可复现 |
| 5 | 文档同步 | `NATIONS.md` §10.3/§10.5/§十二/§十七、README、本计划执行记录 |

## 五、明确不做

- **不做形状/方块检测**（§10.5 的硬要求，且快照类型里根本没有方块信息）
- **不做事件文本渲染 / 周报 / 地名池**（计划 4）
- **不做实际的刷村民**（那是 mc 层：实体生成 + NBT + 职业锁定，计划 4）
- **不做 `buildings.json` 的档位数据**（计划 4 与内容层）
