package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 事件（`NATIONS.md` §五）。**只带结构化内容，不带渲染好的中文文本。**
 *
 * <p>为什么不让 core 直接产出中文句子：地名池、模板、周报排版都属于 M3 的情报册/周报，
 * 一旦把文本生成放进规则层，以后改文案就要动规则、跑规则测试。现在 core 给
 * `textKey + params`，计划 4 拿着 `events.json` 模板把它渲染出来。
 *
 * @param seq      产生它的结算序号（可用来"从上一个完整结算重放"）
 * @param type     {@link EventTypes} 里的词表
 * @param parties  涉及的国家 id（玩家方用 {@link Event#PARTY_PLAYER}）
 * @param textKey  渲染用的模板键（如 {@code battle}）
 * @param params   模板参数（有序，如地名与国名）
 * @param visible  是否可见（未接触的国家的事件先不显示）
 * @param read     是否已读（`unread` 由它派生，见 {@link WorldState#unreadEvents()}）
 */
public record Event(
        long seq,
        String type,
        List<String> parties,
        String textKey,
        List<String> params,
        boolean visible,
        boolean read) {

    /** 玩家方在 {@code parties} 里的固定代号（国家 id 都是 `n0` 这种，不会撞）。 */
    public static final String PARTY_PLAYER = "player";

    public Event {
        if (seq < 0) {
            throw new IllegalArgumentException("Event.seq 不能为负：" + seq);
        }
        requireText(type, "Event.type");
        requireText(textKey, "Event.textKey");
        parties = List.copyOf(parties == null ? List.of() : parties);
        params = List.copyOf(params == null ? List.of() : params);
        for (String p : parties) {
            requireText(p, "Event.parties 的元素");
        }
    }

    public Event asRead() {
        return new Event(seq, type, parties, textKey, params, visible, true);
    }

    public Event withVisible(boolean v) {
        return new Event(seq, type, parties, textKey, params, v, read);
    }

    private static void requireText(String v, String what) {
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException(what + " 不能为空");
        }
    }
}
