package com.wildernessnation.statecraft.core.report;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.model.EventTypes;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.text.Narrator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 周报 / 天下大势（`NATIONS.md` §七 三种触发的产出、§9.4、§11.1）。
 *
 * <p>把"一段时间窗里发生的事"聚合成**给人读的中文**：这是玩家了解世界的主要窗口
 * （设计里"远征的结果走情报站 + 周报"）。
 *
 * <p><strong>纯函数、确定性</strong>：只吃 {@link WorldState} 与叙事者，不碰世界。
 * 同一份状态染出来的周报永远一样（地名取词是确定性的，见 {@code Narrator}）。
 *
 * <p>结构上刻意分成两层：**先按类型归类**（谁宣战了、谁灭了、哪几场仗），
 * 再渲染成文本 —— 这样"摘要里要不要提这一类"是数据/配置的事，不用改渲染逻辑。
 */
public final class WeeklyReport {

    /** 一份周报：标题 + 分节正文 + 统计。 */
    public record Report(
            String title,
            List<Section> sections,
            Stats stats) {

        public Report {
            sections = List.copyOf(sections);
        }

        /** 全部正文拼起来（情报册/书里直接用）。 */
        public String body() {
            StringBuilder sb = new StringBuilder();
            for (Section s : sections) {
                sb.append(s.heading()).append('\n');
                for (String line : s.lines()) {
                    sb.append("  ").append(line).append('\n');
                }
            }
            return sb.toString();
        }

        public boolean isEmpty() {
            return sections.isEmpty();
        }
    }

    /** 一节：小标题 + 若干行。 */
    public record Section(String heading, List<String> lines) {
        public Section {
            if (heading == null || heading.isBlank()) {
                throw new IllegalArgumentException("Section.heading 不能为空");
            }
            lines = List.copyOf(lines);
        }
    }

    /** 统计数字（设计里的"天下大势"要报这些）。 */
    public record Stats(long living, long dead, long wars, long alliances, long absorbed) {
        public static Stats of(WorldState state) {
            long living = state.livingNations().size();
            long dead = state.nations().size() - living;
            long wars = state.relations().stream()
                    .filter(r -> r.state() == com.wildernessnation.statecraft.core.model.Relation.State.WAR)
                    .count();
            long alliances = state.relations().stream()
                    .filter(r -> r.state()
                            == com.wildernessnation.statecraft.core.model.Relation.State.ALLIANCE)
                    .count();
            long absorbed = state.events().stream()
                    .filter(e -> e.type().equals(EventTypes.ABSORBED)).count();
            return new Stats(living, dead, wars, alliances, absorbed);
        }
    }

    private static final String H_WAR = "战事";
    private static final String H_FALL = "兴亡";
    private static final String H_PLAYER = "与你有关";
    private static final String H_OTHER = "其余动静";

    private final Narrator narrator;

    public WeeklyReport(Narrator narrator) {
        this.narrator = narrator;
    }

    /**
     * 取**最后 {@code windowSize} 条事件**织一份周报。
     *
     * <p>为什么不按时间窗：事件本身只带"哪一次结算"（§五 的 `seq`），而两次结算之间的
     * 真实时长由触发方式决定（小结算 2 小时、时代推进可能是 40 小时）。用"最近 N 条"
     * 反而稳定、也和"未读队列"天然对齐。
     */
    public Report build(WorldState state, int windowSize) {
        return build(state, windowSize, "本周情报");
    }

    public Report build(WorldState state, int windowSize, String title) {
        if (state == null || windowSize <= 0) {
            throw new IllegalArgumentException("state 不能为空，windowSize 必须 > 0");
        }
        List<Event> events = state.events();
        int from = Math.max(0, events.size() - windowSize);
        List<Event> window = events.subList(from, events.size());

        Map<String, List<String>> bySection = new LinkedHashMap<>();
        bySection.put(H_WAR, new ArrayList<>());
        bySection.put(H_FALL, new ArrayList<>());
        bySection.put(H_PLAYER, new ArrayList<>());
        bySection.put(H_OTHER, new ArrayList<>());

        for (Event e : window) {
            if (!e.visible()) {
                continue;                     // 没见过面的国家，先不告诉玩家
            }
            bySection.get(sectionOf(e)).add(narrator.narrate(e));
        }

        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : bySection.entrySet()) {
            if (!e.getValue().isEmpty()) {
                sections.add(new Section(e.getKey(), e.getValue()));
            }
        }
        return new Report(title, sections, Stats.of(state));
    }

    private static String sectionOf(Event e) {
        return switch (e.type()) {
            case EventTypes.WAR_DECLARED, EventTypes.BATTLE, EventTypes.SIEGE_STARTED ->
                    H_WAR;
            case EventTypes.COLLAPSE, EventTypes.ABSORBED, EventTypes.CITY_TAKEN -> H_FALL;
            case EventTypes.RAID, EventTypes.LETTER, EventTypes.PLAYER_ACTION -> H_PLAYER;
            default -> H_OTHER;
        };
    }

    /** 「天下大势」一行摘要（§七 时代推进的那一封）。 */
    public String headline(WorldState state) {
        Stats s = Stats.of(state);
        return String.format(java.util.Locale.ROOT,
                "尚存 %d 国、覆灭 %d 国、%d 场战争未了、%d 对结盟",
                s.living(), s.dead(), s.wars(), s.alliances());
    }
}
