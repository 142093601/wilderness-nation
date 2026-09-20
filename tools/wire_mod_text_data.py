import io

p = r"D:\project\nation-pack\mods\statecraft\mod\src\main\java\com\wildernessnation\statecraft\data\StatecraftData.java"
t = io.open(p, encoding="utf-8").read()
if "PLACES_PATH" in t:
    print("跳过：已经接好")
    raise SystemExit(0)

NL = "\n"

t = t.replace(
    '    private static final String NATIONS_PATH = "/data/statecraft/nations.json";',
    '    private static final String NATIONS_PATH = "/data/statecraft/nations.json";' + NL +
    '    private static final String PLACES_PATH = "/data/statecraft/places.json";' + NL +
    '    private static final String EVENTS_PATH = "/data/statecraft/events.json";', 1)

t = t.replace(
    "    private static StateMigrator migrator;",
    "    private static StateMigrator migrator;" + NL +
    "    private static PlaceNames places;" + NL +
    "    private static TextTemplates templates;", 1)

t = t.replace(
    "        migrator = StateMigrations.production();",
    "        places = DataFiles.places(read(PLACES_PATH));" + NL +
    "        templates = DataFiles.templates(read(EVENTS_PATH));" + NL +
    "        migrator = StateMigrations.production();", 1)

t = t.replace(
    "    public static StateMigrator migrator() {",
    "    public static PlaceNames places() {" + NL +
    "        requireLoaded();" + NL +
    "        return places;" + NL +
    "    }" + NL + NL +
    "    public static TextTemplates templates() {" + NL +
    "        requireLoaded();" + NL +
    "        return templates;" + NL +
    "    }" + NL + NL +
    "    public static StateMigrator migrator() {", 1)

t = t.replace(
    "import com.wildernessnation.statecraft.core.persist.StateNode;",
    "import com.wildernessnation.statecraft.core.persist.StateNode;" + NL +
    "import com.wildernessnation.statecraft.core.text.PlaceNames;" + NL +
    "import com.wildernessnation.statecraft.core.text.TextTemplates;", 1)

io.open(p, "w", encoding="utf-8", newline="").write(t)
print("ok: StatecraftData 载入 places/events")
