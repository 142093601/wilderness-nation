package com.wildernessnation.statecraft.core.intel;

import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * **"接触"是怎么发生的**（`NATIONS.md` §11.1 的"已接触国家"）。
 *
 * <h2>为什么需要这一层</h2>
 *
 * <p>{@code Nation.met} 在生成时一律是 {@code false}（{@code Nation.spawn} 写死的），
 * 而**没有任何地方会把它改成 true** —— 2026-09-20 接线时发现的空洞。
 * 后果不是"少一个字段"：§11.1 的情报册只显示已接触国家、§11.2 的六个外交动作全都
 * 以"已接触"为前置、§9.4 的国书也要求"接触过"。
 * 也就是说 **`met` 永远是 false 时，整个玩家侧（情报/外交/国书）都是死的**。
 *
 * <h2>两条接触路径（都来自 §11.1 的字面意思）</h2>
 *
 * <ol>
 *   <li><b>走近</b>：你（或你的建筑）到了它都城附近（{@link #DEFAULT_CONTACT_RADIUS} 格）。
 *       都城位置一直在 {@link Nation#x()} / {@link Nation#z()} 里，不依赖实体化。</li>
 *   <li><b>情报站建成</b>：§11.1 写着情报站给的是"**完整列表**"（而情报册只给已接触国家）——
 *       所以一座绑好村民的情报站，本身就是"把天下各国的名号都记进册子"。</li>
 * </ol>
 *
 * <p>第二条是**设计上最省事的那条**：玩家不必跑遍地图，把情报站建起来就解锁了全部外交。
 * 这也是《荒野建国》想要的节奏（先立情报站，再谈天下）。
 *
 * <p>【占位】"走近多少格算接触"文档没给数。取 512 的原因是它大约等于"你走到能看见它都城"的
 * 距离量级，而且比国与国之间 1500 格的最小间距小得多 —— 不会出现"走到 A 国就算认识了 B 国"。
 */
public final class IntelContact {

    /** 走近多少格算"接触"（【占位】，理由见类注释）。 */
    public static final double DEFAULT_CONTACT_RADIUS = 512.0;

    private IntelContact() {}

    /**
     * 把一个位置（玩家或玩家建筑）能接触到的国家标成已接触。
     *
     * @param x      位置 X
     * @param z      位置 Z
     * @param radius 半径（{@code <= 0} 时一个都不标）
     * @return 处理后的状态 + 这一次**新**接触到的国家 id（已经接触过的不重复列）
     */
    public static Contact byPosition(WorldState state, double x, double z, double radius) {
        if (state == null) {
            throw new IllegalArgumentException("WorldState 不能为 null");
        }
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("位置必须是有限数：" + x + "," + z);
        }
        if (!(radius > 0.0) || !Double.isFinite(radius)) {
            return new Contact(state, List.of());
        }
        List<Nation> nations = new ArrayList<>(state.nations());
        List<String> newly = new ArrayList<>();
        for (int i = 0; i < nations.size(); i++) {
            Nation n = nations.get(i);
            if (n.met() || !n.alive()) {
                continue;
            }
            if (n.distanceTo(x, z) <= radius) {
                nations.set(i, n.withMet(true));
                newly.add(n.id());
            }
        }
        if (newly.isEmpty()) {
            return new Contact(state, List.of());
        }
        return new Contact(state.withNations(nations), List.copyOf(newly));
    }

    /**
     * 情报站建成 → **全部国家都进册子**（§11.1 的"完整列表"）。
     *
     * <p>灭亡的国家**只标活着的**：§八.8 说灭亡的都城留着当废墟，玩家走到那儿自然会发现，
     * 但"情报站让死人复活"没有道理。
     */
    public static Contact revealAll(WorldState state) {
        if (state == null) {
            throw new IllegalArgumentException("WorldState 不能为 null");
        }
        List<Nation> nations = new ArrayList<>(state.nations());
        List<String> newly = new ArrayList<>();
        for (int i = 0; i < nations.size(); i++) {
            Nation n = nations.get(i);
            if (n.met()) {
                continue;
            }
            nations.set(i, n.withMet(true));
            if (n.alive()) {
                newly.add(n.id());
            } else {
                newly.add(n.id() + "(已亡)");
            }
        }
        if (newly.isEmpty()) {
            return new Contact(state, List.of());
        }
        return new Contact(state.withNations(nations), List.copyOf(newly));
    }

    /** 已接触的国家数（给情报册的标题用）。 */
    public static int metCount(WorldState state) {
        int out = 0;
        for (Nation n : state.nations()) {
            if (n.met()) {
                out++;
            }
        }
        return out;
    }

    /** 还没接触的（给"情报站能解锁什么"的提示用）。 */
    public static Set<String> unmet(WorldState state) {
        Set<String> out = new LinkedHashSet<>();
        for (Nation n : state.nations()) {
            if (!n.met()) {
                out.add(n.id());
            }
        }
        return out;
    }

    /**
     * 接触的结果。
     *
     * @param state 处理后的状态（没有新接触时**原样返回入参**）
     * @param newly 这一次新接触到的国家 id（空 = 什么都没变）
     */
    public record Contact(WorldState state, List<String> newly) {

        public Contact {
            if (state == null) {
                throw new IllegalArgumentException("Contact.state 不能为 null");
            }
            newly = newly == null ? List.of() : List.copyOf(newly);
        }

        public boolean changed() {
            return !newly.isEmpty();
        }
    }
}
