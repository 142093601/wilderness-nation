package com.wildernessnation.statecraft.core.staff;

import com.wildernessnation.statecraft.core.building.BuildingCatalog;
import com.wildernessnation.statecraft.core.building.BuildingDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 村民定义表（`staff.json` 读出来的东西）+ **它和建筑表的交叉校验**。
 *
 * <p>为什么不各自为政：`buildings.json` 里写着"这座建筑绑哪个职业"，
 * `staff.json` 里写着"这个职业绑哪座建筑"——**同一件事写了两遍**。
 * 两份数据总有一份会先被改，然后出现"建筑找不到村民"或"村民没有落脚处"这种
 * 只会在真机上才发现的错。所以这里在**载入时**就把两边对齐一次，对不上直接报错。
 *
 * @param byId 按职业 id 索引（保留文件顺序）
 */
public record StaffCatalog(Map<String, StaffDef> byId) {

    public StaffCatalog {
        if (byId == null || byId.isEmpty()) {
            throw new IllegalArgumentException("staff.json 至少要有一个村民职业");
        }
        byId = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
    }

    public List<StaffDef> all() {
        return List.copyOf(byId.values());
    }

    public int size() {
        return byId.size();
    }

    public Optional<StaffDef> byId(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    public StaffDef require(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException(
                "staff.json 里没有这个职业：" + id + "（已有：" + byId.keySet() + "）"));
    }

    /** 哪座建筑要这个村民（反向查；一份职业绑多座建筑是允许的，比如仓库与商栈共用商人）。 */
    public List<BuildingDef> buildingsOf(String staffId, BuildingCatalog buildings) {
        List<BuildingDef> out = new ArrayList<>();
        for (BuildingDef def : buildings.all()) {
            if (def.staffProfession().equals(staffId)) {
                out.add(def);
            }
        }
        return out;
    }

    /**
     * 两张表必须互相指得通（`buildings.json` ↔ `staff.json`）。
     *
     * <p>两个方向都要查：
     * <ul>
     *   <li>建筑声明的职业必须在村民表里（否则实体化时会刷不出人）；</li>
     *   <li>村民声明的建筑类型必须在建筑表里（否则这个村民永远没有落脚处）；</li>
     *   <li>村民的本事必须**盖得住**建筑满级后开放的能力（建筑开了而村民不会，
     *       就会出现"界面按钮在但没人应答"这种最难查的错）。</li>
     * </ul>
     *
     * @return 问题清单（空 = 对齐）。**不抛**：调用方决定是拒绝启动还是只记一条警告。
     */
    public List<String> crossCheck(BuildingCatalog buildings) {
        List<String> problems = new ArrayList<>();
        for (BuildingDef def : buildings.all()) {
            Optional<StaffDef> staff = byId(def.staffProfession());
            if (staff.isEmpty()) {
                problems.add("建筑「" + def.id() + "」要的村民职业不在 staff.json 里："
                        + def.staffProfession());
                continue;
            }
            if (!staff.get().buildingType().equals(def.id())) {
                problems.add("建筑「" + def.id() + "」绑「" + def.staffProfession()
                        + "」，但那个村民说自己绑的是「" + staff.get().buildingType() + "」");
            }
            Set<String> missing = new LinkedHashSet<>(def.abilities());
            missing.removeAll(staff.get().abilities());
            if (!missing.isEmpty()) {
                problems.add("建筑「" + def.id() + "」满级后开放 " + missing
                        + "，但村民「" + def.staffProfession() + "」不会这些");
            }
        }
        for (StaffDef staff : all()) {
            if (buildings.byId(staff.buildingType()).isEmpty()) {
                problems.add("村民「" + staff.id() + "」说自己在「" + staff.buildingType()
                        + "」里，但 buildings.json 没有那座建筑");
            }
        }
        return problems;
    }

    /** 交叉校验不通过就抛（生产载入用这个）。 */
    public StaffCatalog validatedAgainst(BuildingCatalog buildings) {
        List<String> problems = crossCheck(buildings);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("buildings.json 与 staff.json 对不上：\n  - "
                    + String.join("\n  - ", problems));
        }
        return this;
    }
}
