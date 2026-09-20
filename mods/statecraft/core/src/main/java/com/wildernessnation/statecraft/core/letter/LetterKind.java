package com.wildernessnation.statecraft.core.letter;

/**
 * 国书的种类（`NATIONS.md` §9.4：国家主动递来的**可交互事件**，带条件与期限）。
 *
 * <p>设计点名的三种：
 * <ul>
 *   <li>{@code STOP_BUILDING} —— "停止在边境建造"（给一个半径，期限内别在那儿动土）</li>
 *   <li>{@code JOINT_WAR} —— "共同讨伐 X 国"</li>
 *   <li>{@code TRIBUTE} —— "朝贡 N 资源换互不侵犯"</li>
 * </ul>
 */
public enum LetterKind {

    /** 停止在边境建造：接受就承诺一段期限（毁约由 mc 层报告）。 */
    STOP_BUILDING,

    /** 共同讨伐某国：接受即与目标国进入交战。 */
    JOINT_WAR,

    /** 朝贡换互不侵犯：接受即付出资源、换来一段不侵犯承诺。 */
    TRIBUTE
}
