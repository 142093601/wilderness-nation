"""M1 收尾（环境判定 + 幂等决策）的文档同步。每处替换都断言命中。"""

import sys
from pathlib import Path

ROOT = Path(r"D:\project\nation-pack")


def edit(rel: str, old: str, new: str, what: str) -> None:
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    if new in text and old not in text:
        print(f"  跳过（已改过）: {what}")
        return
    if old not in text:
        raise SystemExit(f"没命中: {what}\n  文件: {rel}\n  片段: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8", newline="")
    print(f"  ok: {what}")


# ---- README：用例数 + 两个新包 ----
edit("mods/statecraft/README.md",
     "**199 个 JUnit 用例全绿**（15 个测试类）",
     "**226 个 JUnit 用例全绿**（17 个测试类）",
     "README 用例数")
edit("mods/statecraft/README.md",
     "| `WorldGenerator` | ✅ 布点",
     "| `EnvironmentRules`（`env/`） | ✅ **环境判定**（§10.5）：只吃四个读数（有屋顶/封闭度/体积/在领地内），"
     "**类型上就没有形状信息** → \"不做形状检测\"是结构性保证；档位取\"满足的最高档\"，"
     "不满足时逐条列出原因（带实际读数）|\n"
     "| `Building` + `IdempotencyRules` + `MaterializationThrottle`（`building/`） | ✅ **幂等决策**（§10.3/§10.4）："
     "锚点先判 → 三件校验全过就 `SKIP`（幂等）→ 否则看同一 seq 与冷却；"
     "节流按 §十二（同结算最多 1 处 + 间隔 ≥5 分钟），**5 分钟换算成累计在线小时**，不用结算序号 |\n"
     "| `WorldGenerator` | ✅ 布点",
     "README 加 env/building 行")

# ---- NATIONS.md §五：Building 已实现 + 多一个字段 ----
edit("NATIONS.md",
     "| `staffDeadAt` | 村民死亡/感染的时间戳（用于冷却与\"重新雇用\"）|",
     "| `staffDeadAt` | 村民死亡/感染的时间戳（用于冷却与\"重新雇用\"）|\n"
     "\n"
     "**实现说明（2026-09-20）**：`buildings[]` 已进 `WorldState`；锚点拆成嵌套的 "
     "`Building.Anchor{dimension,x,y,z}`；\n"
     "另外**多一个字段** `materializedAtHours` —— 因为 §十二 的节流是\"两次间隔 ≥5 分钟\"，"
     "而 `materializedAt`（序号）一步就是 2 小时，**用序号表达 5 分钟会差 24 倍**；\n"
     "序号管\"同一 `seq` 不重复生成\"，小时管\"间隔 ≥5 分钟\"。也不用系统真实时间"
     "（那会让世界在没人玩时自己往前走，§七 禁止）。",
     "NATIONS §五 Building 实现说明")

# ---- NATIONS.md §10.3 / §10.5：标注已实现 ----
edit("NATIONS.md",
     "- **同一 `seq` 不重复生成**",
     "- **同一 `seq` 不重复生成**\n\n"
     "> ✅ **core 侧已实现**（2026-09-20）：`building/IdempotencyRules` 出 "
     "`SKIP / RESPAWN / WAIT_COOLDOWN / MARK_INVALID`，\n"
     "> 判定顺序是\"**锚点先判** → 三件校验全过就 SKIP → 再看同一 seq 与冷却\"；"
     "两种冷却分开（村民死了用重雇冷却，村民在但职业不对用实体化间隔）。",
     "NATIONS §10.3 已实现")
edit("NATIONS.md",
     "- **为什么不做硬性形状检测**：脆（挪一块砖就失效）、跨区块误判、性能、且与\"地标手建\"的自由冲突",
     "- **为什么不做硬性形状检测**：脆（挪一块砖就失效）、跨区块误判、性能、且与\"地标手建\"的自由冲突\n\n"
     "> ✅ **core 侧已实现**（2026-09-20）：`env/EnvironmentRules`。"
     "`EnvironmentSnapshot` **恰好只有四个读数**，没有任何方块/形状信息 ——\n"
     "> 所以\"不做形状检测\"是**类型上的保证**，而且有一条用例把 record 的分量名钉死"
     "（谁想加\"方块列表/长宽高\"会先红）。",
     "NATIONS §10.5 已实现")

# ---- NATIONS.md §十七 M1：只剩文本模板 ----
edit("NATIONS.md",
     "| **M1** | `core`：数据模型 + 生成 + 结算 + 吸收与疲劳 + 外交状态机 + 国家对玩家压力 + **环境判定与幂等决策** + 文本模板 |",
     "| **M1** | `core`：数据模型 + 生成 + 结算 + 吸收与疲劳 + 外交状态机 + 国家对玩家压力 + **环境判定与幂等决策** + 文本模板 |",
     "M1 行（占位，下面单独改结论）")

sys.exit(0)
