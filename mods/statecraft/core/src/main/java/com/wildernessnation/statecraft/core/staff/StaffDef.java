package com.wildernessnation.statecraft.core.staff;

import com.wildernessnation.statecraft.core.building.BuildingAbility;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一种村民（`NATIONS.md` §10.2 / §十六 的 `staff.json`）。
 *
 * <p>落地 §10.2 那条边界：**村民只做人气 + 界面，不写任何劳动 AI**。
 * 所以这份定义里没有"干什么活"，只有"叫什么、长什么样、站在哪座建筑里、给你开哪个界面"。
 *
 * @param id            职业标识（mod 层注册实体时用它认职业）
 * @param name          中文职业名（对话/情报册里显示）
 * @param buildingType  绑定的建筑类型（= {@code BuildingDef.id}）
 * @param names         名字池（§10.2"有名字"；与包里 `villager-names` 的语汇一致）
 * @param appearance    外观档位（皮肤包/兵种档位 id，文化相关；core 只当字符串）
 * @param greeting      见面第一句话（对话内容；真实对话树属于 mod 层）
 * @param abilities     这个村民身上开放的能力（{@link BuildingAbility}）
 */
public record StaffDef(
        String id,
        String name,
        String buildingType,
        List<String> names,
        String appearance,
        String greeting,
        Set<String> abilities) {

    public StaffDef {
        requireText(id, "StaffDef.id");
        requireText(name, "StaffDef.name");
        requireText(buildingType, "StaffDef.buildingType");
        requireText(greeting, "StaffDef.greeting");
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("StaffDef「" + id + "」需要至少一个名字");
        }
        names = List.copyOf(names);
        Set<String> unique = new LinkedHashSet<>();
        for (String n : names) {
            requireText(n, "StaffDef.names 的元素");
            if (!unique.add(n)) {
                throw new IllegalArgumentException(
                        "StaffDef「" + id + "」的名字池里有重复：" + n);
            }
        }
        appearance = appearance == null ? "" : appearance;
        abilities = abilities == null ? Set.of() : Set.copyOf(abilities);
    }

    public boolean hasAbility(String ability) {
        return abilities.contains(ability);
    }

    /**
     * 按名字池取第 index 个名字（**取模循环**，不是随机）。
     *
     * <p>为什么不做随机：名字会进存档（村民是实体，实体名字必须稳定）。
     * 随机源要是"生成那一刻的上下文"，区块重载后同一座建筑的文书就会换名字。
     * 所以这里只做纯函数映射，随机性由调用方用 {@code hash(seed, seq, tag)} 决定后传 index 进来。
     */
    public String nameAt(int index) {
        int i = index % names.size();
        if (i < 0) {
            i += names.size();
        }
        return names.get(i);
    }

    /** 名字池大小（mod 层取随机下标时要知道上界）。 */
    public int nameCount() {
        return names.size();
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }
}
