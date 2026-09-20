package com.wildernessnation.statecraft;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.building.BuildingAbility;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.building.StaffObservation;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyAction;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyConfig;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyMachine;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyRequest;
import com.wildernessnation.statecraft.core.diplomacy.DiplomacyResult;
import com.wildernessnation.statecraft.core.env.EnvironmentSnapshot;
import com.wildernessnation.statecraft.core.env.EnvironmentVerdict;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.intel.IntelContact;
import com.wildernessnation.statecraft.core.letter.Letter;
import com.wildernessnation.statecraft.core.letter.LetterMachine;
import com.wildernessnation.statecraft.core.letter.LetterResult;
import com.wildernessnation.statecraft.core.letter.LetterState;
import com.wildernessnation.statecraft.core.model.Building;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.settle.SettlementConfig;
import com.wildernessnation.statecraft.core.settle.SettlementEngine;
import com.wildernessnation.statecraft.core.settle.SettlementInput;
import com.wildernessnation.statecraft.core.settle.SettlementTrigger;
import com.wildernessnation.statecraft.core.staff.StaffBinding;
import com.wildernessnation.statecraft.data.StatecraftData;
import com.wildernessnation.statecraft.data.StatecraftSavedData;
import com.wildernessnation.statecraft.world.BuildingService;
import com.wildernessnation.statecraft.world.ClaimProbe;
import com.wildernessnation.statecraft.world.EnvScanner;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
                                .executes(StatecraftEvents::contact))));
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
        WorldState after = engine.settle(state,
                new SettlementInput(SettlementTrigger.PERIODIC, hours));
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
        String head = String.format("%s seed=%d era=%s ordinal=%d seq=%d nations=%d",
                MARK_INFO, state.seed(), state.eraId(), state.eraOrdinal(), state.seq(),
                state.nations().size());
        source.sendSuccess(() -> Component.literal(head), false);
        for (Nation n : state.nations()) {
            String line = String.format("  %s %s %s size=%d dev=%.1f stance=%.1f mil=%.1f x=%.0f z=%.0f",
                    n.id(), n.name(), n.cultureId(), n.size(), n.development(), n.stance(),
                    n.military(), n.x(), n.z());
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
