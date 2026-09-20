package com.wildernessnation.statecraft.world;

import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.letter.LetterMachine;
import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.settle.SettlementClock;
import com.wildernessnation.statecraft.core.settle.SettlementConfig;
import com.wildernessnation.statecraft.core.settle.SettlementEngine;
import com.wildernessnation.statecraft.core.settle.SettlementInput;
import com.wildernessnation.statecraft.core.settle.SettlementTrigger;
import com.wildernessnation.statecraft.core.text.Narrator;
import com.wildernessnation.statecraft.data.StatecraftData;
import com.wildernessnation.statecraft.data.StatecraftSavedData;
import java.util.OptionalDouble;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * 结算调度：把 §七 的节拍接到真实时间上。
 *
 * <p>在这之前，整个推进只能靠手敲 {@code /statecraft advance} —— 也就是说**真玩游戏时
 * 世界是冻住的**（时代不会走、国家不会动、国书不会来）。
 *
 * <h2>这一层只做三件事</h2>
 *
 * <ol>
 *   <li><b>按真实时间攒"在线小时"</b>：每 tick 用 {@code System.nanoTime()} 的差值算，
 *       而不是数 tick —— 服务器卡顿时 tick 会掉，数 tick 会让时钟跟着变慢。</li>
 *   <li><b>问 core 该不该结算</b>（{@link SettlementClock}）：不在线不推进、跨时代优先，
 *       两条纪律都在那边，这里不重复判断。</li>
 *   <li><b>真的跑 + 写档 + 报出来</b>。</li>
 * </ol>
 *
 * <h2>攒到一半的小时数存在哪</h2>
 *
 * <p>存在 {@link StatecraftSavedData} 的一个 mod 层 NBT 键里（`pendingHours`）——
 * **不进 core 的 schema**：它不是世界状态的一部分，而是"调度器自己记到哪儿了"。
 * 不存的话每次重启都要丢掉最多 2 小时的进度。
 */
public final class SettlementScheduler {

    private static final Logger LOG = LogUtils.getLogger();

    /** 自检用的稳定标记（自动化脚本按它断言）。 */
    public static final String MARK_SETTLE = "STATECRAFT_SETTLE";

    /** 多久问一次"该结算了吗"（秒）。1 秒足够精确，又不会每 tick 都算一遍。 */
    private static final double POLL_SECONDS = 1.0;

    private final SettlementConfig cfg = SettlementConfig.defaults();

    private long lastPollNanos = 0L;
    private double pendingHours = 0.0;

    /** 从存档里恢复调度器记到哪儿了（服务器启动时调一次）。 */
    public void restore(double pendingHours) {
        this.pendingHours = Math.max(0.0, pendingHours);
        this.lastPollNanos = 0L;
    }

    public double pendingHours() {
        return pendingHours;
    }

    /**
     * 每 tick 调一次。
     *
     * @return 这次真的结算了没有（给测试/脚本用）
     */
    public boolean tick(MinecraftServer server, StatecraftSavedData data) {
        long now = System.nanoTime();
        if (lastPollNanos == 0L) {
            lastPollNanos = now;
            return false;
        }
        double seconds = (now - lastPollNanos) / 1_000_000_000.0;
        if (seconds < POLL_SECONDS) {
            return false;
        }
        lastPollNanos = now;

        WorldState state = data.state();
        if (state == null) {
            return false;
        }
        int players = server.getPlayerList().getPlayerCount();
        // **不在线一点都不攒**（§七）。注意这里不是"攒着但不算" —— 是根本不加。
        if (players > 0) {
            pendingHours += SettlementClock.secondsToHours(seconds);
            data.setPendingHours(pendingHours);
        }

        Era current = StatecraftData.eras().byId(state.eraId())
                .orElseGet(() -> StatecraftData.eras().byOrdinal(state.eraOrdinal()));
        OptionalDouble boundary = StatecraftData.eras().boundaryAfter(current);
        SettlementClock.Plan plan = SettlementClock.plan(
                state.elapsedOnlineHours(), pendingHours, players, cfg.periodicHours(),
                boundary.orElse(0.0));
        if (!plan.due()) {
            return false;
        }

        SettlementEngine engine = new SettlementEngine(cfg, StatecraftData.config(),
                StatecraftData.eras(), new LetterMachine(
                        StatecraftData.letters().config(),
                        com.wildernessnation.statecraft.core.diplomacy.DiplomacyConfig.defaults()),
                StatecraftData.letterDefaults());
        SettlementInput input = plan.kind() == SettlementClock.Kind.ERA_ADVANCE
                ? SettlementInput.eraAdvance(plan.hoursDelta())
                : new SettlementInput(SettlementTrigger.PERIODIC, plan.hoursDelta());
        // §十三：**先记账再动手**。日志里的哈希算的是"动手之前"的状态，
        // 写反了就会记住一个已经算过的状态、日志立刻报废。
        data.setLog(data.log().append(state, input));
        WorldState after = engine.settle(state, input);

        pendingHours = 0.0;
        data.setPendingHours(0.0);
        data.set(after);

        LOG.info("{} kind={} hours={} totalHours={} era={} seq={} events={} letters={}",
                MARK_SETTLE, plan.kind(), round(plan.hoursDelta()),
                round(after.elapsedOnlineHours()), after.eraId(), after.seq(),
                after.events().size(), after.letters().size());
        if (plan.kind() == SettlementClock.Kind.ERA_ADVANCE) {
            announceEra(server, after);
        }
        return true;
    }

    /**
     * 时代推进时把那一封「天下大势」**念给在线的人听**（§七）。
     *
     * <p>文本由 core 的 {@link Narrator} 从 `events.json` + `places.json` 渲染 ——
     * 规则层只给 `textKey + params`，中文句子是数据层的事（§五 那条纪律）。
     * 渲染不出来（模板缺了）就只记日志：**不能因为一句话发不出去就让结算失败**。
     */
    private static void announceEra(MinecraftServer server, WorldState after) {
        try {
            Narrator narrator = new Narrator(StatecraftData.templates(),
                    StatecraftData.places(), after.seed());
            for (int i = after.events().size() - 1; i >= 0; i--) {
                Event e = after.events().get(i);
                if (!"era_summary".equals(e.textKey())) {
                    continue;
                }
                String line = narrator.narrate(e);
                server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
                LOG.info("{} 天下大势：{}", MARK_SETTLE, line);
                return;
            }
        } catch (RuntimeException ex) {
            LOG.warn("Statecraft：时代推进的「天下大势」渲染失败（不影响结算）：{}", ex.toString());
        }
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /** 给"立刻结算一次"用（命令与脚本）：把待结算的小时数直接喂进来。 */
    public void injectPendingHours(double hours) {
        pendingHours += Math.max(0.0, hours);
    }

    /** 服务器停时把当前进度写回存档（免得丢最多 2 小时）。 */
    public void persist(StatecraftSavedData data) {
        data.setPendingHours(pendingHours);
    }

    /** 世界维度（只是为了让调用方不用自己拿服务器再取一次）。 */
    public static ServerLevel overworld(MinecraftServer server) {
        return server.overworld();
    }
}
