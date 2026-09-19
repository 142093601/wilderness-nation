package com.wildernessnation.statecraft.core.data;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import java.util.List;

/**
 * `nations.json` 读出来的东西：国名池（按文化）+ 数值配置。
 *
 * @param cultures 文化表（顺序有意义：国名抽取按列表下标键控，见 {@code WorldGenerator}）
 * @param config   数值配置（文件里没写的字段一律用 {@link StatecraftConfig#defaults()}）
 */
public record NationData(List<Culture> cultures, StatecraftConfig config) {

    public NationData {
        if (cultures == null || cultures.isEmpty()) {
            throw new IllegalArgumentException("nations.json 至少要有一个文化");
        }
        cultures = List.copyOf(cultures);
    }
}
