package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 文化：决定国名池与外观（外观到 mod 层才用得上，core 不关心）。
 *
 * @param id            稳定标识（bandit / desert_raider / 自造）
 * @param displayName   中文显示名
 * @param namePrefixes  国名前缀池（全局唯一由生成器负责）
 */
public record Culture(String id, String displayName, List<String> namePrefixes) {

    public Culture {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Culture.id 不能为空");
        }
        if (namePrefixes == null || namePrefixes.isEmpty()) {
            throw new IllegalArgumentException("Culture.namePrefixes 不能为空：" + id);
        }
        namePrefixes = List.copyOf(namePrefixes);
    }
}
