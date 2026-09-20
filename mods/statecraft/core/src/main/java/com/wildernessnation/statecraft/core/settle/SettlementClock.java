package com.wildernessnation.statecraft.core.settle;

/**
 * **什么时候该结算**（`NATIONS.md` §七 的三种触发里的前两种）。
 *
 * <p>纯算术，不碰世界：调用方（mod 层）负责按真实时间累积"这段到底在线了多久"，
 * 这里只回答"现在该不该结算、算哪种"。于是节拍能被 JUnit 钉死。
 *
 * <h2>两条纪律（都来自 §七 的原话）</h2>
 *
 * <ol>
 *   <li><b>不在线不推进</b>：{@code playersOnline <= 0} 时**一点都不能攒**。
 *       这是"回来时看未读队列"那种节奏的前提 —— 服务器空转不该烧掉全服预算。</li>
 *   <li><b>时代推进优先于周期小结算</b>：跨过时代边界时算一次 {@link Kind#ERA_ADVANCE}
 *       （§七 的"主节拍"），而不是先来一次小结算再推时代 —— 否则同一个时间点会连跑两次。</li>
 * </ol>
 *
 * <h2>为什么 delta 用"实际攒了多少"而不是正好 {@code periodicHours}</h2>
 *
 * <p>§七 的时钟口径是**累计在线小时**，而 {@code SettlementInput.elapsedHoursDelta} 的语义
 * 正是"这次结算代表多少累计在线小时"。四舍五入到整 2 小时会让时钟慢慢漂；
 * 用真实攒下的数（可能略多于 2）则永远不漂。
 */
public final class SettlementClock {

    private SettlementClock() {}

    /** 该做什么。 */
    public enum Kind {
        /** 什么都不做（还没攒够 / 没人在线）。 */
        NONE,
        /** 一次周期小结算（§七：每 {@code periodicHours} 累计在线小时）。 */
        PERIODIC,
        /** 一次时代推进（跨过了当前时代的结束点）。 */
        ERA_ADVANCE
    }

    /**
     * @param kind       该跑哪种
     * @param hoursDelta 这次结算代表多少累计在线小时（{@link Kind#NONE} 时为 0）
     */
    public record Plan(Kind kind, double hoursDelta) {

        public Plan {
            if (kind == null) {
                throw new IllegalArgumentException("Plan.kind 不能为 null");
            }
            if (!(hoursDelta >= 0.0) || !Double.isFinite(hoursDelta)) {
                throw new IllegalArgumentException("Plan.hoursDelta 必须 >= 0 且有限：" + hoursDelta);
            }
            if (kind == Kind.NONE && hoursDelta != 0.0) {
                throw new IllegalArgumentException("NONE 不该带时间增量：" + hoursDelta);
            }
            if (kind != Kind.NONE && hoursDelta <= 0.0) {
                throw new IllegalArgumentException(kind + " 必须带正的时间增量：" + hoursDelta);
            }
        }

        public boolean due() {
            return kind != Kind.NONE;
        }
    }

    private static final Plan NOTHING = new Plan(Kind.NONE, 0.0);

    /**
     * 现在该不该结算。
     *
     * @param elapsedOnlineHours 已经累计的在线小时（{@code WorldState.elapsedOnlineHours}）
     * @param pendingHours       上次结算之后又攒了多少（调用方自己记）
     * @param playersOnline      现在有几个玩家在线（**0 = 一点都不能攒**）
     * @param periodicHours      周期小结算的间隔（§七：默认 2 小时）
     * @param currentEraEndHours 当前时代的结束点（累计小时口径）；**{@code <= 0} = 没有下一个时代**
     */
    public static Plan plan(
            double elapsedOnlineHours,
            double pendingHours,
            int playersOnline,
            double periodicHours,
            double currentEraEndHours) {
        requireHours("elapsedOnlineHours", elapsedOnlineHours);
        requireHours("pendingHours", pendingHours);
        requireHours("periodicHours", periodicHours);
        if (periodicHours <= 0.0) {
            throw new IllegalArgumentException("periodicHours 必须 > 0：" + periodicHours);
        }
        if (!Double.isFinite(currentEraEndHours)) {
            throw new IllegalArgumentException("currentEraEndHours 必须是有限数：" + currentEraEndHours);
        }
        if (playersOnline <= 0 || pendingHours <= 0.0) {
            return NOTHING;                       // §七：不在线不推进
        }
        if (currentEraEndHours > 0.0
                && elapsedOnlineHours + pendingHours >= currentEraEndHours) {
            return new Plan(Kind.ERA_ADVANCE, pendingHours);
        }
        if (pendingHours >= periodicHours) {
            return new Plan(Kind.PERIODIC, pendingHours);
        }
        return NOTHING;
    }

    /**
     * 真实经过的秒数 → 在线小时（调用方按 tick 累加时用）。
     *
     * <p>为什么要有这个方法：把"秒 → 小时"这一步也放进 core，是为了让它**只出现在一个地方**。
     * mod 层各处自己写 {@code / 3600.0} 时，漏一个就是 3600 倍的错。
     */
    public static double secondsToHours(double seconds) {
        if (!(seconds >= 0.0) || !Double.isFinite(seconds)) {
            throw new IllegalArgumentException("seconds 必须 >= 0 且有限：" + seconds);
        }
        return seconds / 3600.0;
    }

    /**
     * **提前推进时代**要用的小时数：把累计在线小时推到这个数，{@link #plan} 下一拍就会判定
     * {@link Kind#ERA_ADVANCE}。
     *
     * <p>这 {@code = 当前时代的结束点 − 已经累计的小时数}（clamp 到 0）。
     *
     * <h2>为什么算"到边界还差多少"，而不是新加一种 trigger</h2>
     *
     * <p>时代推进不只换一个 eraId —— 它还要跑一次**时代级结算**（打事件、发国书、念"天下大势"），
     * 那些都在 {@code SettlementEngine} 里、由 {@code trigger == ERA_ADVANCE} 驱动。
     * 若为了"提前推进"另造一条旁路，就会出现"时代变了但没结算"的世界。
     * 所以这里只回答"时间还差多少"，推进本身**仍然由 {@link #plan} 与引擎按原路完成**：
     * <ul>
     *   <li>时间到 → 自动推进（老路径，一个字没改）；</li>
     *   <li>拿到时代钥匙 → 把小时数推到边界 → 走的是**同一段代码**。</li>
     * </ul>
     * 于是"同 seed 同输入必得同结果"仍然成立：加速改变的只是 elapsedOnlineHours，不是随机性。
     *
     * @param elapsedOnlineHours 已累计的在线小时（{@code WorldState.elapsedOnlineHours}）
     * @param currentBoundary    当前时代的结束点（{@code EraTable.boundaryAfter(current)}）；
     *                           <b>空 = 已经是最后一个时代，没有可推进的下一个时代</b>
     * @return 要推到的小时数；空表示**没有下一个时代**（调用方应当拒绝这次加速）
     */
    public static java.util.OptionalDouble hoursToBoundary(
            double elapsedOnlineHours, java.util.OptionalDouble currentBoundary) {
        requireHours("elapsedOnlineHours", elapsedOnlineHours);
        if (currentBoundary == null || currentBoundary.isEmpty()) {
            return java.util.OptionalDouble.empty();
        }
        double boundary = currentBoundary.getAsDouble();
        if (!Double.isFinite(boundary)) {
            throw new IllegalArgumentException("boundary 必须是有限数：" + boundary);
        }
        return java.util.OptionalDouble.of(Math.max(0.0, boundary - elapsedOnlineHours));
    }

    private static void requireHours(String name, double v) {
        if (!(v >= 0.0) || !Double.isFinite(v)) {
            throw new IllegalArgumentException(name + " 必须 >= 0 且有限：" + v);
        }
    }
}
