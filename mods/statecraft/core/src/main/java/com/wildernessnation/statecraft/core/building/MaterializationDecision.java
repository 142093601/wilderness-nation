package com.wildernessnation.statecraft.core.building;

/**
 * 该不该为这座建筑（重新）生成村民（`NATIONS.md` §10.3 / §10.4）。
 *
 * <p>**不做任何世界改动** —— 这只是个决定，执行在 mc 层。
 * 这样"会不会生出三个文书"这种问题可以在 JUnit 里穷举，而不是靠开游戏试。
 */
public enum MaterializationDecision {

    /** 已经好了，**什么都不要做**（幂等的正常结果）。 */
    SKIP,

    /** 需要（重新）绑定村民。 */
    RESPAWN,

    /** 该重生，但冷却还没到 —— 等下一次。 */
    WAIT_COOLDOWN,

    /** 锚点没了 → 建筑登记为失效；**村民保留**，可按登记重新安放（§10.4）。 */
    MARK_INVALID
}
