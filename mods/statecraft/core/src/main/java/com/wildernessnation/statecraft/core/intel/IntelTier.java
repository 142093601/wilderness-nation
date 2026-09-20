package com.wildernessnation.statecraft.core.intel;

import com.wildernessnation.statecraft.core.diplomacy.DiplomacyAction;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;

/**
 * 情报能力的三档（`NATIONS.md` §11.1）。
 *
 * <p>三档不是"玩家等级"，而是**你手上有哪套设施**：
 *
 * <table>
 *   <tr><th>档</th><th>条件</th><th>能力</th></tr>
 *   <tr><td>{@link #BOOK}</td><td>`eras.json` 解锁 {@code intel_book}</td>
 *       <td>看已接触国家的基本信息、读未读事件、回复国书</td></tr>
 *   <tr><td>{@link #STATION}</td><td>建筑条件成立（情报站 + 文书村民）</td>
 *       <td>完整列表、**遣使**发起外交、**宣战/朝贡必须在此发起**</td></tr>
 *   <tr><td>{@link #UPGRADED}</td><td>对锚点用升级件</td>
 *       <td>可追踪国家 +2 · 显示正在进行的战争与同盟 · 每结算可深挖 1 国</td></tr>
 * </table>
 *
 * <p>**门槛一律按 `eras.json` 的 {@code unlocks} 判定**（§七："解锁一律按 unlocks 声明的 era id"），
 * 所以时代数量、什么时候解锁什么，都还是数据说了算。
 */
public enum IntelTier {

    /** 什么都没有：没有情报册就看不到任何东西。 */
    NONE,

    /** 情报册（物品）。 */
    BOOK,

    /** 情报站（建筑 + 文书村民）。 */
    STATION,

    /** 升级过的情报站。 */
    UPGRADED;

    /** 情报册的解锁项（`eras.json` 里 era 0 就给了）。 */
    public static final String UNLOCK_BOOK = "intel_book";

    /**
     * 情报站**图纸**的解锁项（= 建筑工什么时候开始卖这张图纸）。
     *
     * <p>注意它解锁的是"买得到图纸"，不是"档位变成情报站" —— 档位看的是**建筑本身**
     * （见 {@link #promotedBy}）。§11.1 那张表里第二档的条件写的就是"**建筑条件成立**"。
     */
    public static final String UNLOCK_STATION = "intel_station";

    /** 宣战/朝贡这类"要公告天下"的动作的解锁项（§11.2 的 {@code declaration}）。 */
    public static final String UNLOCK_DECLARATION = "declaration";

    /** **升级件**的解锁项（那个时代之后才有升级件可用）。 */
    public static final String UNLOCK_UPGRADE = "intel_station_2";

    /**
     * 时代能给你的**只有情报册**。
     *
     * <p>⚠️ 2026-09-20 更正：这一段原来会把 {@code intel_station} / {@code intel_station_2}
     * 也当成档位来源，于是"我到底是第几档"有了**两个真相来源**（时代解锁 vs 建筑等级）：
     * 一个二级情报站摆在早于 `intel_station_2` 的时代里，会既"建好了升级站"又"用不了追踪"。
     * 现在按 §11.1 那张表的字面意思分开：
     * <ul>
     *   <li><b>第一档（情报册）</b>是**时代**给的 → 这里判；</li>
     *   <li><b>第二/三档（情报站 / 升级站）</b>是**建筑**给的 → {@link #promotedBy} 判。</li>
     * </ul>
     */
    public static IntelTier tierFor(EraTable eras, Era current) {
        if (eras == null || current == null) {
            return NONE;
        }
        return eras.isUnlocked(UNLOCK_BOOK, current) ? BOOK : NONE;
    }

    /**
     * 建筑把档位往上推（§11.1 的第二/三档）。
     *
     * @param hasStation       有没有**绑好村民**的情报站（空壳不算，见 §10.4）
     * @param stationUpgraded  那座情报站到二级了吗（对锚点用过升级件）
     * @param upgradeUnlocked  升级件在这个时代出现了吗（`intel_station_2`）
     */
    public IntelTier promotedBy(boolean hasStation, boolean stationUpgraded,
            boolean upgradeUnlocked) {
        if (hasStation && stationUpgraded && upgradeUnlocked) {
            return UPGRADED;
        }
        if (hasStation) {
            return STATION;
        }
        return this;                 // 没站就还是原来那档（最多是第一档）
    }

    /** 有没有情报册（哪怕是升级过的站也给，因为它本来就包含第一档）。 */
    public boolean hasBook() {
        return this != NONE;
    }

    /** 能不能遣使发起外交（§11.1 第二档）。 */
    public boolean canDispatchDiplomacy() {
        return this == STATION || this == UPGRADED;
    }

    /** 宣战 / 朝贡这类必须"当着情报站的面"发起的动作（§11.2）。 */
    public boolean canDeclare(boolean declarationUnlocked) {
        return canDispatchDiplomacy() && declarationUnlocked;
    }

    /** 能追踪几个国家（§11.1："可追踪国家 +2"）。 */
    public int trackLimit() {
        return this == UPGRADED ? 2 : 0;
    }

    /** 能不能看到各国正在进行的战争与同盟（§11.1 第三档）。 */
    public boolean canSeeWars() {
        return this == UPGRADED;
    }

    /** 每结算能深挖几个国家的详报（§11.1 第三档）。 */
    public int deepDivesPerSettlement() {
        return this == UPGRADED ? 1 : 0;
    }

    /**
     * 这个外交动作在**这一档**下允许发起吗（§11.1 那句"宣战/朝贡必须在此发起"）。
     *
     * <p>两条正交的门槛：动作本身要不要"当面"（{@link DiplomacyAction#needsStation()}），
     * 以及 {@code declaration} 有没有解锁。分开写是为了让"宣战按钮灰着"和
     * "时代还没到"这两种情况能给出不同的中文解释。
     */
    public boolean allows(DiplomacyAction action, boolean declarationUnlocked) {
        if (action == null) {
            return false;
        }
        if (!action.needsStation()) {
            return hasBook();
        }
        return canDeclare(declarationUnlocked);
    }

    /** 给界面看的短名。 */
    public String displayName() {
        return switch (this) {
            case NONE -> "还没有情报能力";
            case BOOK -> "情报册";
            case STATION -> "情报站";
            case UPGRADED -> "情报站（已升级）";
        };
    }
}
