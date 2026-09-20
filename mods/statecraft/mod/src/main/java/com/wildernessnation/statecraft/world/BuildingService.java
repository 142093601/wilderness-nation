package com.wildernessnation.statecraft.world;

import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.building.IdempotencyConfig;
import com.wildernessnation.statecraft.core.building.IdempotencyRules;
import com.wildernessnation.statecraft.core.building.MaterializationDecision;
import com.wildernessnation.statecraft.core.building.MaterializationThrottle;
import com.wildernessnation.statecraft.core.building.StaffObservation;
import com.wildernessnation.statecraft.core.env.EnvironmentVerdict;
import com.wildernessnation.statecraft.core.model.Building;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.staff.StaffBinding;
import com.wildernessnation.statecraft.core.staff.StaffDef;
import com.wildernessnation.statecraft.data.StatecraftData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * 建筑服务：**core 的规则 + mc 的世界**之间的那层薄胶水（`NATIONS.md` §10.1/§10.3/§10.4）。
 *
 * <p>这一层只做四件事，而且每一件都必须是"读世界 / 动世界"：
 * <ol>
 *   <li>把方块位置认成锚点（{@link #anchorAt}）；</li>
 *   <li>登记 / 升级建筑（{@link #register}）；</li>
 *   <li>把世界观察成 {@link StaffObservation}（{@link #observe}）；</li>
 *   <li>按 {@link IdempotencyRules} 的决定**动手**（{@link #materialize}）。</li>
 * </ol>
 *
 * <p><b>判定一个字都不在这里写</b>：该不该生成、要不要等冷却、是不是失效，
 * 全部来自 core；这里只把 core 的决定翻译成"刷一个村民 / 什么都不做 / 标记失效"。
 *
 * <h2>v1 的村民是**原版 Villager + 持久化标签**</h2>
 *
 * <p>§10.2 要的是"继承原版 Villager 的自研实体"。这一步先用原版实体：
 * §10.3 的绑定校验只需要**身份**（职业 + 归属建筑），而那两样用 {@code PersistentData}
 * 就足够表达；自研实体换来的是"禁繁殖 / 锁职业 / 游荡限制 / 不被自然清除"这些**行为**打磨。
 * 所以先让整条链路（图纸 → 锚点 → 环境 → 绑定村民）在游戏里跑通，
 * 行为打磨与自研实体一起做（记在 `DEFERRED.md` §I）。
 */
public final class BuildingService {

    private static final Logger LOG = LogUtils.getLogger();

    /** 村民身上记职业的键。 */
    public static final String TAG_PROFESSION = "statecraft:profession";

    /** 村民身上记归属建筑的键（§10.3 的第三件校验）。 */
    public static final String TAG_BUILDING = "statecraft:building";

    /**
     * 找村民时以锚点为心搜多大范围（格）。
     *
     * <p>§10.2 要求"游荡限制在建筑半径内"，所以 16 格足够覆盖一个还没做行为打磨的村民；
     * 而且这个搜索是**按 AABB 查已加载区块**，不依赖按 UUID 的索引。
     */
    private static final double SEARCH_RADIUS = 16.0;

    private final IdempotencyRules rules = new IdempotencyRules(IdempotencyConfig.defaults());
    private final MaterializationThrottle throttle =
            new MaterializationThrottle(IdempotencyConfig.defaults());
    private final ClaimProbe claims;

    public BuildingService(ClaimProbe claims) {
        this.claims = claims;
    }

    // ---- 认锚点 ----

    /** 这个位置上有没有"某座建筑的锚点方块"。多个定义共用同一个方块时按数据顺序取第一个。 */
    public Optional<BuildingDef> anchorAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (BuildingDef def : StatecraftData.buildings().all()) {
            if (def.anchorBlock().equals(id)) {
                return Optional.of(def);
            }
        }
        return Optional.empty();
    }

    /** 环境判定（§10.5）：读世界 → core 出可用档位 + 缺什么。 */
    public EnvironmentVerdict verdict(ServerLevel level, BuildingDef def, BlockPos pos) {
        return def.evaluate(EnvScanner.scan(level, pos, claims));
    }

    // ---- 登记 ----

    /**
     * 登记一座建筑（已经登记过就返回它，不改动）。
     *
     * <p>id 由"类型 + 坐标"合成，而不是随机数或自增计数器：**同一个锚点永远得到同一个 id**，
     * 所以区块重载 / 服务器重启之后不会冒出一座"新的"建筑（§10.3 的幂等从这里开始）。
     */
    public Optional<Building> register(WorldState state, BuildingDef def, ServerLevel level,
            BlockPos pos) {
        String id = idFor(def, level, pos);
        if (state.buildings().stream().anyMatch(b -> b.id().equals(id))) {
            return Optional.empty();
        }
        Building building = Building.register(id, def.id(),
                new Building.Anchor(level.dimension().location().toString(),
                        pos.getX(), pos.getY(), pos.getZ()));
        List<Building> all = new ArrayList<>(state.buildings());
        all.add(building);
        return Optional.of(building);
    }

    public static String idFor(BuildingDef def, ServerLevel level, BlockPos pos) {
        return String.format(Locale.ROOT, "%s@%s:%d,%d,%d", def.id(),
                level.dimension().location(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** 登记表里找一座建筑（按 id）。 */
    public static Optional<Building> find(WorldState state, String id) {
        return state.buildings().stream().filter(b -> b.id().equals(id)).findFirst();
    }

    /** 按坐标找（"这个锚点登记过没有"）。 */
    public static Optional<Building> findAt(WorldState state, ServerLevel level, BlockPos pos) {
        String dim = level.dimension().location().toString();
        return state.buildings().stream().filter(b -> b.anchor().dimension().equals(dim)
                && b.anchor().x() == pos.getX() && b.anchor().y() == pos.getY()
                && b.anchor().z() == pos.getZ()).findFirst();
    }

    // ---- 观察 ----

    /**
     * 把世界读成 §10.3 的三件校验 + 锚点。
     *
     * <p>mod 层只报告**原始事实**（实体在不在、什么职业、身上记着哪个 buildingId、锚点还在不在），
     * "这算不算绑定健康"由 core 的 {@link StaffBinding#observe} 判。
     */
    /**
     * 找这座建筑的村民。
     *
     * <p>**两步查**，顺序有意义：
     * <ol>
     *   <li>先按存档里记的 {@code staffUUID} 找；</li>
     *   <li>找不到就按**身上记着这座建筑的标签**（{@link #TAG_BUILDING}）找 —— 找到就"改认"它。</li>
     * </ol>
     *
     * <p>为什么必须有第二步（2026-09-20 实测）：**UUID 只是缓存，标签才是绑定的真相**
     * （§10.3 的第三件校验本来就是拿 {@code buildingId} 比）。只按 UUID 查的话，
     * 任何一次"查不到"（刚开服、区块刚加载、存档里的 UUID 写坏）都会被当成"村民没了"，
     * 而 §10.4 的应对是**再刷一个** —— 实测连着重启几次就攒出了 **4 个文书**。
     */
    public Optional<StaffBinding.StaffIdentity> findStaff(
            ServerLevel level, BuildingDef def, Building building) {
        List<StaffBinding.StaffIdentity> all = findAllStaff(level, building);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * 找出**所有**身上记着这座建筑的村民（一台建筑理应只有一个）。
     *
     * <p>为什么要返回全部而不是第一个：2026-09-20 那个 bug 已经在这个世界里留下过
     * **6 个同名文书**。只取第一个的话，多出来的那些会永远站在那儿、谁也看不见它们存在。
     * 有了这个清单，{@code /statecraft buildings} 能报出 `duplicates=N`，
     * {@code settle} 也能顺手把多出来的清掉（见 {@link #pruneDuplicates}）。
     */
    public List<StaffBinding.StaffIdentity> findAllStaff(ServerLevel level, Building building) {
        BlockPos anchor = anchorPos(building);
        if (anchor == null) {
            return List.of();
        }
        String buildingId = building.id();
        List<StaffBinding.StaffIdentity> out = new ArrayList<>();
        // 先按 UUID 认（若它还在），再按标签补齐 —— 顺序决定 findStaff 返回谁
        String uuid = building.staffUUID();
        if (uuid != null && !uuid.isBlank()) {
            UUID parsed = parseOrNull(uuid);
            if (parsed != null) {
                near(level, anchor, e -> e.getUUID().equals(parsed)).ifPresent(out::add);
            }
        }
        for (Entity entity : level.getEntities((Entity) null,
                new AABB(anchor).inflate(SEARCH_RADIUS),
                e -> buildingId.equals(e.getPersistentData().getString(TAG_BUILDING)))) {
            StaffBinding.StaffIdentity id = new StaffBinding.StaffIdentity(
                    entity.getUUID().toString(),
                    entity.getPersistentData().getString(TAG_PROFESSION),
                    entity.getPersistentData().getString(TAG_BUILDING));
            if (out.stream().noneMatch(x -> x.uuid().equals(id.uuid()))) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    /**
     * 只留一个：**多出来的那些是历史 bug 的产物**（同一个 buildingId 被刷了多次），
     * 清掉它们才是"这座建筑有一个文书"这个事实。
     *
     * <p>为什么敢删：判据是**我们自己的持久化标签**（`statecraft:building` == 这座建筑的 id），
     * 不是一个模糊的"附近所有村民"。所以它不会碰到玩家的任何村民。
     * 而且每次删除都记 WARN —— 这种事必须留痕。
     *
     * @return 删掉了几个
     */
    public int pruneDuplicates(ServerLevel level, Building building) {
        List<StaffBinding.StaffIdentity> all = findAllStaff(level, building);
        if (all.size() <= 1) {
            return 0;
        }
        Set<String> keep = new HashSet<>();
        String uuid = building.staffUUID();
        if (uuid != null && !uuid.isBlank()) {
            keep.add(uuid);
        }
        keep.add(all.get(0).uuid());          // 登记表里那个不在（或没登记）时，留第一个
        int removed = 0;
        for (StaffBinding.StaffIdentity id : all) {
            if (keep.contains(id.uuid())) {
                continue;
            }
            UUID parsed = parseOrNull(id.uuid());
            if (parsed == null) {
                continue;
            }
            Optional<Entity> entity = nearEntity(level, anchorPos(building),
                    e -> e.getUUID().equals(parsed));
            if (entity.isPresent() && building.id().equals(
                    entity.get().getPersistentData().getString(TAG_BUILDING))) {
                entity.get().discard();
                removed++;
                LOG.warn("Statecraft：清掉「{}」多出来的一个村民 {}（历史 bug 留下的重复）",
                        building.id(), id.uuid());
            }
        }
        return removed;
    }

    @Nullable
    private static UUID parseOrNull(String uuid) {
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 在锚点周围找**第一个**符合条件的实体（按 AABB 查已加载区块，不依赖按 UUID 的索引）。
     *
     * <p>为什么不用 {@code level.getEntity(uuid)}：服务器刚起来那一刻，从磁盘载入的实体
     * 还没被索引到，按 UUID 查会返回 null（"村民明明站在那里"却被判成"人没了"）。
     */
    private static Optional<StaffBinding.StaffIdentity> near(
            ServerLevel level, BlockPos anchor, java.util.function.Predicate<Entity> match) {
        return nearEntity(level, anchor, match).map(BuildingService::identityOf);
    }

    /** 找实体本体（诊断/清理要动它，光有身份不够）。 */
    private static Optional<Entity> nearEntity(
            ServerLevel level, BlockPos anchor, java.util.function.Predicate<Entity> match) {
        for (Entity entity : level.getEntities((Entity) null,
                new AABB(anchor).inflate(SEARCH_RADIUS), match)) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    private static StaffBinding.StaffIdentity identityOf(Entity entity) {
        return new StaffBinding.StaffIdentity(entity.getUUID().toString(),
                entity.getPersistentData().getString(TAG_PROFESSION),
                entity.getPersistentData().getString(TAG_BUILDING));
    }

    public StaffObservation observe(ServerLevel level, BuildingDef def, Building building) {
        return observeAndRebind(level, def, building).observation();
    }

    @Nullable
    public static BlockPos anchorPos(Building building) {
        if (!building.anchor().dimension().isEmpty()) {
            return new BlockPos(building.anchor().x(), building.anchor().y(), building.anchor().z());
        }
        return null;
    }

    // ---- 实体化 ----

    /** 一次实体化的结果（给命令回话与日志用）。 */
    public record Step(String buildingId, MaterializationDecision decision, String detail) {}

    /**
     * 一次实体化的**全部**结果。
     *
     * <p>为什么把新状态一起返回、而不是让调用方自己去猜：绑定村民这件事是**这一层干的**，
     * 只有它知道"刚绑上的 UUID 与时间戳"。状态要是留在里面，调用方就只好再扫一遍世界去
     * 重建绑定——那是把同一个判定写两遍，两边一定会漂。
     */
    public record SettleResult(WorldState state, List<Step> steps) {

        public long boundCount() {
            return state.buildings().stream().filter(b -> !b.stalled()).count();
        }
    }

    /**
     * 跑一次实体化（幂等）：先逐座判定，再用 {@link MaterializationThrottle} 决定**这一轮允许谁**动手。
     *
     * <p>顺序必须是这样——先把所有建筑判一遍是为了拿到"谁够格重生"，
     * 再由节流决定"哪一处真的动手"（§十二：同一次最多 1 处、两次间隔 ≥5 分钟，其余排队）。
     *
     * @param seq   当次序号（同 seq 不重复生成，§10.3）
     * @param hours 累计在线小时（**节流用的是它，不是 seq**）
     */
    public SettleResult settle(ServerLevel level, WorldState state, long seq, double hours) {
        List<Step> steps = new ArrayList<>();
        List<Building> eligible = new ArrayList<>();
        for (Building building : state.buildings()) {
            Optional<BuildingDef> def = StatecraftData.buildings().byId(building.type());
            if (def.isEmpty()) {
                steps.add(new Step(building.id(), MaterializationDecision.MARK_INVALID,
                        "buildings.json 里已经没有「" + building.type() + "」这座建筑了"));
                continue;
            }
            BlockPos anchor = anchorPos(building);
            if (anchor == null || !level.isLoaded(anchor)) {
                // **区块没加载就别判**：`getBlockState` 会读成空气（→ 误判"锚点没了"），
                // `getEntity(uuid)` 也找不到那个村民（→ 误判"村民不在"→ **又刷一个**）。
                // 那正是 §10.3 说的"生出三个文书"。所以这一步由 mod 层挡下来，
                // 规则层看不到这条不可靠的观察。
                steps.add(new Step(building.id(), MaterializationDecision.WAIT_COOLDOWN,
                        "锚点所在的区块没加载，这次跳过（观察不可靠）"));
                continue;
            }
            StaffObservation obs = observe(level, def.get(), building);
            MaterializationDecision decision = rules.decide(building, obs, seq, hours);
            eligible.add(building);
            steps.add(new Step(building.id(), decision, describe(def.get(), obs, decision)));
        }

        List<Building> ready = new ArrayList<>();
        for (Building b : eligible) {
            if (decisionOf(steps, b).orElse(null) == MaterializationDecision.RESPAWN) {
                ready.add(b);
            }
        }
        List<Building> chosen = throttle.select(eligible, ready, hours, 0);
        WorldState next = state;
        for (Building building : chosen) {
            BuildingDef def = StatecraftData.buildings().require(building.type());
            // 动手之前再确认一次"真的没有"：够格重生到真的刷之间可能已经过去了很久
            // （节流会排队），这一段时间里村民完全可能已经被别的东西刷回来了。
            Optional<StaffBinding.StaffIdentity> existing = findStaff(level, def, building);
            if (existing.isPresent()) {
                next = next.withBuilding(rebind(building, existing.get()));
                replace(steps, building.id(), new Step(building.id(),
                        MaterializationDecision.SKIP, "已经有人站在这儿了（改绑到 "
                        + existing.get().uuid() + "），不重复生成"));
                continue;
            }
            Building updated = spawnStaff(level, def, building, seq, hours);
            if (updated == null) {
                continue;
            }
            next = next.withBuilding(updated);
            replace(steps, building.id(), new Step(building.id(),
                    MaterializationDecision.RESPAWN, "已绑定村民 " + updated.staffUUID()
                    + "（" + def.staffProfession() + "）"));
        }

        // 顺手清掉历史 bug 留下的重复（**每座建筑都查，不只这一轮动手的那几座** ——
        // 重复的那些很可能正是"绑得好好的、所以永远轮不到它重生"的建筑身上）
        for (Building building : next.buildings()) {
            int removed = pruneDuplicates(level, building);
            if (removed > 0) {
                replace(steps, building.id(), new Step(building.id(),
                        MaterializationDecision.SKIP,
                        "清掉了 " + removed + " 个多出来的村民（历史 bug 的残留）"));
            }
        }
        return new SettleResult(next, List.copyOf(steps));
    }

    /**
     * 一句话描述这座建筑现在的村民（{@code /statecraft buildings} 用）：
     * 名字 / 职业 / 是不是 NoAI。
     *
     * <p>为什么要把 NoAI 也报出来：§10.2 的三条要求（禁繁殖/锁职业/不游荡）
     * 就是靠它实现的，所以"它到底是不是 NoAI"是**可验收项**，不该只存在于代码里。
     */
    public Optional<String> describeStaff(ServerLevel level, Building building) {
        BlockPos anchor = anchorPos(building);
        if (anchor == null) {
            return Optional.empty();
        }
        String uuid = building.staffUUID();
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        UUID parsed = parseOrNull(uuid);
        if (parsed == null) {
            return Optional.of("（staffUUID 写坏了：" + uuid + "）");
        }
        Optional<Entity> entity = nearEntity(level, anchor, e -> e.getUUID().equals(parsed));
        if (entity.isEmpty()) {
            return Optional.of("（附近找不到 " + uuid + "）");
        }
        String name = entity.get().getCustomName() == null
                ? "（无名）" : entity.get().getCustomName().getString();
        boolean noAi = entity.get() instanceof Mob mob && mob.isNoAi();
        return Optional.of(name + "/" + entity.get().getPersistentData().getString(TAG_PROFESSION)
                + "/no_ai=" + noAi);
    }

    /**
     * 把登记表里的 {@code staffUUID} 改成**世界里那一个**（UUID 缓存过期时自愈）。
     *
     * <p>只改 UUID：实体化时刻保持原样 —— 否则每观察一次就等于"刚实体化过一次"，
     * §十二 的节流会被自己的自愈动作一次次顶掉。
     */
    private static Building rebind(Building building, StaffBinding.StaffIdentity found) {
        return building.withStaff(found.uuid(), building.materializedAtSeq(),
                building.materializedAtHours());
    }

    /**
     * 观察一次并把"UUID 缓存过期"顺手修掉（{@code /statecraft buildings} 也会用到）。
     *
     * @return 观察结果 + 是否发生过改绑
     */
    public Rebind observeAndRebind(ServerLevel level, BuildingDef def, Building building) {
        Optional<StaffBinding.StaffIdentity> seen = findStaff(level, def, building);
        StaffObservation obs = StaffBinding.observe(level == null ? null : def, building.id(),
                seen, anchorIntact(level, def, building));
        boolean rebound = seen.isPresent()
                && !seen.get().uuid().equals(building.staffUUID());
        return new Rebind(obs, rebound ? rebind(building, seen.get()) : building, rebound);
    }

    /** 观察结果 + （可能的）改绑后的建筑。 */
    public record Rebind(StaffObservation observation, Building building, boolean rebound) {}

    private static boolean anchorIntact(ServerLevel level, BuildingDef def, Building building) {
        BlockPos anchor = anchorPos(building);
        return anchor != null && def.anchorBlock().equals(
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(anchor).getBlock()).toString());
    }

    /**
     * 真的刷一个村民并绑上（§10.1 的最后一格）。
     *
     * <p>名字从 `staff.json` 的名字池里取，下标由**建筑 id 的哈希**决定：
     * 于是同一座建筑重建之后还是同一个人名（不会每次区块重载就换人）。
     *
     * <h2>为什么是 {@code setNoAi(true)}（§10.2 的三条要求一次做完）</h2>
     *
     * <p>§10.2 要求自研实体做到"**禁繁殖、锁定职业、游荡限制在建筑半径内**"。
     * 这三条在原版村民身上**全都是 AI 行为**：
     * <ul>
     *   <li>繁殖要 {@code BreedGoal}（还要床与食物）；</li>
     *   <li>认领职业方块要 {@code VillagerGoalPackages} 里的那套目标 —— 而**我们的锚点
     *       恰好是讲台**（`minecraft:lectern`，正是图书管理员的职业方块），
     *       不锁的话它会当场把情报站的锚点认领成工作台；</li>
     *   <li>游荡也是目标驱动的（`RandomStrollGoal`）。</li>
     * </ul>
     * NoAI 一次把三条都关掉，而且**比"加三个补丁"更可靠**：没有 AI 就没有这些行为，
     * 不存在"某个目标被别的 mod 换掉之后补丁失效"。</p>
     *
     * <p>代价是它站在那儿不动（像坐在案前的文书）。§10.2 说得清楚：村民**只做人气 + 界面**，
     * 不盖房、不生产、不搬运、不参战 —— 一个守在案前的文书比一个到处乱走的更贴题。
     * 真想要"会动的人气"要等自研实体（那时用自定义 Brain/动画，而不是把原版 AI 放回来）。</p>
     *
     * <p>职业设成图书管理员纯属**外观**（文书 ≈ 图书管理员），
     * 而且**只在 NoAI 下才安全** —— 没有 AI 就不会去认领职业方块。</p>
     */
    @Nullable
    public Building spawnStaff(ServerLevel level, BuildingDef def, Building building,
            long seq, double hours) {
        BlockPos anchor = anchorPos(building);
        if (anchor == null) {
            return null;
        }
        Optional<StaffDef> staff = StatecraftData.staff().byId(def.staffProfession());
        if (staff.isEmpty()) {
            return null;
        }
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }
        BlockPos spot = spawnSpot(level, anchor);
        villager.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.0f, 0.0f);
        villager.setCustomName(Component.literal(
                staff.get().nameAt(nameIndex(building.id(), staff.get().nameCount()))));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();          // §10.2：不被自然清除
        villager.setNoAi(true);                     // §10.2：禁繁殖 + 锁职业 + 不游荡（见上）
        // 外观：让它看起来像个文书。**NoAI 之下才不会去认领讲台**（那正是我们的锚点）
        villager.setVillagerData(villager.getVillagerData()
                .setProfession(VillagerProfession.LIBRARIAN));
        villager.getPersistentData().putString(TAG_PROFESSION, staff.get().id());
        villager.getPersistentData().putString(TAG_BUILDING, building.id());
        if (!level.addFreshEntity(villager)) {
            return null;
        }
        return building.withStaff(villager.getUUID().toString(), seq, hours);
    }

    /** 站哪：锚点上方那格（不够就往上找两格），都没有就退回锚点自己。 */
    private static BlockPos spawnSpot(ServerLevel level, BlockPos anchor) {
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos p = anchor.above(dy);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                return p;
            }
        }
        return anchor.above();
    }

    /** 名字下标：只用建筑 id 的哈希，**不用随机数**（名字要随存档稳定）。 */
    static int nameIndex(String buildingId, int poolSize) {
        if (poolSize <= 0) {
            return 0;
        }
        int h = buildingId.hashCode() & 0x7fffffff;
        return h % poolSize;
    }

    private static Optional<MaterializationDecision> decisionOf(List<Step> steps, Building b) {
        return steps.stream().filter(s -> s.buildingId().equals(b.id()))
                .map(Step::decision).findFirst();
    }

    private static void replace(List<Step> steps, String id, Step replacement) {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).buildingId().equals(id)) {
                steps.set(i, replacement);
                return;
            }
        }
    }

    private static String describe(BuildingDef def, StaffObservation obs,
            MaterializationDecision decision) {
        return switch (decision) {
            case SKIP -> obs.bindingHealthy() ? "绑定完好，什么都不做"
                    : "同一次结算里已经生成过了";
            case RESPAWN -> obs.staffExists()
                    ? "站在那里的人不对（" + String.join("；",
                            StaffBinding.describeMismatch(def, obs)) + "）"
                    : "村民不在，等冷却";
            case WAIT_COOLDOWN -> "冷却还没到（§十二：两次间隔 ≥5 分钟）";
            case MARK_INVALID -> "锚点没了 → 登记失效（村民保留，§10.4）";
        };
    }
}
