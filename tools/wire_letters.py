"""把 `letters`（§五 的 letters[]，国书）接进 WorldState + 编解码 + 迁移，并补所有构造点。

字段顺序照 §五：`… events[], letters[], buildings[], …` —— letters 在 buildings **之前**。
所以给测试构造点补参数时要插在**倒数第二个**实参之前（不是末尾）。
（上一轮"插在末尾"那版踩过坑：buildings 是倒数第二、elapsedOnlineHours 才是最后。）
"""

import sys
from pathlib import Path

SRC = Path(r"D:\project\nation-pack\mods\statecraft\core\src")
MAIN = SRC / "main" / "java" / "com" / "wildernessnation" / "statecraft" / "core"


def edit(path: Path, old: str, new: str, what: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        print(f"  跳过（已改过）: {what}")
        return
    if old not in text:
        raise SystemExit(f"没命中: {what}\n  文件: {path.name}\n  片段: {old[:120]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"命中 {text.count(old)} 次（要求唯一）: {what}")
    path.write_text(text.replace(old, new), encoding="utf-8", newline="")
    print(f"  ok: {what}")


WS = MAIN / "model" / "WorldState.java"

edit(WS, "        List<Event> events,\n        List<Building> buildings,",
     "        List<Event> events,\n        List<Letter> letters,\n        List<Building> buildings,",
     "record 头加 letters")

edit(WS, "        buildings = List.copyOf(buildings == null ? List.of() : buildings);",
     "        letters = List.copyOf(letters == null ? List.of() : letters);\n"
     "        buildings = List.copyOf(buildings == null ? List.of() : buildings);",
     "构造器拷贝 letters")

edit(WS, "        Set<String> buildingIds = new LinkedHashSet<>();",
     "        Set<String> letterIds = new LinkedHashSet<>();\n"
     "        for (Letter l : letters) {\n"
     "            if (!letterIds.add(l.id())) {\n"
     "                throw new IllegalArgumentException(\"国书 id 重复：\" + l.id());\n"
     "            }\n"
     "        }\n\n"
     "        Set<String> buildingIds = new LinkedHashSet<>();",
     "构造器校验国书 id 唯一")

edit(WS, "seq, v, relations, events, buildings,", "seq, v, relations, events, letters, buildings,",
     "withNations")
edit(WS, "seq, nations, v, events, buildings,", "seq, nations, v, events, letters, buildings,",
     "withRelations")
edit(WS, "seq, nations, relations,\n                merged, buildings, elapsedOnlineHours);",
     "seq, nations, relations, merged, letters, buildings, elapsedOnlineHours);",
     "withEventsAdded")
edit(WS, "seq, nations, relations,\n                out, buildings, elapsedOnlineHours);",
     "seq, nations, relations, out, letters, buildings, elapsedOnlineHours);",
     "withAllEventsRead")
edit(WS, "newEraId, newEraOrdinal, newSeq, nations,\n                relations, events, buildings, hours);",
     "newEraId, newEraOrdinal, newSeq, nations,\n"
     "                relations, events, letters, buildings, hours);",
     "withClock")

CODEC = MAIN / "persist" / "StateCodec.java"
edit(CODEC, "import com.wildernessnation.statecraft.core.model.Event;",
     "import com.wildernessnation.statecraft.core.letter.Letter;\n"
     "import com.wildernessnation.statecraft.core.letter.LetterKind;\n"
     "import com.wildernessnation.statecraft.core.letter.LetterState;\n"
     "import com.wildernessnation.statecraft.core.model.Event;",
     "codec import")
edit(CODEC, '        List<StateNode> buildings = new ArrayList<>();',
     '        List<StateNode> letters = new ArrayList<>();\n'
     '        for (Letter l : state.letters()) {\n'
     '            letters.add(writeLetter(l));\n'
     '        }\n'
     '        root.put("letters", new StateNode.Arr(letters));\n\n'
     '        List<StateNode> buildings = new ArrayList<>();',
     "codec 写出 letters")
edit(CODEC, "    private static StateNode writeBuilding(Building b) {",
     "    private static StateNode writeLetter(Letter l) {\n"
     "        Map<String, StateNode> f = StateNode.fields();\n"
     "        f.put(\"id\", new StateNode.Str(l.id()));\n"
     "        f.put(\"fromNationId\", new StateNode.Str(l.fromNationId()));\n"
     "        f.put(\"kind\", new StateNode.Str(l.kind().name()));\n"
     "        f.put(\"targetNationId\", new StateNode.Str(l.targetNationId()));\n"
     "        f.put(\"stopBuildRadius\", new StateNode.Dec(l.stopBuildRadius()));\n"
     "        f.put(\"amount\", new StateNode.Dec(l.amount()));\n"
     "        f.put(\"issuedAtSeq\", new StateNode.Int(l.issuedAtSeq()));\n"
     "        f.put(\"deadlineSeq\", new StateNode.Int(l.deadlineSeq()));\n"
     "        f.put(\"state\", new StateNode.Str(l.state().name()));\n"
     "        return new StateNode.Obj(f);\n"
     "    }\n\n"
     "    private static StateNode writeBuilding(Building b) {",
     "codec writeLetter")
edit(CODEC, '        List<Building> buildings = new ArrayList<>();',
     '        List<Letter> letters = new ArrayList<>();\n'
     '        List<StateNode> letterNodes = root.field(p, "letters").asArr(p + ".letters").items();\n'
     '        for (int i = 0; i < letterNodes.size(); i++) {\n'
     '            letters.add(readLetter(letterNodes.get(i), StateNode.index(p + ".letters", i)));\n'
     '        }\n\n'
     '        List<Building> buildings = new ArrayList<>();',
     "codec 读 letters")
edit(CODEC, "                events, buildings, hours);", "                events, letters, buildings, hours);",
     "codec 传给 WorldState")
edit(CODEC, "    private static Building readBuilding(StateNode node, String p) {",
     "    private static Letter readLetter(StateNode node, String p) {\n"
     "        return new Letter(\n"
     "                node.field(p, \"id\").asString(p + \".id\"),\n"
     "                node.field(p, \"fromNationId\").asString(p + \".fromNationId\"),\n"
     "                readEnum(LetterKind.class, node.field(p, \"kind\").asString(p + \".kind\"),\n"
     "                        p + \".kind\"),\n"
     "                node.field(p, \"targetNationId\").asString(p + \".targetNationId\"),\n"
     "                node.field(p, \"stopBuildRadius\").asDouble(p + \".stopBuildRadius\"),\n"
     "                node.field(p, \"amount\").asDouble(p + \".amount\"),\n"
     "                node.field(p, \"issuedAtSeq\").asLong(p + \".issuedAtSeq\"),\n"
     "                node.field(p, \"deadlineSeq\").asLong(p + \".deadlineSeq\"),\n"
     "                readEnum(LetterState.class, node.field(p, \"state\").asString(p + \".state\"),\n"
     "                        p + \".state\"));\n"
     "    }\n\n"
     "    private static Building readBuilding(StateNode node, String p) {",
     "codec readLetter")

edit(MAIN / "persist" / "StateMigrations.java",
     '        fields.put("buildings", new StateNode.Arr(List.of()));',
     '        fields.put("letters", new StateNode.Arr(List.of()));\n'
     '        fields.put("buildings", new StateNode.Arr(List.of()));',
     "迁移补空 letters")

edit(MAIN / "gen" / "WorldGenerator.java",
     "initialRelations(nations), List.of(), List.of(),\n                0.0);",
     "initialRelations(nations), List.of(), List.of(), List.of(),\n                0.0);",
     "WorldGenerator 补 letters")

edit(MAIN / "settle" / "SettlementEngine.java",
     "state.seq(), nations, relations, state.events(), state.buildings(),",
     "state.seq(), nations, relations, state.events(), state.letters(), state.buildings(),",
     "SettlementEngine 带 letters")


def patch_tests() -> None:
    target = "new WorldState("
    insert = " List.of(),"
    total = 0
    for path in sorted((SRC / "test").rglob("*.java")):
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
            body = text[j + len(target):k]
            depth, commas = 0, []
            for idx, ch in enumerate(body):
                if ch in "([{":
                    depth += 1
                elif ch in ")]}":
                    depth -= 1
                elif ch == "," and depth == 0:
                    commas.append(idx)
            if len(commas) < 2:
                raise SystemExit(f"{path.name}: 参数太少，无法插在倒数第二之前")
            cut = commas[-2]
            if body[cut + 1:cut + 14].strip().startswith("List.of()"):
                out.append(body)
            else:
                out.append(body[:cut + 1] + insert + body[cut + 1:])
                n += 1
            out.append(")")
            i = k + 1
        if n:
            path.write_text("".join(out), encoding="utf-8", newline="")
            print(f"  ok: {path.name} 补了 {n} 处")
            total += n
    print(f"  测试共补 {total} 处")


print("测试构造点：")
patch_tests()
sys.exit(0)
