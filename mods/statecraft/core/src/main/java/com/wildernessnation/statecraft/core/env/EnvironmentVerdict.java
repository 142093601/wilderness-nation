package com.wildernessnation.statecraft.core.env;

import java.util.List;
import java.util.Optional;

/**
 * 环境判定的结果（`NATIONS.md` §10.5："决定能不能用 / 到哪一档"）。
 *
 * @param usableTierId 当前可用的**最高**档；一个都不满足时为空
 * @param nextTierId   下一个还没满足的档（{@code usableTierId} 为空时它就是最低档）——
 *                     玩家要的是"我还差哪一步"，不是一句"不能用"
 * @param unmet        {@code nextTierId} 那一档**没满足的每一条**，带实际读数（如"封闭度 42.0% < 60.0%"）
 */
public record EnvironmentVerdict(
        Optional<String> usableTierId,
        Optional<String> nextTierId,
        List<String> unmet) {

    public EnvironmentVerdict {
        if (usableTierId == null) {
            usableTierId = Optional.empty();
        }
        if (nextTierId == null) {
            nextTierId = Optional.empty();
        }
        unmet = List.copyOf(unmet == null ? List.of() : unmet);
    }

    public boolean usable() {
        return usableTierId.isPresent();
    }

    /** 给玩家看的一句话（拒绝理由逐条列出，不是"不能用"三个字）。 */
    public String describe() {
        if (usable()) {
            return nextTierId
                    .map(next -> "可用（" + usableTierId.get() + "）；再满足 "
                            + next + " 还能升一档：" + String.join("；", unmet))
                    .orElse("可用（" + usableTierId.get() + "），已是最高档");
        }
        return "还不能用，差：" + String.join("；", unmet);
    }
}
