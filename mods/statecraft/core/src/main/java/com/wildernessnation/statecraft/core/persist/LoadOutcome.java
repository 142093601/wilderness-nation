package com.wildernessnation.statecraft.core.persist;

import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Optional;

/**
 * 读档结果。
 *
 * @param state    读出来的状态；{@link Optional#empty()} 表示<strong>国家系统重置</strong>
 *                 （坏档 / 版本对不上 / 缺迁移）——调用方用 {@code WorldGenerator} 重新生成，
 *                 <strong>世界本身不报废</strong>
 * @param warnings 给玩家/日志看的中文说明（按发生顺序）
 */
public record LoadOutcome(Optional<WorldState> state, List<String> warnings) {

    public LoadOutcome {
        if (state == null) {
            throw new IllegalArgumentException("LoadOutcome.state 不能为 null（用 Optional.empty()）");
        }
        warnings = List.copyOf(warnings);
    }

    /** 是否走了"重置国家系统"这条路。 */
    public boolean reset() {
        return state.isEmpty();
    }
}
