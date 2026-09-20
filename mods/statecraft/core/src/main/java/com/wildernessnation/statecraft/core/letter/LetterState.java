package com.wildernessnation.statecraft.core.letter;

/**
 * 国书的状态（`NATIONS.md` §9.4："接受 / 拒绝 / **忽略=拒绝**"）。
 */
public enum LetterState {

    /** 还没回。 */
    OPEN,

    /** 接受了（承诺类国书在期限内有效）。 */
    ACCEPTED,

    /** 明确拒绝了。 */
    REFUSED,

    /** 过了期限还没回 = 拒绝（§9.4 的原话）。 */
    EXPIRED,

    /**
     * 接受过、但在承诺期内**破了约**（只有「停止在边境建造」用得上，§9.4）。
     *
     * <p>为什么单独立一个状态而不是把 {@code promiseUntilSeq} 清零了事：
     * "履约完成"和"中途毁约"对玩家是两件事（态度一个 +10 一个 −25），
     * 只留一个数字的话，事后翻国书看不出当时到底算哪种。
     */
    BREACHED
}
