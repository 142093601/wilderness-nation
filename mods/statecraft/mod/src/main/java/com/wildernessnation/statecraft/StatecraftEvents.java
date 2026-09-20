package com.wildernessnation.statecraft;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.building.BuildingAbility;
import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.building.StaffObservation;
import com.wildernessnation.statecraft.core.blueprint.BlueprintRules;
import com.wildernessnation.statecraft.core.blueprint.BlueprintVerdict;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyAction;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyConfig;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyMachine;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyRequest;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyResult;
import com.wildernessnation.statecraft.core.diplomacy.PlayerActions;
import com.wildernessnation.statecraft.core.env.EnvironmentSnapshot;
import com.wildernessnation.statecraft.core.env.EnvironmentVerdict;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.intel.IntelBook;
import com.wildernessnation.statecraft.core.intel.IntelContact;
import com.wildernessnation.statecraft.core.intel.IntelTier;
import com.wildernessnation.statecraft.core.intel.IntelVerdict;
import com.wildernessnation.statecraft.core.intel.NationBrief;
import com.wildernessnation.statecraft.core.letter.Letter;
import com.wildernessnation.statecraft.core.letter.LetterMachine;
import com.wildernessnation.statecraft.core.letter.LetterResult;
import com.wildernessnation.statecraft.core.letter.LetterState;
import com.wildernessnation.statecraft.core.model.Building;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.settle.SettlementClock;
import com.wildernessnation.statecraft.core.settle.SettlementConfig;
import com.wildernessnation.statecraft.core.settle.SettlementEngine;
import com.wildernessnation.statecraft.core.settle.SettlementInput;
import com.wildernessnation.statecraft.core.settle.SettlementLog;
import com.wildernessnation.statecraft.core.settle.SettlementLogEntry;
import com.wildernessnation.statecraft.core.settle.SettlementTrigger;
import com.wildernessnation.statecraft.core.staff.StaffBinding;
import com.wildernessnation.statecraft.data.StatecraftData;
import com.wildernessnation.statecraft.data.StatecraftSavedData;
import com.wildernessnation.statecraft.world.BuildingService;
import com.wildernessnation.statecraft.world.ClaimProbe;
import com.wildernessnation.statecraft.world.EnvScanner;
import com.wildernessnation.statecraft.world.PlayerLedger;
import com.wildernessnation.statecraft.world.SettlementScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * 事件与命令：把 core 挂到游戏生命周期上。
 *
 * <p>这里<strong>不做任何计算</strong>——生成交给 core 的 {@code WorldGenerator}，
 * 往返判定交给 {@code StatecraftSavedData.roundTrip()}。mod 层只负责"什么时候调用"。
 */
@EventBusSubscriber(modid = StatecraftMod.MOD_ID)
public final class StatecraftEvents {

    private static final Logger LOG = LogUtils.getLogger();

    /** 结算调度器（§七）：整进程一个。 */
    private static final SettlementScheduler SCHEDULER = new SettlementScheduler();

    /** 自检用的稳定标记，方便自动化脚本断言（中文回显不可靠）。 */
    private static final String MARK_INFO = "STATECRAFT_INFO";
    private static final String MARK_ROUNDTRIP = "STATECRAFT_ROUNDTRIP";
    private static final String MARK_SCAN = "STATECRAFT_SCAN";
    private static final String MARK_ANCHOR = "STATECRAFT_ANCHOR";
    private static final String MARK_BUILDINGS = "STATECRAFT_BUILDINGS";
    private static final String MARK_MATERIALIZE = "STATECRAFT_MATERIALIZE";
    private static final String MARK_ADVANCE = "STATECRAFT_ADVANCE";
    private static final String MARK_LETTERS = "STATECRAFT_LETTERS";
    private static final String MARK_ANSWER = "STATECRAFT_ANSWER";
    private static final String MARK_DIPLO = "STATECRAFT_DIPLO";
    private static final String MARK_CONTACT = "STATECRAFT_CONTACT";
    private static final String MARK_BUILD = "STATECRAFT_BUILD";
    private static final String MARK_INTEL = "STATECRAFT_INTEL";
    private static final String MARK_BLUEPRINT = "STATECRAFT_BLUEPRINT";
    private static final String MARK_PENDING = "STATECRAFT_PENDING";
    /** 时代钥匙：`/statecraft accelerate` 的自检标记（自动化脚本按它断言）。 */
    private static final String MARK_ACCELERATE = "STATECRAFT_ACCELERATE";
    private static final String MARK_LOG = "STATECRAFT_LOG";
    private static final String MARK_UPGRADE = "STATECRAFT_UPGRADE";

    /** "在情报站发起"的半径（§11.2 只说"在情报站发起"，【占位】取 16 格）。 */
    private static final double STATION_RADIUS = 16.0;

    private StatecraftEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null) {
            return;
        }
        StatecraftSavedData data = StatecraftSavedData.get(overworld);
        for (String warning : data.loadWarnings()) {
            LOG.warn("Statecraft 读档：{}", warning);
        }
        // 调度器记到哪儿了（攒到一半的在线小时）也一起恢复，免得每次重启丢掉最多 2 小时
        SCHEDULER.restore(data.pendingHours());
        if (data.state() == null) {
            WorldState generated = generate(overworld);
            data.set(generated);
            LOG.info("Statecraft 建库：seed={} 时代={}({}) 国家={} 个",
                    generated.seed(), generated.eraId(), generated.eraOrdinal(),
                    generated.nations().size());
        } else {
            WorldState state = data.state();
            LOG.info("Statecraft 读档成功：seed={} 时代={}({}) 序号={} 国家={} 个",
                    state.seed(), state.eraId(), state.eraOrdinal(), state.seq(),
                    state.nations().size());
        }
    }

    /** 按世界种子建库（`NATIONS.md` §五：seed 由世界种子派生，首建时写入）。 */
    public static WorldState generate(ServerLevel overworld) {
        BlockPos spawn = overworld.getSharedSpawnPos();
        long seed = overworld.getSeed();
        return new WorldGenerator(StatecraftData.config(), seed)
                .generate(StatecraftData.cultures(), spawn.getX(), spawn.getZ(),
                        StatecraftData.eras().byOrdinal(0).id(), 0);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("statecraft")
                .then(Commands.literal("info").executes(StatecraftEvents::info))
                .then(Commands.literal("roundtrip").executes(StatecraftEvents::roundTrip))
                .then(Commands.literal("regen")
                        .requires(source -> source.hasPermission(2))
                        .executes(StatecraftEvents::regen))
                // ---- 阶段 3：建筑链路（§10）的四条 ----
                .then(Commands.literal("scan")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(StatecraftEvents::scan)))
                .then(Commands.literal("anchor")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(StatecraftEvents::anchor)))
                .then(Commands.literal("buildings").executes(StatecraftEvents::buildings))
                .then(Commands.literal("materialize")
                        .requires(source -> source.hasPermission(2))
                        .executes(StatecraftEvents::materialize))
                .then(Commands.literal("advance")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("hours", DoubleArgumentType.doubleArg(0.01))
                                .executes(StatecraftEvents::advance)))
                // 开发用：往"待结算的在线小时"里注水（§J4 的验收靠它，不然要真等 2 小时）
                .then(Commands.literal("pending")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("hours", DoubleArgumentType.doubleArg(0.0))
                                .executes(StatecraftEvents::pending)))
                // **时代钥匙**（任务书的 command 奖励调它）：把时间推到当前时代的结束点，
                // 于是"提前推进"和"时间到"走的是**同一段代码**（见 SettlementClock.hoursToBoundary）
                .then(Commands.literal("accelerate")
                        .requires(source -> source.hasPermission(2))
                        .executes(StatecraftEvents::accelerate))
                // ---- 阶段 3：事务日志（§十三）----
                .then(Commands.literal("log")
                        .executes(ctx -> log(ctx, 8))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> log(ctx,
                                        IntegerArgumentType.getInteger(ctx, "n")))))
                // ---- 阶段 3：国书（§9.4）的两条 ----
                .then(Commands.literal("letters").executes(StatecraftEvents::letters))
                .then(Commands.literal("answer")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("decision",
                                                StringArgumentType.word())
                                        .executes(StatecraftEvents::answer))))
                // ---- 阶段 3：外交（§11.2 的 6 个动作）----
                .then(diplomacyCommand())
                // ---- 阶段 3：接触（§11.1）----
                .then(Commands.literal("contact")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(StatecraftEvents::contact)))
                // ---- 阶段 3：玩家行为 → 态度（§十二）----
                .then(Commands.literal("build")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(StatecraftEvents::build)))
                // ---- 阶段 3：升级建筑（§11.1 的"对锚点用升级件"）----
                .then(Commands.literal("upgrade")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(StatecraftEvents::upgrade)))
                // ---- 阶段 3：情报册（§11.1）----
                .then(Commands.literal("intel")
                        .executes(ctx -> intel(ctx, "list", ""))
                        .then(Commands.literal("track")
                                .then(Commands.argument("nation", StringArgumentType.word())
                                        .executes(ctx -> intel(ctx, "track",
                                                StringArgumentType.getString(ctx, "nation")))))
                        .then(Commands.literal("untrack")
                                .then(Commands.argument("nation", StringArgumentType.word())
                                        .executes(ctx -> intel(ctx, "untrack",
                                                StringArgumentType.getString(ctx, "nation")))))
                        .then(Commands.literal("deep")
                                .then(Commands.argument("nation", StringArgumentType.word())
                                        .executes(ctx -> intel(ctx, "deep",
                                                StringArgumentType.getString(ctx, "nation"))))))
                // ---- 阶段 3：图纸授权（§10.1）----
                .then(Commands.literal("blueprint")
                        .executes(ctx -> blueprint(ctx, ""))
                        .then(Commands.literal("buy")
                                .then(Commands.argument("building", StringArgumentType.word())
                                        .executes(ctx -> blueprint(ctx,
                                                StringArgumentType.getString(ctx, "building")))))
                        .then(Commands.literal("treasury")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("amount",
                                                DoubleArgumentType.doubleArg(0.0))
                                        .executes(ctx -> treasury(ctx,
                                                DoubleArgumentType.getDouble(ctx, "amount")))))
                        // 开发用：清空账本。存在的唯一理由是**让"没买授权就不许登记"这条门禁
                        // 可被验收**（买过之后就没法再观察它了）
                        .then(Commands.literal("reset")
                                .requires(source -> source.hasPermission(2))
                                .executes(StatecraftEvents::blueprintReset))));
    }

    /** 外交命令单独成一个方法：嵌套的 argument/then 括号数错一次就编译不过，这样好核对。 */
    private static LiteralArgumentBuilder<CommandSourceStack> diplomacyCommand() {
        return Commands.literal("diplomacy")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("nation", StringArgumentType.word())
                        .then(Commands.argument("action", StringArgumentType.word())
                                .executes(ctx -> diplomacy(ctx, 0.0))
                                .then(Commands.argument("dev", DoubleArgumentType.doubleArg(0.0))
                                        .executes(ctx -> diplomacy(ctx,
                                                DoubleArgumentType.getDouble(ctx, "dev"))))));
    }

    /**
     * 玩家动作的随机序号（§11.2 的"外部输入"）。
     *
     * <p>**这是个占位**：core 要求掷骰的动作带一个由调用方递进来、并且被事务日志记下来的 nonce
     * （否则"同 seed 同输入必得同结果"不成立）。mod 层还没接事务日志（计划 5），
     * 所以这里用一个自增计数器保证"同一串命令里各不相同"。
     * 真玩家路径必须把它记进日志 —— 记在 {@code DEFERRED.md} §I2。
     */
    private static final java.util.concurrent.atomic.AtomicLong NONCE =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * `/statecraft diplomacy <nation> <action> [dev]`：发起一次外交动作（§11.2）。
     *
     * <p>动作名用小写：{@code declare_war / peace / alliance / trade / aid / tribute}。
     * {@code dev} 是**玩家方发展度**的占位（它属于计划 5 的玩家账本，现在没有；
     * §11.2 的「朝贡」与求和概率都要用它，所以先用参数顶着，默认 0）。
     */
    private static int diplomacy(CommandContext<CommandSourceStack> ctx, double playerDevelopment) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_DIPLO + " EMPTY"), false);
            return 0;
        }
        String nationId = StringArgumentType.getString(ctx, "nation");
        String raw = StringArgumentType.getString(ctx, "action");
        Optional<DiplomacyAction> action = parseAction(raw);
        if (action.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    MARK_DIPLO + " UNKNOWN_ACTION " + raw
                            + "（可用：declare_war peace alliance trade aid tribute）"), false);
            return 0;
        }
        long nonce = state.seq() * 1000L + NONCE.incrementAndGet();
        // ---- §11.2 的两道"在哪发起"的门槛（core 只管规则，位置是世界事实）----
        // ① 宣战 / 朝贡必须**在情报站发起**（§11.2 的原话，全服公告）
        if (action.get().needsStation()
                && !BUILDINGS.nearBoundStation(source.getLevel(), source.getPosition().x,
                        source.getPosition().y, source.getPosition().z,
                        STATION_RADIUS, state)) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "%s %s REFUSED 这个动作得在情报站发起（%s 格内，且文书要在位）",
                    MARK_DIPLO, action.get(), Math.round(STATION_RADIUS))), false);
            return 0;
        }
        // ② 其余四个动作至少要**有情报册**（§11.1 第一档），而且 declaration 得被时代解锁
        IntelTier tier = currentTier(state);
        boolean declaration = StatecraftData.eras().isUnlocked(IntelTier.UNLOCK_DECLARATION,
                StatecraftData.eras().fallbackForUnknownId(state.eraId(), state.eraOrdinal()));
        if (!tier.allows(action.get(), declaration)) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "%s %s REFUSED 现在的情报能力做不了这个（档位 %s，declaration=%s）",
                    MARK_DIPLO, action.get(), tier, declaration)), false);
            return 0;
        }
        DiplomacyMachine machine = new DiplomacyMachine(
                DiplomacyConfig.defaults(), SettlementConfig.defaults());
        DiplomacyResult result = machine.apply(state,
                new DiplomacyRequest(action.get(), nationId, playerDevelopment, nonce));
        data.set(result.state());
        source.sendSuccess(() -> Component.literal(String.format("%s %s %s %s",
                MARK_DIPLO, action.get(), result.outcome().accepted() ? "OK" : "REFUSED",
                result.outcome().reason())), true);
        return result.outcome().accepted() ? 1 : 0;
    }

    /**
     * `/statecraft contact [radius]`：按**当前位置**算一次接触（§11.1）。
     *
     * <p>正常的接触是 mod 层在玩家走动时自动算的（还没有那个 tick 钩子），这条命令是
     * **没有客户端时唯一的观察口**：给一个坐标就等价于"玩家站到了那儿"。
     * 半径省略时用 {@link IntelContact#DEFAULT_CONTACT_RADIUS}。
     */
    private static int contact(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_CONTACT + " EMPTY"), false);
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        double radius = IntelContact.DEFAULT_CONTACT_RADIUS;
        IntelContact.Contact c = IntelContact.byPosition(state, pos.getX(), pos.getZ(), radius);
        data.set(c.state());
        source.sendSuccess(() -> Component.literal(String.format(
                "%s pos=%d,%d radius=%.0f newly=%s met=%d/%d",
                MARK_CONTACT, pos.getX(), pos.getZ(), radius, c.newly(),
                IntelContact.metCount(c.state()), state.nations().size())), true);
        return c.newly().size();
    }

    /**
     * 情报站建成 → 天下各国的名号都进册子（§11.1 的"完整列表"）。
     *
     * <p>由 {@code advance}（以及将来的周期结算）顺手调用：判据是"**有一座绑好的、
     * 带 {@code diplomacy} 能力的建筑**"——也就是情报站本身，而不是某个时代解锁项。
     * 这条让玩家**不必跑遍地图**也能开始外交（设计上要的节奏：先立情报站，再谈天下）。
     *
     * @return 处理后的状态（没有情报站、或没有新接触时原样返回）
     */
    private static WorldState revealByStation(WorldState state) {
        boolean hasStation = false;
        for (Building b : state.buildings()) {
            if (b.stalled()) {
                continue;
            }
            Optional<BuildingDef> def = StatecraftData.buildings().byId(b.type());
            if (def.isPresent() && def.get().hasAbilityAt(b.level(), BuildingAbility.DIPLOMACY)) {
                hasStation = true;
                break;
            }
        }
        if (!hasStation) {
            return state;
        }
        IntelContact.Contact c = IntelContact.revealAll(state);
        if (c.changed()) {
            LOG.info("Statecraft：情报站已建成，天下各国的名号都进了册子（新接触 {} 个）",
                    c.newly().size());
        }
        return c.state();
    }

    private static Optional<DiplomacyAction> parseAction(String raw) {
        return switch (raw.toLowerCase(java.util.Locale.ROOT)) {
            case "declare_war", "war" -> Optional.of(DiplomacyAction.DECLARE_WAR);
            case "peace", "sue_for_peace" -> Optional.of(DiplomacyAction.SUE_FOR_PEACE);
            case "alliance" -> Optional.of(DiplomacyAction.ALLIANCE);
            case "trade" -> Optional.of(DiplomacyAction.TRADE);
            case "aid", "call_for_aid" -> Optional.of(DiplomacyAction.CALL_FOR_AID);
            case "tribute" -> Optional.of(DiplomacyAction.TRIBUTE);
            default -> Optional.empty();
        };
    }

    // ---- 阶段 3：建筑链路 ----

    private static final BuildingService BUILDINGS =
            new BuildingService(ClaimProbe.unclaimedIsInside());

    /**
     * `/statecraft scan <pos>`：把那个位置的环境**读成一个快照**，再让每座建筑的档位判一遍。
     *
     * <p>这是 §10.5 的"软性判定"在游戏里唯一的观察口：一条命令把四个读数和
     * "够不够用 / 还缺什么"全打出来，不用去猜为什么村民不出现。
     *
     * <p>这里刻意用 {@code ClaimProbe.fixed(true)} 而不是真的探针：这条命令回答的是
     * "环境本身够不够"，领地那一条已经由"能不能动工"决定，混在一起会让读数看不清。
     */
    private static int scan(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        EnvironmentSnapshot snapshot = EnvScanner.scan(level, pos, ClaimProbe.fixed(true));

        source.sendSuccess(() -> Component.literal(String.format(
                "%s pos=%d,%d,%d roof=%s enclosure=%.1f volume=%d claim=%s",
                MARK_SCAN, pos.getX(), pos.getY(), pos.getZ(), snapshot.hasRoof(),
                snapshot.enclosurePercent(), snapshot.volume(), snapshot.insideClaim())), false);

        for (BuildingDef def : StatecraftData.buildings().all()) {
            EnvironmentVerdict verdict = def.evaluate(snapshot);
            String line = String.format("  %s %s usable=%s next=%s unmet=%s", MARK_SCAN, def.id(),
                    verdict.usableTierId().orElse("-"), verdict.nextTierId().orElse("-"),
                    verdict.unmet());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    /**
     * `/statecraft anchor <pos>`：那个位置如果正好是某座建筑的锚点方块，就**登记**一座建筑。
     *
     * <p>登记 ≠ 世界里存在（§五 的原话）：这一步只写 {@code buildings[]}，
     * 村民由 {@code /statecraft materialize} 按幂等规则生成。
     */
    private static int anchor(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_ANCHOR + " EMPTY（还没建库）"), false);
            return 0;
        }
        Optional<BuildingDef> def = BUILDINGS.anchorAt(level, pos);
        if (def.isEmpty()) {
            String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                    .toString();
            source.sendSuccess(() -> Component.literal(
                    MARK_ANCHOR + " NOT_ANCHOR " + id + "（不是任何建筑的锚点方块）"), false);
            return 0;
        }
        // §10.1 的门禁：**没买图纸授权就别想动工**。
        // 这条检查必须在这里（而不是材料化时）：玩家拿加农炮打出壳、锚点就位才轮到我们看见，
        // 那时候再拒就等于"砖白放了"。所以登记的入口就是门禁。
        BlueprintVerdict allowed = BlueprintRules.canBuild(def.get(),
                data.ledger().ownsBlueprint(def.get().requiredBlueprint()));
        if (!allowed.allowed()) {
            source.sendSuccess(() -> Component.literal(
                    MARK_ANCHOR + " NO_BLUEPRINT " + allowed.reason()), false);
            return 0;
        }
        Optional<Building> created = BUILDINGS.register(state, def.get(), level, pos);
        if (created.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    MARK_ANCHOR + " ALREADY " + BuildingService.idFor(def.get(), level, pos)),
                    false);
            return 1;
        }
        data.set(state.withBuildings(concat(state.buildings(), created.get())));
        EnvironmentVerdict verdict = BUILDINGS.verdict(level, def.get(), pos);
        String line = String.format("%s REGISTERED %s type=%s usable=%s unmet=%s", MARK_ANCHOR,
                created.get().id(), def.get().id(), verdict.usableTierId().orElse("-"),
                verdict.unmet());
        source.sendSuccess(() -> Component.literal(line), true);
        return 1;
    }

    /** `/statecraft buildings`：登记表 + 每座建筑现在到底怎么样（§10.3 的三件校验）。 */
    private static int buildings(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        WorldState state = data(source).state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_BUILDINGS + " EMPTY"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                MARK_BUILDINGS + " count=" + state.buildings().size()), false);
        for (Building b : state.buildings()) {
            Optional<BuildingDef> def = StatecraftData.buildings().byId(b.type());
            BlockPos anchor = BuildingService.anchorPos(b);
            if (anchor == null || !level.isLoaded(anchor)) {
                // 区块没加载时**不许报"村民不在了"**：那会把"读不到"说成"人没了"，
                // 而这两件事在玩家眼里完全不同（前者等一会儿就好，后者要重雇）。
                source.sendSuccess(() -> Component.literal(
                        "  " + MARK_BUILDINGS + " " + b.id()
                                + " loaded=false（区块没加载，现状读不到） at="
                                + b.anchor().describe()), false);
                continue;
            }
            String detail = def.map(d -> {
                StaffObservation obs = BUILDINGS.observe(level, d, b);
                List<String> why = StaffBinding.describeMismatch(d, obs);
                int duplicates = BUILDINGS.findAllStaff(level, b).size();
                String staff = BUILDINGS.describeStaff(level, b).orElse("-");
                return "loaded=true bound=" + !b.stalled()
                        + " healthy=" + obs.bindingHealthy()
                        + " level=" + b.level() + " staff=" + staff
                        + " duplicates=" + Math.max(0, duplicates - 1)
                        + " at=" + b.anchor().describe()
                        + (why.isEmpty() ? "" : " why=" + String.join("；", why));
            }).orElse("（buildings.json 里没有这个类型）");
            source.sendSuccess(() -> Component.literal(
                    "  " + MARK_BUILDINGS + " " + b.id() + " " + detail), false);
        }
        return state.buildings().size();
    }

    /** `/statecraft materialize`：按 §10.3 的幂等规则跑一次实体化（该不该刷、冷却到了没有）。 */
    private static int materialize(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_MATERIALIZE + " EMPTY"), false);
            return 0;
        }
        BuildingService.SettleResult result = BUILDINGS.settle(
                level, state, state.seq(), state.elapsedOnlineHours());
        data.set(result.state());
        for (BuildingService.Step step : result.steps()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "  %s %s %s %s", MARK_MATERIALIZE, step.buildingId(), step.decision(),
                    step.detail())), false);
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "%s DONE registered=%d bound=%d", MARK_MATERIALIZE,
                result.state().buildings().size(), result.boundCount())), true);
        return (int) result.boundCount();
    }

    /**
     * `/statecraft advance <hours>`：推进一次**周期小结算**（§七）。
     *
     * <p>"累计在线小时"是这个系统的时间口径，而它只有在有人真的在线时才走；
     * 这条命令就是把"这段时间"手动喂给引擎——自动化脚本也正是这样在几分钟内跑完 300 小时。
     */
    private static int advance(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double hours = DoubleArgumentType.getDouble(ctx, "hours");
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_ADVANCE + " EMPTY"), false);
            return 0;
        }
        SettlementEngine engine = new SettlementEngine(
                SettlementConfig.defaults(), StatecraftData.config(), StatecraftData.eras(),
                letterMachine(), StatecraftData.letterDefaults());
        SettlementInput input = new SettlementInput(SettlementTrigger.PERIODIC, hours);
        // §十三：手工推进也要记账（和调度器同一条纪律：先记账再动手）
        data.setLog(data.log().append(state, input));
        WorldState after = engine.settle(state, input);
        after = revealByStation(after);
        data.set(after);
        long open = LetterMachine.openLetters(after).size();
        String line = String.format("%s hours=%.2f era=%s seq=%d events=%d letters=%d open=%d",
                MARK_ADVANCE, after.elapsedOnlineHours(), after.eraId(), after.seq(),
                after.events().size(), after.letters().size(), open);
        source.sendSuccess(() -> Component.literal(line), true);
        return after.events().size();
    }

    /**
     * 国书状态机（§9.4）：数值来自 `letters.json`，承诺长度借用 §11.2 朝贡那一个数。
     *
     * <p>每次新建（而不是缓存一个静态的）：`StatecraftData` 是 mod 构造时载入的，
     * 而命令可能在数据**重载**之后才跑（§十六 的数据包热重载）。新建成本可以忽略。
     */
    private static LetterMachine letterMachine() {
        return new LetterMachine(StatecraftData.letters().config(), DiplomacyConfig.defaults());
    }

    /**
     * `/statecraft letters`：列出所有国书（待回的排前面）。
     *
     * <p>§9.4 说 v1 的形态是"情报册/情报站里的可回复条目"—— 这条命令就是那个列表在
     * **没有客户端时**的观察口：国书到底有没有被递出来、递的是哪一种、期限还剩几拍。
     */
    private static int letters(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        WorldState state = data(source).state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_LETTERS + " EMPTY"), false);
            return 0;
        }
        List<Letter> open = LetterMachine.openLetters(state);
        source.sendSuccess(() -> Component.literal(String.format("%s total=%d open=%d seq=%d",
                MARK_LETTERS, state.letters().size(), open.size(), state.seq())), false);
        // 待回的排前面：情报册里最该被看见的就是它们
        List<Letter> ordered = new ArrayList<>(open);
        for (Letter l : state.letters()) {
            if (l.state() != LetterState.OPEN) {
                ordered.add(l);
            }
        }
        for (Letter l : ordered) {
            String from = state.nation(l.fromNationId()).map(Nation::name).orElse(l.fromNationId());
            String detail = switch (l.kind()) {
                case STOP_BUILDING -> "半径 " + trim(l.stopBuildRadius());
                case TRIBUTE -> "要 " + trim(l.amount());
                case JOINT_WAR -> "目标 "
                        + state.nation(l.targetNationId()).map(Nation::name)
                                .orElse(l.targetNationId());
            };
            source.sendSuccess(() -> Component.literal(String.format(
                    "  %s %s %s from=%s %s state=%s deadline=%d iss=%d",
                    MARK_LETTERS, l.id(), l.kind(), from, detail, l.state(),
                    l.deadlineSeq(), l.issuedAtSeq())), false);
        }
        return ordered.size();
    }

    /**
     * `/statecraft answer <id> accept|refuse`：回复一封国书（§9.4 的"接受 / 拒绝"）。
     *
     * <p>核心判定全在 core 的 {@link LetterMachine}：这条命令只负责把结果写回存档、
     * 并把 core 给的中文说明转给玩家（包括"要掏多少资源"那个数）。
     */
    private static int answer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_ANSWER + " EMPTY"), false);
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id");
        boolean accept = StringArgumentType.getString(ctx, "decision").equalsIgnoreCase("accept");
        LetterResult result = letterMachine().answer(state, id, accept);
        if (result.accepted()) {
            data.set(result.state());
        }
        source.sendSuccess(() -> Component.literal(String.format("%s %s %s",
                MARK_ANSWER, result.accepted() ? "OK" : "REFUSED", result.message())), true);
        if (result.playerTreasuryDelta() != 0.0) {
            // 玩家侧账本属于计划 5（core 只报数）—— 这里把该记的数原样打出来
            source.sendSuccess(() -> Component.literal(String.format(
                    "  %s playerTreasuryDelta=%.1f（玩家侧账本还没落盘，见 DEFERRED §I2）",
                    MARK_ANSWER, result.playerTreasuryDelta())), false);
        }
        return result.accepted() ? 1 : 0;
    }

    private static String trim(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15
                ? String.valueOf((long) v)
                : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static List<Building> concat(List<Building> existing, Building extra) {
        List<Building> out = new ArrayList<>(existing);
        out.add(extra);
        return out;
    }

    /**
     * `/statecraft pending <hours>`：往"待结算的在线小时"里注水（**开发用**）。
     *
     * <p>为什么需要它：§七 的节拍是"每 2 小时累计在线一次小结算"，而验收那条
     * "**有**人在线时调度器真的会推进"不能真等两小时。注水之后调度器**下一拍**就会
     * 看到 `pending >= periodicHours`，于是走的是**和真实在线完全相同的代码路径**
     * （只是时间不是自然流逝来的）。
     */
    private static int pending(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        double hours = DoubleArgumentType.getDouble(ctx, "hours");
        SCHEDULER.injectPendingHours(hours);
        source.sendSuccess(() -> Component.literal(String.format(
                "%s +%.2f（待结算现在 %.2f 小时；下一拍就会按 §七 结算）",
                MARK_PENDING, hours, SCHEDULER.pendingHours())), true);
        return 1;
    }

    /**
     * `/statecraft accelerate`：**提前推进时代**（§七 的"时间到或主动加速，取先到者"）。
     *
     * <p>这是任务书里那把「时代钥匙」的落点：主线做完 → FTB Quests 发一个 {@code command}
     * 奖励调它 → 时代提前进入下一个。
     *
     * <h2>它**不**另造一条推进路径</h2>
     *
     * <p>做法是"把累计在线小时推到当前时代的结束点"，然后由本来就存在的
     * {@link SettlementScheduler} 下一拍判定 {@code ERA_ADVANCE}。于是：
     *
     * <ul>
     *   <li><b>时间到自动推进那条老路径一个字没改</b> —— 它和加速走的是同一段代码；</li>
     *   <li>时代推进该做的事（时代级结算、事件、国书、"天下大势"播报）**一件都不会少**；</li>
     *   <li>"同 seed 同输入必得同结果"仍然成立：加速只改 {@code elapsedOnlineHours}，不碰随机性。</li>
     * </ul>
     *
     * <p>已经是最后一个时代时**拒绝**（没有下一个时代可推），而不是悄悄什么都不做。
     */
    private static int accelerate(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data =
                StatecraftSavedData.get(source.getServer().overworld());
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(
                    MARK_ACCELERATE + " EMPTY（还没建库，用 /statecraft regen）"), false);
            return 0;
        }
        var eras = StatecraftData.eras();
        Era current = eras.byId(state.eraId())
                .orElseGet(() -> eras.byOrdinal(state.eraOrdinal()));
        java.util.OptionalDouble need = SettlementClock.hoursToBoundary(
                state.elapsedOnlineHours(), eras.boundaryAfter(current));
        if (need.isEmpty()) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "%s REFUSED LAST_ERA（%s 已经是最后一个时代，没有下一个可推进）",
                    MARK_ACCELERATE, current.id())), false);
            return 0;
        }
        // need 为 0 时（恰好站在边界上）不能什么也不做：plan 要求 pending > 0 才会推进。
        double inject = Math.max(need.getAsDouble(), 1e-9);
        SCHEDULER.injectForcedHours(inject);
        source.sendSuccess(() -> Component.literal(String.format(
                "%s OK era=%s 推进 %.3f 小时至边界（累计 %.3f；下一拍结算并按 §七 推进时代；"
                        + "此路径不看是否有人在线的自然累积，因为钥匙是刻意操作）",
                MARK_ACCELERATE, current.id(), inject,
                state.elapsedOnlineHours() + SCHEDULER.pendingHours())), true);
        return 1;
    }

    /**
     * 结算调度（§七）：按真实时间攒"在线小时"，攒够 2 小时就跑一次周期小结算，
     * 跨过时代边界就跑时代推进。**没有玩家在线时一点都不攒**。
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        try {
            SCHEDULER.tick(event.getServer(),
                    StatecraftSavedData.get(event.getServer().overworld()));
        } catch (RuntimeException ex) {
            // 调度器出问题**不能带崩服务器**：记一条、跳过这一拍，下一拍继续
            LOG.error("Statecraft：结算调度出错（已跳过这一拍）：{}", ex.toString());
        }
    }

    /**
     * `/statecraft intel [track|deep|history] [nationId]`：情报册的三种读法（§11.1 的三档）。
     *
     * <p>档位按 `eras.json` 的 unlocks 判定；**全部判定都在 core 的 {@code IntelBook} 里**，
     * 这里只负责"把账本里的事实递进去、把结果打出来"。没有客户端时这就是情报册。
     */
    private static int intel(CommandContext<CommandSourceStack> ctx, String mode, String nationId) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_INTEL + " EMPTY"), false);
            return 0;
        }
        PlayerLedger ledger = data.ledger();
        IntelTier tier = currentTier(state);
        boolean declaration = StatecraftData.eras().isUnlocked(
                IntelTier.UNLOCK_DECLARATION, StatecraftData.eras()
                        .fallbackForUnknownId(state.eraId(), state.eraOrdinal()));

        switch (mode) {
            case "track" -> {
                IntelVerdict v = IntelBook.track(tier, ledger.trackedNations(), nationId);
                if (v.allowed()) {
                    data.setLedger(ledger.withTrackedAdded(nationId));
                }
                source.sendSuccess(() -> Component.literal(
                        MARK_INTEL + " track " + (v.allowed() ? "OK " : "REFUSED ") + v.reason()),
                        false);
            }
            case "untrack" -> {
                data.setLedger(ledger.withTrackedRemoved(nationId));
                source.sendSuccess(() -> Component.literal(
                        MARK_INTEL + " untrack OK 不再追踪 " + nationId), false);
            }
            case "deep" -> {
                // 每结算限 1 国（§11.1 第三档）。计数不另存：**看这一拍的结算序号**
                // （上一次深挖存的就是 seq，seq 一变就等于额度重置）
                int used = ledger.lastDeepDiveSeq() == state.seq() ? 1 : 0;
                IntelVerdict v = IntelBook.deepDive(tier, used);
                if (!v.allowed()) {
                    source.sendSuccess(() -> Component.literal(
                            MARK_INTEL + " deep REFUSED " + v.reason()), false);
                    return 0;
                }
                data.setLedger(ledger.withDeepDive(state.seq()));
                for (Event e : IntelBook.history(state, nationId, 12)) {
                    String line = "  " + MARK_INTEL + " history seq=" + e.seq() + " "
                            + e.textKey() + " " + e.params();
                    source.sendSuccess(() -> Component.literal(line), false);
                }
                source.sendSuccess(() -> Component.literal(
                        MARK_INTEL + " deep OK " + nationId + "（每结算 1 国）"), false);
            }
            default -> {
                List<NationBrief> listing = IntelBook.listing(
                        state, tier, declaration, ledger.trackedNations());
                source.sendSuccess(() -> Component.literal(String.format(
                        "%s tier=%s met=%d/%d tracked=%s declaration=%s",
                        MARK_INTEL, tier, listing.size(), state.nations().size(),
                        ledger.trackedNations(), declaration)), false);
                for (NationBrief b : listing) {
                    String line = "  " + MARK_INTEL + " " + b.id() + " " + b.name()
                            + " size=" + b.size() + " att=" + Math.round(b.attitude())
                            + " dev=" + b.development().map(d -> String.valueOf(Math.round(d)))
                                    .orElse("-")
                            + " mil=" + b.military().map(d -> String.valueOf(Math.round(d)))
                                    .orElse("-")
                            + " war=" + b.atWarWith() + " ally=" + b.alliedWith()
                            + (b.tracked() ? " TRACKED" : "");
                    source.sendSuccess(() -> Component.literal(line), false);
                }
            }
        }
        return 1;
    }

    /**
     * 当前的情报档位（§11.1）。
     *
     * <p>**两个来源各管一段**（2026-09-20 更正，见 `IntelTier.tierFor` 的注释）：
     * 第一档来自**时代解锁**（情报册），第二/三档来自**建筑**（绑好的情报站 / 二级情报站）。
     * 判"有没有站"要求**村民绑着**：空壳站不是情报能力（§10.4 停摆）。
     */
    private static IntelTier currentTier(WorldState state) {
        Era current = StatecraftData.eras()
                .fallbackForUnknownId(state.eraId(), state.eraOrdinal());
        IntelTier fromEra = IntelTier.tierFor(StatecraftData.eras(), current);
        boolean hasStation = false;
        boolean upgraded = false;
        for (Building b : state.buildings()) {
            if (b.stalled()) {
                continue;
            }
            Optional<BuildingDef> def = StatecraftData.buildings().byId(b.type());
            if (def.isEmpty()
                    || !def.get().hasAbilityAt(b.level(), BuildingAbility.DIPLOMACY)) {
                continue;
            }
            hasStation = true;
            if (def.get().hasAbilityAt(b.level(), BuildingAbility.TRACK_NATIONS)) {
                upgraded = true;
            }
        }
        boolean upgradeUnlocked = StatecraftData.eras()
                .isUnlocked(IntelTier.UNLOCK_UPGRADE, current);
        return fromEra.promotedBy(hasStation, upgraded, upgradeUnlocked);
    }

    /**
     * `/statecraft blueprint [buy <buildingId>]`：建筑工的货架 / 买一份图纸授权（§10.1）。
     *
     * <p>价钱与"要不要授权"全在 `buildings.json` 里；能不能买得起由 core 的
     * {@code BlueprintRules.buy} 判，账本（已购 + 国库）在 mod 层。
     */
    private static int blueprint(CommandContext<CommandSourceStack> ctx, String buildingId) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        PlayerLedger ledger = data.ledger();
        BuildingCatalog catalog = StatecraftData.buildings();

        if (buildingId == null || buildingId.isBlank()) {
            List<BuildingDef> shelf = BlueprintRules.shelf(catalog, ledger.grantedBlueprints());
            source.sendSuccess(() -> Component.literal(String.format(
                    "%s shelf=%d %s", MARK_BLUEPRINT, shelf.size(), ledger.describe())), false);
            for (BuildingDef def : shelf) {
                String line = "  " + MARK_BLUEPRINT + " " + def.id() + " " + def.name()
                        + " price=" + Math.round(def.blueprintPrice())
                        + " blueprint=" + def.requiredBlueprint();
                source.sendSuccess(() -> Component.literal(line), false);
            }
            return shelf.size();
        }

        Optional<BuildingDef> def = catalog.byId(buildingId);
        if (def.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    MARK_BLUEPRINT + " UNKNOWN " + buildingId), false);
            return 0;
        }
        BlueprintVerdict verdict = BlueprintRules.buy(def.get(),
                ledger.ownsBlueprint(def.get().requiredBlueprint()), ledger.treasury());
        if (verdict.allowed()) {
            data.setLedger(ledger.withPurchase(def.get(), def.get().blueprintPrice()));
        }
        source.sendSuccess(() -> Component.literal(String.format("%s buy %s %s",
                MARK_BLUEPRINT, verdict.allowed() ? "OK" : "REFUSED", verdict.reason())), true);
        return verdict.allowed() ? 1 : 0;
    }

    /**
     * `/statecraft log [n]`：看结算事务日志的最后 n 步（§十三）。
     *
     * <p>为什么值得有一条命令：这份日志是**"从上一个完整结算重放"的唯一凭据**，
     * 而它平时完全不可见 —— 出问题时才有用。所以把它打出来（结算序号 / 触发 / 在线小时 /
     * **动手之前的状态哈希**），配合 core 的确定性用例就能核对"这段历史是不是重放得出来"。
     */
    private static int log(CommandContext<CommandSourceStack> ctx, int limit) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        SettlementLog log = data.log();
        source.sendSuccess(() -> Component.literal(String.format(
                "%s entries=%d stateSeq=%d lastEntrySeq=%s",
                MARK_LOG, log.size(), data.state() == null ? -1 : data.state().seq(),
                log.isEmpty() ? "-" : String.valueOf(log.lastEntry().seq()))), false);
        List<SettlementLogEntry> entries = log.entries();
        int from = Math.max(0, entries.size() - limit);
        for (int i = from; i < entries.size(); i++) {
            SettlementLogEntry e = entries.get(i);
            source.sendSuccess(() -> Component.literal(String.format(
                    "  %s seq=%d trigger=%s hours=%.2f inputHash=%d",
                    MARK_LOG, e.seq(), e.trigger(), e.hoursDelta(), e.inputHash())), false);
        }
        return log.size();
    }

    /** `/statecraft treasury <amount>`：设置玩家国库（计划 5 的账本还没接，先用命令喂）。 */
    private static int treasury(CommandContext<CommandSourceStack> ctx, double amount) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        data.setLedger(data.ledger().withTreasury(amount));
        source.sendSuccess(() -> Component.literal(
                MARK_BLUEPRINT + " treasury=" + Math.round(amount)), true);
        return 1;
    }

    /**
     * `/statecraft blueprint reset`：清空玩家侧账本（**开发用**）。
     *
     * <p>为什么要有它：门禁（"没买授权就不许登记"）只在**没买过**的时候才看得见，
     * 而账本是落盘的 —— 验收一次之后就再也观察不到那条分支了。
     * 没有这条命令，"门禁真的在"就只能靠读代码相信。
     */
    private static int blueprintReset(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        data.setLedger(PlayerLedger.empty());
        source.sendSuccess(() -> Component.literal(
                MARK_BLUEPRINT + " reset OK " + data.ledger().describe()), true);
        return 1;
    }

    /**
     * `/statecraft upgrade <pos>`：对锚点用升级件（§11.1 第三档的入口）。
     *
     * <p>为什么必须有这条入口：§11.1 的第三档（追踪 / 看战争与同盟 / 每结算深挖 1 国）
     * 全挂在"建筑等级 ≥ 2"上，而**没有任何地方能把等级升上去** —— 那三项就是死代码。
     * 真正的形态应该是"手上有个升级件、对着锚点用"（§11.1 的原话），
     * 升级件本身是内容（等图纸那一套做完再加），这里先把**规则与状态**接通。
     */
    private static int upgrade(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_UPGRADE + " EMPTY"), false);
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        Optional<Building> found = BuildingService.findAt(state, level, pos);
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                    MARK_UPGRADE + " NOT_FOUND 这里没有登记过的建筑（先 /statecraft anchor）"),
                    false);
            return 0;
        }
        BuildingService.UpgradeResult result = BUILDINGS.upgrade(level, state, found.get());
        if (!result.ok()) {
            source.sendSuccess(() -> Component.literal(
                    MARK_UPGRADE + " REFUSED " + result.message()), false);
            return 0;
        }
        data.set(state.withBuilding(result.upgraded()));
        source.sendSuccess(() -> Component.literal(String.format("%s OK %s %s",
                MARK_UPGRADE, result.upgraded().id(), result.message())), true);
        return 1;
    }

    /**
     * §十二 的"玩家行为 → 态度"：**玩家在谁家都城 64 格内放了方块**。
     *
     * <p>在这之前**没有任何东西会拉低国家对玩家的态度** —— 生成时是 0，每拍的态度回归又把它
     * 拉回 0，于是 §9.1 的宣战门槛（≤ −40）永远踩不到、§9.4 的国书也只剩"手动宣战再求和"。
     *
     * <p>这一层只报告**事实**（谁在哪儿放了方块）；"落在谁家 64 格内""这个周期还能扣多少"
     * 全在 core 的 {@link PlayerActions} 里判，所以那条周期上限能被 JUnit 钉死。
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            StatecraftSavedData data = StatecraftSavedData.get(level.getServer().overworld());
            WorldState state = data.state();
            if (state == null) {
                return;
            }
            BlockPos pos = event.getPos();
            // 先做一次便宜的问句：这一带有没有谁家的都城？没有就直接走（每个方块都要过这一关）
            if (PlayerActions.capitalsNear(state, pos.getX(), pos.getZ()).isEmpty()) {
                return;
            }
            PlayerActions.Reaction reaction = PlayerActions.buildingNear(
                    state, pos.getX(), pos.getZ(), state.seq());
            if (!reaction.changed()) {
                return;
            }
            data.set(reaction.state());
            for (String nationId : reaction.affected()) {
                reaction.state().nation(nationId).ifPresent(n -> player.displayClientMessage(
                        Component.literal("你在「" + n.name() + "」都城边上动了土，"
                                + "它对你们的态度降到 " + Math.round(n.attitudeToParty())),
                        true));
            }
        } catch (RuntimeException ex) {
            LOG.error("Statecraft：处理放置方块的态度变化出错（已忽略这一块）：{}", ex.toString());
        }
    }

    /**
     * `/statecraft build <pos>`：报告一次"玩家在 pos 放置了方块"（§十二）。
     *
     * <p>为什么要有这条命令：正常的触发是玩家真的放方块，而这台验证机**没有客户端**。
     * 这条命令走的是**和那个事件钩子完全相同的代码路径**（同一段 core 规则），
     * 所以它能验证"在都城边上动土会掉态度、而且有周期上限"，区别只是"谁报告的"。
     */
    private static int build(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            source.sendSuccess(() -> Component.literal(MARK_BUILD + " EMPTY"), false);
            return 0;
        }
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        PlayerActions.Reaction r = PlayerActions.buildingNear(
                state, pos.getX(), pos.getZ(), state.seq());
        data.set(r.state());
        String detail = r.changed()
                ? "affected=" + r.affected()
                : "（这一带没有谁家的都城，或这个周期的额度已经扣满）";
        // 诊断读数：把"这一带谁家的都城在范围内""这个周期已经扣了多少"一并打出来 ——
        // 少了它们就只能靠猜（2026-09-20 排查时正是这么被卡住的）
        String diag = String.format("near=%s already=%.1f cap=%.1f",
                PlayerActions.capitalsNear(state, pos.getX(), pos.getZ()),
                PlayerActions.penaltySoFar(state, "n1", state.seq()),
                PlayerActions.BUILD_ATTITUDE_CAP);
        source.sendSuccess(() -> Component.literal(String.format(
                "%s pos=%d,%d seq=%d %s %s", MARK_BUILD, pos.getX(), pos.getZ(), state.seq(),
                detail, diag)), true);
        return r.affected().size();
    }

    private static StatecraftSavedData data(CommandSourceStack source) {
        return StatecraftSavedData.get(source.getServer().overworld());
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        WorldState state = data.state();
        if (state == null) {
            String message = MARK_INFO + " EMPTY（还没建库，用 /statecraft regen）";
            source.sendSuccess(() -> Component.literal(message), false);
            return 0;
        }
        // 拆成两行：RCON 的一行太长会被折行，折了之后正则就跨不过去了
        // （2026-09-20 实测：`buildings=1\n letters=14` 把脚本断言拆散，误报成功能失败）
        String head = String.format("%s seed=%d era=%s ordinal=%d seq=%d hours=%.2f",
                MARK_INFO, state.seed(), state.eraId(), state.eraOrdinal(), state.seq(),
                state.elapsedOnlineHours());
        String counts = String.format("  %s nations=%d buildings=%d letters=%d met=%d",
                MARK_INFO, state.nations().size(), state.buildings().size(),
                state.letters().size(), IntelContact.metCount(state));
        source.sendSuccess(() -> Component.literal(head), false);
        source.sendSuccess(() -> Component.literal(counts), false);
        for (Nation n : state.nations()) {
            String line = String.format(
                    "  %s %s %s %s size=%d dev=%.1f stance=%.1f mil=%.1f att=%.1f party=%s "
                            + "x=%.0f z=%.0f",
                    n.id(), n.name(), n.cultureId(), n.status(), n.size(), n.development(),
                    n.stance(), n.military(), n.attitudeToParty(), n.partyStatus(), n.x(), n.z());
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return state.nations().size();
    }

    /** M2 的验收动作本身就是一条命令：写进 NBT 再读回来，逐字段相等才算过。 */
    private static int roundTrip(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        StatecraftSavedData data = data(source);
        if (data.state() == null) {
            source.sendSuccess(() -> Component.literal(
                    MARK_ROUNDTRIP + " SKIP（还没建库）"), false);
            return 0;
        }
        StatecraftSavedData.RoundTrip result = data.roundTrip();
        for (String warning : result.warnings()) {
            source.sendSuccess(() -> Component.literal("  读档警告：" + warning), false);
        }
        String message = MARK_ROUNDTRIP + (result.identical() ? "=OK" : "=FAIL");
        source.sendSuccess(() -> Component.literal(message), false);
        return result.identical() ? 1 : 0;
    }

    private static int regen(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel overworld = source.getServer().overworld();
        WorldState generated = generate(overworld);
        StatecraftSavedData.get(overworld).set(generated);
        String message = String.format("%s 重建完成：seed=%d 国家=%d 个",
                MARK_INFO, generated.seed(), generated.nations().size());
        source.sendSuccess(() -> Component.literal(message), true);
        return generated.nations().size();
    }
}
