package com.wildernessnation.statecraft;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import com.wildernessnation.statecraft.core.building.StaffObservation;
import com.wildernessnation.statecraft.core.env.EnvironmentSnapshot;
import com.wildernessnation.statecraft.core.env.EnvironmentVerdict;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
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
                                .executes(StatecraftEvents::advance))));
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
                SettlementConfig.defaults(), StatecraftData.config(), StatecraftData.eras());
        WorldState after = engine.settle(state,
                new SettlementInput(SettlementTrigger.PERIODIC, hours));
        data.set(after);
        String line = String.format("%s hours=%.2f era=%s seq=%d events=%d letters=%d",
                MARK_ADVANCE, after.elapsedOnlineHours(), after.eraId(), after.seq(),
                after.events().size(), after.letters().size());
        source.sendSuccess(() -> Component.literal(line), true);
        return after.events().size();
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
