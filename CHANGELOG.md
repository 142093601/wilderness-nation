# 变更记录

> 纪律（来自 `pack-distribution` 技能）：**每次发布写清三件事**——
> ① 新增 / 移除 / 升级了哪些 mod ② 玩家**要不要开新档** ③ 有没有**需要手动做**的事。
> 少写这三样，玩家就会来问你三样。

版本号规则：**改玩法或加 mod = 次版本号**，只修配置 = 修订号。

---

## 0.2.0-dev · 进行中（未装机）

**状态**：**选型已定案，但一个 mod 都还没装进包里。**

- ✅ **mod 清单定案：180 条**（`MODLIST.md` / `modlist.tsv`），**180/180 通过核验**
- ✅ 选型方法确立：三条证据来源并用 —— Modrinth 接口筛选 · 本机成熟包实证（补 CF 侧）· **跨整合包共识**
  （解析 **50 个 NeoForge 整合包**的 `.mrpack`，共 1816 个 mod）
- ✅ **中文化方案定案**：改用社区四层设施（I18nUpdateMod + 汉化资源包 + VaultPatcher + KubeJS 注入），
  在一个 479 mod 的同版本包上验证过 → 不再需要手写翻译
- ✅ 任务书载体改定 **FTB Quests**（替代原 Questlog：原选择是因为候选池只扫 Modrinth，属证据盲区）
- ✅ 工具链扩展到 8 个新工具（跨包共识、参照包对差、清单核验、mod 详情、jar 探针、端侧分类等）

### 新增（mod：仅"定案"，尚未安装）

**180 条已定案**，其中最关键的几类：性能组补漏（`badoptimizations` / `dynamic-fps`）·
Create 生态 30+ 件（含 `create-pattern-schematics` 图纸图案、铁路三件套）·
建筑构件（Macaw's 全套 · `medieval-buildings` · `framedblocks`）·
结构扩容（YUNG's 全家桶 · Twilight Forest · Repurposed Structures）·
多人体验（`lootr` 不抢箱 · `corpse` · 拼音搜索 `jecharacters`）·
中文化（`i18nupdatemod`）· 任务书（FTB Quests 系列）。

### 移除

- **Questlog** → 降为备选（改用 FTB Quests）
- **FTB Essentials / Ultimine / Ranks** → 明确不装（分别会打掉"交通等级决定远征半径"、"材料经济"、"零头衔"三条设计约束）

### 玩家需要手动做的事

- **无**（还不能玩）

### 要不要开新档

- **是**。世界生成类 mod（`terralith`）**必须在建世界前就位**，且**不能从已有世界移除**。

---

## 0.1.0 · 待发布（尚未可玩）

**状态**：这是**技术栈与工程骨架**，不是可玩的包。当前只装了 19 个 mod（性能组 + Create + OPAC + YBD），
设计稿要的 80~120 个内容 mod **还没进来**（见 `MODPLAN.md` 的分层计划）。

### 新增

- **工程骨架**：`tools/` 工具链（自动启动、真客户端驱动、服务端 + RCON、图纸校验、中文审计、接口调研）
- **设计定稿**：`DESIGN.md` v3 —— 六个时代的玩法规格按 **150 小时**口径（12/20/25/25/30/38），
  以「时代」为唯一刻度
- **选型**：`SELECTION.md`（功能位决策表）· `MODPLAN.md`（472 个候选分层计划）· `CANDIDATES*.md`（原始候选）
- **packwiz 骨架**：`pack/pack.toml` 锁定 `1.21.1` + `neoforge 21.1.250`

### 移除

- **C2ME**（0.4.0-alpha）：覆盖 ModernFix 四项优化（含 `bugfix.chunk_deadlock`）、与 ScalableLux 领域重叠、
  mixin 静态绑定违规、与 RoadWeaver 已知不兼容。证据见 `baseline.csv`。

### 玩家需要手动做的事

- **无**（还不能玩）

### 要不要开新档

- **是**。世界生成类 mod（Terralith）**必须在建世界前就位**，且**不能从已有世界移除**。
