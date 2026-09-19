package com.wildernessnation.statecraft.core.persist;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 一级存档迁移：把 <strong>v{@code fromVersion}}</strong> 的树就地变换成 v{@code fromVersion+1} 的树。
 *
 * <p><strong>不用自己改 {@code schemaVersion}</strong>：{@link StateMigrator} 每走完一级会统一盖章，
 * 免得某一级忘了写，链就断了。
 *
 * <p>写法示例（将来加字段时）：
 * <pre>{@code
 * new StateMigration(1, root -> {
 *     // v1 -> v2：新增 relations 字段
 *     return StateMigrator.putField(root, "relations", new StateNode.Obj(StateNode.fields()));
 * })
 * }</pre>
 */
public record StateMigration(int fromVersion, UnaryOperator<StateNode> apply) {

    public StateMigration {
        if (fromVersion < 0) {
            throw new IllegalArgumentException("迁移起点版本不能为负：" + fromVersion);
        }
        Objects.requireNonNull(apply, "迁移函数不能为 null");
    }
}
