import io

p = r"D:\project\nation-pack\mods\statecraft\core\src\main\java\com\wildernessnation\statecraft\core\model\WorldState.java"
t = io.open(p, encoding="utf-8").read()
NL = "\n"

# 1) import Letter
if "import com.wildernessnation.statecraft.core.letter.Letter;" not in t:
    t = t.replace("import java.util.ArrayList;",
                  "import com.wildernessnation.statecraft.core.letter.Letter;" + NL
                  + "import java.util.ArrayList;", 1)
    print("ok: import Letter")

# 2) 补回 withLetters / withLetter / withBuildings / withBuilding
if "public WorldState withBuildings(" not in t:
    block = (
        "    public WorldState withLetters(List<Letter> v) {" + NL +
        "        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations," + NL +
        "                relations, events, v, buildings, elapsedOnlineHours);" + NL +
        "    }" + NL + NL +
        "    /** 换掉一封国书（其余原样）。 */" + NL +
        "    public WorldState withLetter(Letter replacement) {" + NL +
        "        List<Letter> out = new ArrayList<>(letters.size());" + NL +
        "        for (Letter l : letters) {" + NL +
        "            out.add(l.id().equals(replacement.id()) ? replacement : l);" + NL +
        "        }" + NL +
        "        return withLetters(out);" + NL +
        "    }" + NL + NL +
        "    public WorldState withBuildings(List<Building> v) {" + NL +
        "        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations," + NL +
        "                relations, events, letters, v, elapsedOnlineHours);" + NL +
        "    }" + NL + NL +
        "    /** 换掉一座建筑（其余原样）。 */" + NL +
        "    public WorldState withBuilding(Building replacement) {" + NL +
        "        List<Building> out = new ArrayList<>(buildings.size());" + NL +
        "        for (Building b : buildings) {" + NL +
        "            out.add(b.id().equals(replacement.id()) ? replacement : b);" + NL +
        "        }" + NL +
        "        return withBuildings(out);" + NL +
        "    }" + NL + NL)
    idx = t.rstrip().rfind("}")
    t = t[:idx] + block + t[idx:]
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("ok: 补回 withLetters/withLetter/withBuildings/withBuilding")
else:
    print("跳过：withBuildings 已在")
