import io

# 1) DataFiles：加 places() / templates() 的读取入口
p = r"D:\project\nation-pack\mods\statecraft\core\src\main\java\com\wildernessnation\statecraft\core\data\DataFiles.java"
t = io.open(p, encoding="utf-8").read()
anchor = "    /** `nations.json`：根是对象 `{ config?, cultures[] }`。 */"
addition = '''    /** `places.json`：根是 `{ "capital": [...], "town": [...], … }`。 */
    public static PlaceNames places(StateNode root) {
        return PlaceNames.fromNode(root);
    }

    /** `events.json`：根是 `{ "<textKey>": "<模板>", … }`。 */
    public static TextTemplates templates(StateNode root) {
        return TextTemplates.fromNode(root);
    }

'''
if "public static PlaceNames places(" not in t:
    t = t.replace(anchor, addition + anchor, 1)
    t = t.replace("import com.wildernessnation.statecraft.core.persist.StateNode;",
                  "import com.wildernessnation.statecraft.core.persist.StateNode;\n"
                  "import com.wildernessnation.statecraft.core.text.PlaceNames;\n"
                  "import com.wildernessnation.statecraft.core.text.TextTemplates;", 1)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("ok: DataFiles 加 places/templates")
else:
    print("跳过: DataFiles 已有")

# 2) 数据文件的文档注释补一句
t = io.open(p, encoding="utf-8").read()
old = " * 数据文件（`eras.json` / `nations.json`）→ core 类型的读取规则。"
new = " * 数据文件（`eras.json` / `nations.json` / `places.json` / `events.json`）→ core 类型的读取规则。"
if old in t:
    t = t.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("ok: DataFiles 注释")

# 3) DataFilesTest：补 places / templates 的用例
p = r"D:\project\nation-pack\mods\statecraft\core\src\test\java\com\wildernessnation\statecraft\core\data\DataFilesTest.java"
t = io.open(p, encoding="utf-8").read()
block = '''
    // ---- places.json / events.json（计划 4 的文案层）----

    @Test
    void readsPlacePoolsAndTemplatesFromData() {
        java.util.Map<String, StateNode> places = StateNode.fields();
        places.put("town", new StateNode.Arr(List.of(
                new StateNode.Str("石口"), new StateNode.Str("枯井镇"))));
        PlaceNames pool = DataFiles.places(new StateNode.Obj(places));
        assertTrue(pool.has(PlaceNames.TOWN));
        assertEquals(2, pool.kinds().size(), "只有一种池子");

        java.util.Map<String, StateNode> events = StateNode.fields();
        events.put("battle", new StateNode.Str("{0}攻破{1}边镇『{p:town}』"));
        TextTemplates templates = DataFiles.templates(new StateNode.Obj(events));
        assertTrue(templates.has("battle"));
        assertEquals("{0}攻破{1}边镇『{p:town}』", templates.require("battle"));
    }

    @Test
    void placePoolAndTemplateTablesRejectBadData() {
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.places(new StateNode.Obj(StateNode.fields())));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.templates(new StateNode.Obj(StateNode.fields())));
        // 模板内容不能是空白
        java.util.Map<String, StateNode> blank = StateNode.fields();
        blank.put("x", new StateNode.Str("   "));
        assertThrows(IllegalArgumentException.class,
                () -> DataFiles.templates(new StateNode.Obj(blank)));
    }
}
'''
if "readsPlacePoolsAndTemplatesFromData" not in t:
    idx = t.rstrip().rfind("}")
    t = t[:idx] + block + t[idx + 1:]
    if "import com.wildernessnation.statecraft.core.text.PlaceNames;" not in t:
        t = t.replace("import com.wildernessnation.statecraft.core.persist.StateNode;",
                      "import com.wildernessnation.statecraft.core.persist.StateNode;\n"
                      "import com.wildernessnation.statecraft.core.text.PlaceNames;\n"
                      "import com.wildernessnation.statecraft.core.text.TextTemplates;", 1)
    io.open(p, "w", encoding="utf-8", newline="").write(t)
    print("ok: DataFilesTest 补用例")
else:
    print("跳过: DataFilesTest 已有")
