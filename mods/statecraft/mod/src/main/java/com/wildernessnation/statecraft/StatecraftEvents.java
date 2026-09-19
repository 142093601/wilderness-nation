package com.wildernessnation.statecraft;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.data.StatecraftData;
import com.wildernessnation.statecraft.data.StatecraftSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
                        .executes(StatecraftEvents::regen)));
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
