package com.wildernessnation.statecraft.core.building;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 建筑定义表（`buildings.json` 读出来的东西）。
 *
 * <p>**"加内容"的入口**：§10.6 写着 v1 只实现两处（情报站、建筑工），
 * 仓库管事 / 铁匠 / 商人 / 驿站马夫 / 农夫 全是**只加数据**。所以这张表必须能装下
 * 任意多座建筑，代码里不出现"情报站"三个字。
 *
 * @param byId 按 id 索引（保留文件顺序，遍历顺序必须确定）
 */
public record BuildingCatalog(Map<String, BuildingDef> byId) {

    public BuildingCatalog {
        if (byId == null || byId.isEmpty()) {
            throw new IllegalArgumentException("buildings.json 至少要有一座建筑");
        }
        // 刻意**不用 Map.copyOf**：它的遍历顺序是未定义的，而这里的顺序必须等于文件顺序
        // （同一份数据两次跑出两种顺序，"同 seed 同输入必得同结果"就不成立了）
        byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    /** 按文件顺序列出。 */
    public List<BuildingDef> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public Optional<BuildingDef> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    /**
     * 取定义，没有就抛。
     *
     * <p>为什么这里是抛而不是给个默认值：调用点拿到的 id 来自**存档里已有的建筑**
     * （`Building.type`）或**数据里已有的事件**，取不到意味着"内容被删了而存档还在"——
     * 那必须被看见（§十三 的原则是"不让存档报废"，但也不该静默换一个东西出来）。
     */
    public BuildingDef require(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException(
                "buildings.json 里没有这座建筑：" + id + "（已有：" + byId.keySet() + "）"));
    }

    /** 需要图纸授权的那些（建筑工 NPC 的货架就是它）。 */
    public List<BuildingDef> authorized() {
        return all().stream().filter(BuildingDef::authorized).toList();
    }
}
