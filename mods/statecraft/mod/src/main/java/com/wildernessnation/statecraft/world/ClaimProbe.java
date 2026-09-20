package com.wildernessnation.statecraft.world;

import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * "这个位置在不在领地内"（`NATIONS.md` §10.5 的四个读数之一）。
 *
 * <p><b>为什么做成一个接口</b>：领地是整合包里另一个 mod 的事（OPAC），
 * 而 core 侧刻意不碰任何 mc/第三方类型。所以那条读数由 mod 层注入，
 * 规则层只管拿到一个布尔。
 *
 * <p><b>现在的实现是"永远算在领地内"</b>（{@link #unclaimedIsInside()}），
 * 而且**只警告一次**。理由是：不接的话 §10.5 的四个条件在这台机器上永远满足不了，
 * 整条建筑链路（图纸→锚点→村民）连试都试不了；而真正的接入要等
 * `DEFERRED.md` A4（图纸 × 领地，联机验证）那一次一起做——那时才知道该怎么问 OPAC。
 * 这不是"偷偷放过"，启动日志里会明说。
 */
@FunctionalInterface
public interface ClaimProbe {

    Logger LOG = LogUtils.getLogger();

    boolean insideClaim(ServerLevel level, BlockPos pos);

    /**
     * 开发期的回退：没有接入领地系统时**一律算在领地内**，并留下一条一次性的警告。
     *
     * <p>用 {@link AtomicBoolean} 而不是普通字段：探针会被区块事件在多个线程上问到，
     * 而"只警告一次"要是漏了，日志会被刷爆。
     */
    static ClaimProbe unclaimedIsInside() {
        AtomicBoolean warned = new AtomicBoolean(false);
        return (level, pos) -> {
            if (warned.compareAndSet(false, true)) {
                LOG.warn("Statecraft：还没接入领地系统（OPAC），"
                        + "「在领地内」这一条**暂时一律按满足算**——建筑链路可以试，"
                        + "但 §10.5 的门槛现在并没有真的生效。见 DEFERRED.md A4。");
            }
            return true;
        };
    }

    /** 给测试与"只想看环境本身"的场景用：把领地那一条固定成一个值。 */
    static ClaimProbe fixed(boolean value) {
        return (level, pos) -> value;
    }
}
