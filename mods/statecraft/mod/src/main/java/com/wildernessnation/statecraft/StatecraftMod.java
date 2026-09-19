package com.wildernessnation.statecraft;

import com.mojang.logging.LogUtils;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.data.StatecraftData;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Mod 入口。
 *
 * <p>这一层<strong>只做 NeoForge 适配</strong>：规则、状态、存档格式全在 {@code core}（纯 Java，
 * 离线 JUnit 可跑）。这里刻意不放任何业务逻辑，否则那部分就再也测不了了。
 */
@Mod(StatecraftMod.MOD_ID)
public final class StatecraftMod {

    public static final String MOD_ID = "statecraft";

    private static final Logger LOG = LogUtils.getLogger();

    public StatecraftMod() {
        // 必须早于任何存档读取：SavedData 反序列化要用到时代表
        StatecraftData.load();
        LOG.info("Statecraft（邦交）已加载，存档格式版本 {}", WorldState.CURRENT_SCHEMA_VERSION);
    }
}
