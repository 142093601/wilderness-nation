package com.wildernessnation.statecraft.core.intel;

import com.wildernessnation.statecraft.core.model.Nation;
import java.util.Optional;

/**
 * 情报册里一条**国家条目**——按档位决定露出哪些字段（`NATIONS.md` §11.1）。
 *
 * <p>为什么不直接把 {@link Nation} 给界面：`Nation` 是**规则层的完整状态**
 * （含军力、国库、疲劳、absorbedCount、partyWarScore…）。要是界面拿到它，
 * "第三档才能深挖"就变成了界面自觉少显示几行——**一条注释就能被绕过**。
 * 所以这里是一层**投影**：档位不够时那些字段根本不在对象里（用 {@link Optional} 表达）。
 *
 * <p>各档看得到什么（按 §11.1 的字面意思）：
 * <ul>
 *   <li>{@link IntelTier#BOOK}：名字、文化、规模、态度 —— "基本信息"；</li>
 *   <li>{@link IntelTier#STATION}：再加发展度、政治倾向、军力、国库（"完整列表"）；</li>
 *   <li>{@link IntelTier#UPGRADED}：再加正在进行的战争与同盟、以及"被追踪"标记。</li>
 * </ul>
 *
 * <p><strong>未接触的国家（{@code met=false}）不出现在列表里</strong>——连名字都不给。
 * 这不是"少显示"，是情报本身还不存在（§11.1 第一档写的正是"**已接触**国家"）。
 *
 * @param id          国家 id（列表里选人要用）
 * @param name        国名
 * @param cultureId   文化
 * @param size        规模（1~10）
 * @param attitude    对玩家方的态度（有符号）
 * @param development 发展度；第一档为空
 * @param stance      政治倾向；第一档为空
 * @param military    军力；第一档为空
 * @param treasury    国库；第一档为空
 * @param atWarWithParty 是否正与玩家方交战（这个第一档就给：它已经打你了，藏不住）
 * @param partyStatus 对玩家方的外交状态（中立/交战/同盟/停战）
 * @param atWarWith 与哪些国家正在交战；只有第三档为空以外有值
 * @param alliedWith 与哪些国家同盟；只有第三档有值
 * @param tracked     是否被玩家追踪（第三档起才有意义）
 */
public record NationBrief(
        String id,
        String name,
        String cultureId,
        int size,
        double attitude,
        Optional<Double> development,
        Optional<Double> stance,
        Optional<Double> military,
        Optional<Double> treasury,
        boolean atWarWithParty,
        Nation.PartyStatus partyStatus,
        java.util.List<String> atWarWith,
        java.util.List<String> alliedWith,
        boolean tracked) {

    public NationBrief {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("NationBrief.id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("NationBrief.name 不能为空");
        }
        development = development == null ? Optional.empty() : development;
        stance = stance == null ? Optional.empty() : stance;
        military = military == null ? Optional.empty() : military;
        treasury = treasury == null ? Optional.empty() : treasury;
        atWarWith = atWarWith == null ? java.util.List.of() : java.util.List.copyOf(atWarWith);
        alliedWith = alliedWith == null ? java.util.List.of() : java.util.List.copyOf(alliedWith);
    }

    /** 这一档是不是"完整列表"（后面几档的字段在不在）。 */
    public boolean detailed() {
        return development.isPresent();
    }

    /** 第三档才有的"局势"字段有没有给。 */
    public boolean showsDiplomacy() {
        return !atWarWith.isEmpty() || !alliedWith.isEmpty();
    }
}
