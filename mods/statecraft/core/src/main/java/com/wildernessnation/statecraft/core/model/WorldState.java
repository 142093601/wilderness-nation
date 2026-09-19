package com.wildernessnation.statecraft.core.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 世界状态（`NATIONS.md` §五）。
 *
 * <p>字段顺序与设计文档 §五 保持一致：`schemaVersion, seed, eraId, eraOrdinal, seq, nations,
 * relations, events`。**唯一的追加**是 {@code elapsedOnlineHours} —— 文档没列它，
 * 但 §七 的"按累计在线时间推进时代"必须有个地方攒这个数。
 *
 * <p>还没做的字段（`unread` / `letters` / `buildings` / `domains` / `playerLedger`）属于计划 4/5：
 * `unread` 由 {@link Event#read} 派生（见 {@link #unreadEvents()}），其余等有规则读它们时再加。
 *
 * <p><strong>上限在构造器里强制</strong>：事件只留最近 {@value #MAX_EVENTS} 条、
 * 未读最多 {@value #MAX_UNREAD} 条（超出的把最旧的标成已读——§十二 写着"归档 ≠ 丢失"，
 * 所以是标记而不是删掉）。这样"状态一旦构造出来就是合法的"，不用每个调用点自己记得裁。
 */
public record WorldState(
        int schemaVersion,
        long seed,
        String eraId,
        int eraOrdinal,
        long seq,
        List<Nation> nations,
        List<Relation> relations,
        List<Event> events,
        double elapsedOnlineHours) {

    /** 当前代码写出的存档格式版本。加字段就 +1，并在 {@code StateMigrations} 里补一级迁移。 */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    /** 事件保留条数（§十二）。 */
    public static final int MAX_EVENTS = 500;

    /** 未读事件上限（§十二）。 */
    public static final int MAX_UNREAD = 200;

    public WorldState {
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("WorldState.schemaVersion 必须 >= 1：" + schemaVersion);
        }
        if (nations == null) {
            throw new IllegalArgumentException("WorldState.nations 不能为 null");
        }
        if (eraId == null || eraId.isBlank()) {
            throw new IllegalArgumentException("WorldState.eraId 不能为空");
        }
        if (eraOrdinal < 0) {
            throw new IllegalArgumentException("WorldState.eraOrdinal 不能为负：" + eraOrdinal);
        }
        if (seq < 0) {
            throw new IllegalArgumentException("WorldState.seq 不能为负：" + seq);
        }
        if (!(elapsedOnlineHours >= 0.0) || !Double.isFinite(elapsedOnlineHours)) {
            throw new IllegalArgumentException(
                    "WorldState.elapsedOnlineHours 必须 >= 0 且有限：" + elapsedOnlineHours);
        }
        nations = List.copyOf(nations);
        relations = List.copyOf(relations == null ? List.of() : relations);
        events = List.copyOf(events == null ? List.of() : events);

        // 关系不能指向不存在的国家：灭亡的国家仍留在 nations 里（status=DEAD），所以这条恒可满足，
        // 一旦不满足就是真的写坏了。
        Set<String> ids = new LinkedHashSet<>();
        for (Nation n : nations) {
            if (!ids.add(n.id())) {
                throw new IllegalArgumentException("国家 id 重复：" + n.id());
            }
        }
        for (Relation r : relations) {
            if (!ids.contains(r.a()) || !ids.contains(r.b())) {
                throw new IllegalArgumentException("关系指向了不存在的国家：" + r.a() + " ↔ " + r.b());
            }
        }

        events = trimEvents(events);
    }

    private static List<Event> trimEvents(List<Event> input) {
        List<Event> kept = input;
        if (kept.size() > MAX_EVENTS) {
            kept = new ArrayList<>(kept.subList(kept.size() - MAX_EVENTS, kept.size()));
        }
        long unread = kept.stream().filter(e -> !e.read()).count();
        if (unread <= MAX_UNREAD) {
            return kept;
        }
        // 超出未读上限：把**最旧的**未读标成已读（归档 ≠ 丢失）
        long toArchive = unread - MAX_UNREAD;
        List<Event> out = new ArrayList<>(kept.size());
        for (Event e : kept) {
            if (toArchive > 0 && !e.read()) {
                out.add(e.asRead());
                toArchive--;
            } else {
                out.add(e);
            }
        }
        return out;
    }

    /** 存活的国家（引擎每步只看这些；灭亡的仍留在 {@link #nations()} 里当史料）。 */
    public List<Nation> livingNations() {
        List<Nation> out = new ArrayList<>();
        for (Nation n : nations) {
            if (n.alive()) {
                out.add(n);
            }
        }
        return out;
    }

    public Optional<Nation> nation(String id) {
        return nations.stream().filter(n -> n.id().equals(id)).findFirst();
    }

    /** 查一段关系（无视双方顺序）。 */
    public Optional<Relation> relation(String a, String b) {
        return relations.stream().filter(r -> r.connects(a, b)).findFirst();
    }

    /** 未读事件（按发生顺序）。 */
    public List<Event> unreadEvents() {
        List<Event> out = new ArrayList<>();
        for (Event e : events) {
            if (!e.read()) {
                out.add(e);
            }
        }
        return out;
    }

    public WorldState withNations(List<Nation> v) {
        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, v, relations, events,
                elapsedOnlineHours);
    }

    public WorldState withRelations(List<Relation> v) {
        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations, v, events,
                elapsedOnlineHours);
    }

    /** 追加事件（上限由构造器负责裁）。 */
    public WorldState withEventsAdded(List<Event> extra) {
        List<Event> merged = new ArrayList<>(events);
        merged.addAll(extra);
        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations, relations,
                merged, elapsedOnlineHours);
    }

    /** 全部标为已读（打开情报册时用）。 */
    public WorldState withAllEventsRead() {
        List<Event> out = new ArrayList<>(events.size());
        for (Event e : events) {
            out.add(e.asRead());
        }
        return new WorldState(schemaVersion, seed, eraId, eraOrdinal, seq, nations, relations,
                out, elapsedOnlineHours);
    }

    /** 推进时钟与时代（引擎结算完时一次性写回）。 */
    public WorldState withClock(double hours, String newEraId, int newEraOrdinal, long newSeq) {
        return new WorldState(schemaVersion, seed, newEraId, newEraOrdinal, newSeq, nations,
                relations, events, hours);
    }
}
