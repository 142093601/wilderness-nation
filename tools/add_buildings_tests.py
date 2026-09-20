"""给 buildings 存档链路补验收用例（Task 1 的验收：v1 老档迁完 field 齐全、往返一致）。"""

import sys
from pathlib import Path

T = Path(r"D:\project\nation-pack\mods\statecraft\core\src\test\java\com\wildernessnation"
         r"\statecraft\core")


def insert_before_last_brace(path: Path, block: str, what: str) -> None:
    text = path.read_text(encoding="utf-8")
    if block.strip() in text:
        print(f"  跳过（已存在）: {what}")
        return
    idx = text.rstrip().rfind("}")
    if idx < 0:
        raise SystemExit(f"找不到类结尾: {path.name}")
    text = text[:idx] + block + text[idx:]
    path.write_text(text, encoding="utf-8", newline="")
    print(f"  ok: {what}")


def ensure_import(path: Path, imp: str) -> None:
    text = path.read_text(encoding="utf-8")
    if imp in text:
        return
    anchor = "import com.wildernessnation.statecraft.core.model.Nation;"
    if anchor in text:
        text = text.replace(anchor, imp + "\n" + anchor, 1)
    else:
        anchor2 = "import java.util.List;"
        text = text.replace(anchor2, imp + "\n" + anchor2, 1)
    path.write_text(text, encoding="utf-8", newline="")
    print(f"  加了 import: {imp} -> {path.name}")


CODEC_TEST = T / "persist" / "StateCodecTest.java"
insert_before_last_brace(CODEC_TEST, '''
    /** §五 的 `buildings[]` 也要原样过去 —— 包括"还没绑定村民"的那座。 */
    @Test
    void buildingsSurviveTheRoundTrip() {
        Building bound = new Building("b1", "intel_station",
                new Building.Anchor("minecraft:overworld", 100, 64, -200), 2,
                "uuid-1", 7L, 11.5, 0L);
        Building fresh = Building.register("b2", "warehouse",
                new Building.Anchor("minecraft:overworld", -5, 70, 9));
        WorldState state = new WorldState(
                WorldState.CURRENT_SCHEMA_VERSION, 3L, "infra", 2, 7L,
                List.of(), List.of(), List.of(), List.of(bound, fresh), 11.5);

        WorldState back = StateCodec.read(StateCodec.write(state));
        assertEquals(state, back, "带建筑的往返必须逐字段相等");
        assertEquals(2, back.buildings().size());
        assertEquals("uuid-1", back.buildings().get(0).staffUUID());
        assertTrue(back.buildings().get(1).stalled(), "没绑定村民的也要能原样读回来");
        assertEquals(11.5, back.buildings().get(0).materializedAtHours(), 0.0);
        assertEquals(-200, back.buildings().get(0).anchor().z());
    }
}
''', "StateCodecTest：buildings 往返")
ensure_import(CODEC_TEST, "import com.wildernessnation.statecraft.core.model.Building;")

MIGRATOR_TEST = T / "persist" / "StateMigratorTest.java"
if "v1 没有建筑登记" not in MIGRATOR_TEST.read_text(encoding="utf-8"):
    text = MIGRATOR_TEST.read_text(encoding="utf-8")
    anchor = '        assertEquals(0, state.events().size());'
    if anchor not in text:
        raise SystemExit("StateMigratorTest: 找不到插入锚点")
    text = text.replace(anchor,
                        anchor + '\n        assertEquals(0, state.buildings().size(), "v1 没有建筑登记");', 1)
    MIGRATOR_TEST.write_text(text, encoding="utf-8", newline="")
    print("  ok: StateMigratorTest：断言 v1 迁移后建筑表为空")

WORLD_STATE_TEST = T / "model" / "WorldStateTest.java"
insert_before_last_brace(WORLD_STATE_TEST, '''
    /** 建筑 id 不能重复（同一个 id 两条登记会让"换掉哪一座"变得没有意义）。 */
    @Test
    void rejectsDuplicateBuildingIds() {
        Building b = Building.register("b1", "intel_station",
                new Building.Anchor("minecraft:overworld", 0, 64, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldState(V, 1L, "landing", 0, 0L,
                List.of(nation("n0")), List.of(), List.of(), List.of(b, b), 0.0));
        // 正常情况下建筑表是能挂上的，并且不可变
        WorldState ok = new WorldState(V, 1L, "landing", 0, 0L,
                List.of(nation("n0")), List.of(), List.of(), List.of(b), 0.0);
        assertEquals(1, ok.buildings().size());
        assertThrows(UnsupportedOperationException.class, () -> ok.buildings().clear());
    }
}
''', "WorldStateTest：建筑 id 唯一 + 不可变")
ensure_import(WORLD_STATE_TEST, "import com.wildernessnation.statecraft.core.model.Building;")

sys.exit(0)
