package com.wildernessnation.statecraft.core.intel;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.Relation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 情报册的数据模型（`NATIONS.md` §11.1 的三档）。
 *
 * <p>"情报册"在游戏里是一本书/一个界面，但它能显示什么**完全是规则**，所以规则在这里：
 * 档位决定能不能遣使、能追几个国家、每结算能深挖几国；名单按"接没接触过"过滤。
 *
 * <h2>玩家侧的选择由调用方递进来</h2>
 *
 * <p>"追踪了哪些国家""这个结算深挖过没有"属于玩家侧状态（和 `playerLedger` 一样，
 * 计划 4/5 才落盘）。所以这里收的是调用方递进来的那两个事实，
 * 输出 {@link IntelVerdict}——**不改任何状态，也不持有追踪清单**。
 *
 * <h2>遍历顺序</h2>
 *
 * <p>名单一律&#64;**按国家 id 顺序**（= `nations[]` 的顺序，由生成器固定）。
 * 不做"按态度排序"之类的界面友好排序：那种排序会随每次结算漂，
 * 同一份存档两次打开看到的名次不一样，玩家会以为数据坏了。
 */
public final class IntelBook {

    private IntelBook() {}

    /**
     * 这一档能看到的名单。
     *
     * <p>未接触的国家不出现（§11.1："看**已接触**国家的基本信息"）；
     * 灭亡的国家**要出现**并标注——§八.8 说它们的都城留在世界里当废墟，
     * 情报册里突然消失才是怪事（玩家会以为是自己看漏了）。
     */
    public static List<NationBrief> listing(
            WorldState state, IntelTier tier, boolean declarationUnlocked, Set<String> tracked) {
        if (state == null) {
            throw new IllegalArgumentException("WorldState 不能为 null");
        }
        if (tier == null) {
            throw new IllegalArgumentException("IntelTier 不能为 null");
        }
        if (!tier.hasBook()) {
            return List.of();
        }
        Set<String> track = tracked == null ? Set.of() : tracked;
        List<NationBrief> out = new ArrayList<>();
        for (Nation n : state.nations()) {
            if (!n.met()) {
                continue;
            }
            out.add(brief(state, n, tier, track.contains(n.id())));
        }
        return out;
    }

    /** 单个国家的条目（深挖详报用的就是它，只是档位更高）。 */
    public static NationBrief brief(
            WorldState state, Nation nation, IntelTier tier, boolean tracked) {
        boolean full = tier == IntelTier.STATION || tier == IntelTier.UPGRADED;
        boolean diplomacy = tier.canSeeWars();
        List<String> atWarWith = new ArrayList<>();
        List<String> alliedWith = new ArrayList<>();
        if (diplomacy) {
            for (Relation r : state.relations()) {
                // 关系是"两端"的：只挑**涉及它**的那些。这里不能用 Relation.connects(x, x)
                // ——那两个参数是"两端"，传同一个 id 进去恒为 false（踩过一次）
                if (!involves(r, nation.id())) {
                    continue;
                }
                if (r.state() == Relation.State.WAR) {
                    atWarWith.add(other(r, nation.id()));
                } else if (r.state() == Relation.State.ALLIANCE) {
                    alliedWith.add(other(r, nation.id()));
                }
            }
        }
        return new NationBrief(
                nation.id(),
                nation.name(),
                nation.cultureId(),
                nation.size(),
                nation.attitudeToParty(),
                full ? Optional.of(nation.development()) : Optional.empty(),
                full ? Optional.of(nation.stance()) : Optional.empty(),
                full ? Optional.of(nation.military()) : Optional.empty(),
                full ? Optional.of(nation.treasury()) : Optional.empty(),
                nation.atWarWithParty(),
                nation.partyStatus(),
                atWarWith,
                alliedWith,
                tracked);
    }

    /**
     * 追踪一个国家（§11.1 第三档的"可追踪国家 +2"）。
     *
     * <p>已经追着的不算超限（重复点等于没点，不是错误）——玩家不该因为多点了一下就被骂。
     */
    public static IntelVerdict track(
            IntelTier tier, Set<String> tracked, String nationId) {
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId 不能为空");
        }
        Set<String> current = tracked == null ? Set.of() : tracked;
        int limit = tier.trackLimit();
        if (limit <= 0) {
            return IntelVerdict.rejected("现在的"
                    + tier.displayName() + "还追不了国家（要升级情报站）");
        }
        if (current.contains(nationId)) {
            return IntelVerdict.accepted("已经在追着它了");
        }
        if (current.size() >= limit) {
            return IntelVerdict.rejected("最多同时追 " + limit + " 个国家，先取消一个");
        }
        return IntelVerdict.accepted("开始追踪（" + (current.size() + 1) + "/" + limit + "）");
    }

    /** 一次结算里能不能再深挖一份详报（§11.1 第三档："每结算可深挖 1 国详报"）。 */
    public static IntelVerdict deepDive(IntelTier tier, int usedThisSettlement) {
        if (usedThisSettlement < 0) {
            throw new IllegalArgumentException("usedThisSettlement 不能为负：" + usedThisSettlement);
        }
        int limit = tier.deepDivesPerSettlement();
        if (limit <= 0) {
            return IntelVerdict.rejected("现在的" + tier.displayName() + "只能看简报了（升级情报站才有详报）");
        }
        if (usedThisSettlement >= limit) {
            return IntelVerdict.rejected("这个结算已经深挖过了，等下一次结算");
        }
        return IntelVerdict.accepted("翻开详报");
    }

    /**
     * 深挖出来的那段历史：**只取"和这个国家有关"的事件**，按发生顺序。
     *
     * <p>为什么从事件日志里取而不是另记一份国史：§八.8 说灭亡国家的"事件与国史保留"，
     * 而事件日志本来就是那份国史（上限 500 条，§十二）。另记一份 = 两处可能不一致。
     *
     * @param limit 最多给几条（从最近的往回想，但**返回时按时间正序**，读起来才顺）
     */
    public static List<Event> history(WorldState state, String nationId, int limit) {
        if (nationId == null || nationId.isBlank()) {
            throw new IllegalArgumentException("nationId 不能为空");
        }
        if (limit <= 0) {
            return List.of();
        }
        List<Event> hits = new ArrayList<>();
        for (Event e : state.events()) {
            if (e.visible() && e.parties().contains(nationId)) {
                hits.add(e);
            }
        }
        int from = Math.max(0, hits.size() - limit);
        return List.copyOf(hits.subList(from, hits.size()));
    }

    /**
     * 读未读事件（§11.1 第一档就有的能力）：把状态里的未读事件标成已读。
     *
     * <p>这里只管"有没有资格读"，读的动作（{@link WorldState#withAllEventsRead()}）由状态自己完成。
     */
    public static IntelVerdict readUnread(IntelTier tier, int unreadCount) {
        if (unreadCount < 0) {
            throw new IllegalArgumentException("unreadCount 不能为负：" + unreadCount);
        }
        if (!tier.hasBook()) {
            return IntelVerdict.rejected("你还没有情报册");
        }
        if (unreadCount == 0) {
            return IntelVerdict.rejected("没有新的消息");
        }
        return IntelVerdict.accepted("读了 " + unreadCount + " 条新消息");
    }

    /** 追踪清单的规范化副本（去重 + 保序；存进玩家账本前先把脏数据理掉）。 */
    public static Set<String> normalizeTracked(Set<String> tracked) {
        return tracked == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tracked));
    }

    private static String other(Relation r, String selfId) {
        return r.a().equals(selfId) ? r.b() : r.a();
    }

    private static boolean involves(Relation r, String nationId) {
        return r.a().equals(nationId) || r.b().equals(nationId);
    }
}
