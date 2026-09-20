"""收尾 `buildings` 接线：更新被 pin 的哈希值 + 两处文档说明。

为什么哈希会变：`StateHash` 算的是**序列化后的整棵树**，所以**加一个字段也会换哈希**。
这不是 bug，是这套设计的自带约束 —— 也正因为如此，换 schema 时事务日志必须一起清掉，
否则重放第一步就报"输入哈希对不上"。把这条写进代码注释，别让下次的人重新踩。
"""

import sys
from pathlib import Path

CORE = Path(r"D:\project\nation-pack\mods\statecraft\core\src")
OLD_HASH = "8352801430311920762L"
NEW_HASH = "2479475232479818627L"


def edit(path: Path, old: str, new: str, what: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"没命中: {what} ({path.name})")
    path.write_text(text.replace(old, new), encoding="utf-8", newline="")
    print(f"  ok: {what}")


def main() -> int:
    edit(CORE / "test" / "java" / "com" / "wildernessnation" / "statecraft" / "core" / "settle"
         / "SettlementLogTest.java",
         OLD_HASH, NEW_HASH,
         "更新被 pin 的状态哈希（加了 buildings 字段）")

    edit(CORE / "test" / "java" / "com" / "wildernessnation" / "statecraft" / "core" / "settle"
         / "SettlementLogTest.java",
         '                "哈希算法变了 —— 存档里的 inputHash 会全部失效，必须有意为之并写进迁移说明");',
         '                "状态哈希变了：要么算法改了，要么 schema 加了字段 —— 两者都会让存档里的 "\n'
         '                        + "inputHash 全部失效，必须有意为之（换 schema 时事务日志要一起清掉）");',
         "把哈希 pin 的说明写清楚：加字段也会换哈希")

    edit(CORE / "test" / "java" / "com" / "wildernessnation" / "statecraft" / "core" / "persist"
         / "StateCodecTest.java",
         '        assertEquals(\n'
         '                List.of("schemaVersion", "seed", "eraId", "eraOrdinal", "seq", "nations",\n'
         '                        "relations", "events", "elapsedOnlineHours"),',
         '        assertEquals(\n'
         '                List.of("schemaVersion", "seed", "eraId", "eraOrdinal", "seq", "nations",\n'
         '                        "relations", "events", "buildings", "elapsedOnlineHours"),',
         "字段名清单加 buildings")

    edit(CORE / "main" / "java" / "com" / "wildernessnation" / "statecraft" / "core" / "persist"
         / "StateHash.java",
         ' * 这样"哈希一致"和"存档一致"是同一件事，不会出现"哈希说一样、存出来不一样"。',
         ' * 这样"哈希一致"和"存档一致"是同一件事，不会出现"哈希说一样、存出来不一样"。\n'
         ' *\n'
         ' * <p><strong>代价（必须记住）</strong>：哈希覆盖整棵树，所以**加一个字段也会换哈希**。\n'
         ' * 于是：**换 schema 版本时，事务日志（SettlementLog）必须跟着清掉**，\n'
         ' * 否则重放第一步就会报"输入哈希对不上"。这不是 bug，是这套设计自带的约束 ——\n'
         ' * 有一条用例把某个具体哈希值钉住，任何改动都会被立刻看到。',
         "StateHash 注明：加字段也会换哈希")
    return 0


if __name__ == "__main__":
    sys.exit(main())
