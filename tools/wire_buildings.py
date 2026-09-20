"""把 `buildings` 接进 WorldState 的存档链路：生产代码 4 处 + 测试构造点补参数。

每处替换都断言"确实命中了"，没命中就报错退出 —— 静默不替换比改错更糟。
"""

import sys
from pathlib import Path

ROOT = Path(r"D:\project\nation-pack\mods\statecraft\core\src")
MAIN = ROOT / "main" / "java" / "com" / "wildernessnation" / "statecraft" / "core"


def edit(path: Path, old: str, new: str, what: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text and old not in text:
        print(f"  跳过（已经改过）: {what}")
        return
    if old not in text:
        raise SystemExit(f"没命中: {what}\n  文件: {path.name}\n  期望片段: {old[:120]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"命中 {text.count(old)} 次（要求唯一）: {what}")
    path.write_text(text.replace(old, new), encoding="utf-8", newline="")
    print(f"  ok: {what}")


def patch_worldstate_calls() -> None:
    """给所有 `new WorldState(...)` 在**最后一个参数之前**插入 buildings 的实参。

    注意不是插在末尾：`buildings` 是倒数第二个分量，最后一个是 elapsedOnlineHours。
    第一次我插在末尾，编译器立刻报"要 List<Building> 却给了 double"——这条注释就是留给下次的。
    """
    target = "new WorldState("
    insert = " List.of(),"
    total = 0
    for path in sorted((ROOT / "test").rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        if target not in text:
            continue
        out, i, n = [], 0, 0
        while True:
            j = text.find(target, i)
            if j < 0:
                out.append(text[i:])
                break
            out.append(text[i:j + len(target)])
            depth, k = 1, j + len(target)
            while k < len(text) and depth > 0:
                ch = text[k]
                if ch == "(":
                    depth += 1
                elif ch == ")":
                    depth -= 1
                    if depth == 0:
                        break
                k += 1
            if k >= len(text):
                raise SystemExit(f"{path.name}: 括号没配对")
            body = text[j + len(target):k]
            # 找**顶层**的最后一个逗号
            depth, last = 0, -1
            for idx, ch in enumerate(body):
                if ch in "([{":
                    depth += 1
                elif ch in ")]}":
                    depth -= 1
                elif ch == "," and depth == 0:
                    last = idx
            if last < 0:
                raise SystemExit(f"{path.name}: 找不到参数分隔逗号: {body[:80]!r}")
            if body[last + 1:last + 12].strip().startswith("List.of()"):
                out.append(body)          # 已经补过
            else:
                out.append(body[:last + 1] + insert + body[last + 1:])
                n += 1
            out.append(")")
            i = k + 1
        if n:
            path.write_text("".join(out), encoding="utf-8", newline="")
            print(f"  ok: {path.name} 补了 {n} 处")
            total += n
    print(f"  测试共补 {total} 处")


def main() -> int:
    print("生产代码：")
    edit(MAIN / "gen" / "WorldGenerator.java",
         "startingEraOrdinal, 0L, nations, initialRelations(nations), List.of(), 0.0);",
         "startingEraOrdinal, 0L, nations, initialRelations(nations), List.of(), List.of(),\n                0.0);",
         "WorldGenerator：新世界没有建筑")

    edit(MAIN / "settle" / "SettlementEngine.java",
         "state.seq(), nations, relations, state.events(), state.elapsedOnlineHours());",
         "state.seq(), nations, relations, state.events(), state.buildings(),\n                state.elapsedOnlineHours());",
         "SettlementEngine：结算要原样带着建筑")

    edit(MAIN / "persist" / "StateMigrations.java",
         '        fields.put("events", new StateNode.Arr(List.of()));',
         '        fields.put("events", new StateNode.Arr(List.of()));\n'
         '        fields.put("buildings", new StateNode.Arr(List.of()));',
         "迁移：v1 没有建筑登记 → 空表")

    codec = MAIN / "persist" / "StateCodec.java"
    edit(codec,
         'import com.wildernessnation.statecraft.core.model.Event;',
         'import com.wildernessnation.statecraft.core.model.Building;\n'
         'import com.wildernessnation.statecraft.core.model.Event;',
         "StateCodec：import Building")
    edit(codec,
         '        root.put("elapsedOnlineHours", new StateNode.Dec(state.elapsedOnlineHours()));',
         '        List<StateNode> buildings = new ArrayList<>();\n'
         '        for (Building b : state.buildings()) {\n'
         '            buildings.add(writeBuilding(b));\n'
         '        }\n'
         '        root.put("buildings", new StateNode.Arr(buildings));\n\n'
         '        root.put("elapsedOnlineHours", new StateNode.Dec(state.elapsedOnlineHours()));',
         "StateCodec.write：写出建筑")
    edit(codec,
         '    private static StateNode writeEvent(Event e) {',
         '    private static StateNode writeBuilding(Building b) {\n'
         '        Map<String, StateNode> f = StateNode.fields();\n'
         '        f.put("id", new StateNode.Str(b.id()));\n'
         '        f.put("type", new StateNode.Str(b.type()));\n'
         '        f.put("dimension", new StateNode.Str(b.anchor().dimension()));\n'
         '        f.put("x", new StateNode.Int(b.anchor().x()));\n'
         '        f.put("y", new StateNode.Int(b.anchor().y()));\n'
         '        f.put("z", new StateNode.Int(b.anchor().z()));\n'
         '        f.put("level", new StateNode.Int(b.level()));\n'
         '        // staffUUID 可空 → 用空串表示"还没绑定"（NBT/JSON 里 null 是最容易出错的东西）\n'
         '        f.put("staffUUID", new StateNode.Str(b.staffUUID() == null ? "" : b.staffUUID()));\n'
         '        f.put("materializedAtSeq", new StateNode.Int(b.materializedAtSeq()));\n'
         '        f.put("materializedAtHours", new StateNode.Dec(b.materializedAtHours()));\n'
         '        f.put("staffDeadAtSeq", new StateNode.Int(b.staffDeadAtSeq()));\n'
         '        return new StateNode.Obj(f);\n'
         '    }\n\n'
         '    private static StateNode writeEvent(Event e) {',
         "StateCodec：writeBuilding")
    edit(codec,
         '        double hours = root.field(p, "elapsedOnlineHours").asDouble(p + ".elapsedOnlineHours");',
         '        List<Building> buildings = new ArrayList<>();\n'
         '        List<StateNode> buildingNodes = root.field(p, "buildings").asArr(p + ".buildings").items();\n'
         '        for (int i = 0; i < buildingNodes.size(); i++) {\n'
         '            buildings.add(readBuilding(buildingNodes.get(i), StateNode.index(p + ".buildings", i)));\n'
         '        }\n\n'
         '        double hours = root.field(p, "elapsedOnlineHours").asDouble(p + ".elapsedOnlineHours");',
         "StateCodec.read：读建筑")
    edit(codec,
         '        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations, relations,\n'
         '                events, hours);',
         '        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations, relations,\n'
         '                events, buildings, hours);',
         "StateCodec.read：传给 WorldState")
    edit(codec,
         '    private static Event readEvent(StateNode node, String p) {',
         '    private static Building readBuilding(StateNode node, String p) {\n'
         '        String uuid = node.field(p, "staffUUID").asString(p + ".staffUUID");\n'
         '        return new Building(\n'
         '                node.field(p, "id").asString(p + ".id"),\n'
         '                node.field(p, "type").asString(p + ".type"),\n'
         '                new Building.Anchor(\n'
         '                        node.field(p, "dimension").asString(p + ".dimension"),\n'
         '                        node.field(p, "x").asInt(p + ".x"),\n'
         '                        node.field(p, "y").asInt(p + ".y"),\n'
         '                        node.field(p, "z").asInt(p + ".z")),\n'
         '                node.field(p, "level").asInt(p + ".level"),\n'
         '                uuid.isBlank() ? null : uuid,          // 空串 = 还没绑定\n'
         '                node.field(p, "materializedAtSeq").asLong(p + ".materializedAtSeq"),\n'
         '                node.field(p, "materializedAtHours").asDouble(p + ".materializedAtHours"),\n'
         '                node.field(p, "staffDeadAtSeq").asLong(p + ".staffDeadAtSeq"));\n'
         '    }\n\n'
         '    private static Event readEvent(StateNode node, String p) {',
         "StateCodec：readBuilding")

    print("测试构造点：")
    patch_worldstate_calls()
    return 0


if __name__ == "__main__":
    sys.exit(main())
