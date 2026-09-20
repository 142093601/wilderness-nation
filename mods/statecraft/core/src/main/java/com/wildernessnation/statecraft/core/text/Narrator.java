package com.wildernessnation.statecraft.core.text;

import com.wildernessnation.statecraft.core.model.Event;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 叙事者：把事件（{@code textKey + params}）渲染成**中文句子**。
 *
 * <p>这是"计划 4 才做文本"那个决定的落点：计划 3 的规则层只产出结构化事件，
 * 文本在这里、由**数据模板**决定。于是改文案不用动规则、也不用跑规则测试。
 *
 * <h2>确定性</h2>
 * 地名用 {@code hash(seed, 事件序号, 用途标签)} 取 —— 同一个事件永远渲染出同一句话。
 * 这不是洁癖：事件的渲染结果会进存档（未读队列），如果每次渲染都不一样，
 * "重放得到同一段历史"就不成立了。
 */
public final class Narrator {

    /** 匹配 {@code {0}} 或 {@code {p:town}}。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");

    private final TextTemplates templates;
    private final PlaceNames places;
    private final long seed;

    public Narrator(TextTemplates templates, PlaceNames places, long seed) {
        this.templates = templates;
        this.places = places;
        this.seed = seed;
    }

    /** 渲染一个事件。 */
    public String narrate(Event event) {
        return render(event.textKey(), event.params(), event.seq());
    }

    /**
     * @param textKey 模板键
     * @param params  规则层给的参数（按顺序对应 {@code {0}} {@code {1}} …）
     * @param seq     事件所属结算序号（地名取词的确定性来源）
     */
    public String render(String textKey, List<String> params, long seq) {
        String template = templates.require(textKey);
        DeterministicRandom rng = new DeterministicRandom(seed);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        int placeIndex = 0;
        while (m.find()) {
            String token = m.group(1).trim();
            String replacement;
            if (token.startsWith("p:")) {
                String kind = token.substring(2).trim();
                replacement = places.pick(kind, rng, seq, token + "#" + (placeIndex++));
            } else {
                int index;
                try {
                    index = Integer.parseInt(token);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException(
                            "模板「" + textKey + "」里有不认识的占位符：{" + token + "}");
                }
                if (index < 0 || index >= params.size()) {
                    throw new IllegalStateException("模板「" + textKey + "」要第 " + index
                            + " 个参数，但事件只给了 " + params.size() + " 个：" + params);
                }
                replacement = params.get(index);
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
