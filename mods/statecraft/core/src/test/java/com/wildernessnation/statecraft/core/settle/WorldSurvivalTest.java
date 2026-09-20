package com.wildernessnation.statecraft.core.settle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.era.Era;
import com.wildernessnation.statecraft.core.era.EraTable;
import com.wildernessnation.statecraft.core.gen.WorldGenerator;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * **世界撑不撑得住**：12 个国家跑到最后一个时代还剩几个（`NATIONS.md` §八）。
 *
 * <p>为什么单独立一条：2026-09-20 在真服务端上跑到第 110 个累计在线小时（6 个时代里的第 3 个），
 * `/statecraft info` 显示 **12 国里 10 国已亡**。而 300 小时的内容规划是以"世界上有十几个国家"
 * 为前提的 —— 如果世界在前三分之一就自己清空了，后面两百小时的国家层就没什么可玩的了。
 *
 * <p>这条**不做平衡决策**，只把"现在的参数下会发生什么"量出来，并钉一个下限：
 * **最后一个时代结束时至少要有一半国家活着**。理由：§八 要的是"兼并会发生"，
 * 不是"世界会清空"；而且 §八.8 的废墟调性需要**少数**旧世崩掉，不是大多数。
 *
 * <p>量出来的数会打印出来（原始证据），参数要调时照它调。
 */
class WorldSurvivalTest {

    /**
     * 测试用的文化表：**名字要够**（生成器在入口就会拒绝"名字不够分"的配置 ——
     * 这是 §六 的一条硬校验，`Nation.spawn` 之前就报错）。
     */
    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of("赤沙", "黄沙", "黑风", "断刃", "孤鹰",
                    "铁蹄", "血旗", "荒骨")),
            new Culture("desert_raider", "漠寇", List.of("旱海", "枯井", "裂石", "白骨",
                    "风蚀", "烈日", "孤驼", "干河")));

    /** 与 `eras.json` 同一条曲线（24/40/50/50/60/76 ≈ 300 小时）。 */
    private static EraTable sixEras() {
        return new EraTable(List.of(
                new Era("landing", "落地", 0, 24, 1.00, Set.of()),
                new Era("founding", "立国", 1, 40, 1.15, Set.of()),
                new Era("infra", "基建", 2, 50, 1.30, Set.of()),
                new Era("expedition", "远征", 3, 50, 1.45, Set.of()),
                new Era("defense", "御敌", 4, 60, 1.60, Set.of()),
                new Era("civilization", "文明", 5, 76, 1.75, Set.of())));
    }

    /** 一个时代里走多少拍（2 小时一拍，向上取整 + 1，保证跨过边界）。 */
    private static WorldState playTo(WorldState start, SettlementEngine engine, EraTable eras,
            int eraOrdinal, SettlementConfig cfg) {
        WorldState s = start;
        double target = 0.0;
        for (int i = 0; i <= eraOrdinal; i++) {
            target += eras.byOrdinal(i).durationHours();
        }
        int guard = 0;
        while (s.elapsedOnlineHours() < target && guard++ < 400) {
            s = engine.settle(s, SettlementInput.periodic(cfg));
        }
        return s;
    }

    @Test
    void atLeastHalfTheNationsSurviveToTheLastEra() {
        SettlementConfig cfg = SettlementConfig.defaults();
        EraTable eras = sixEras();
        SettlementEngine engine = new SettlementEngine(
                cfg, StatecraftConfig.defaults(), eras);

        int seeds = 12;
        List<Integer> survivorsAtEra3 = new ArrayList<>();
        List<Integer> survivorsAtEnd = new ArrayList<>();
        for (long seed = 0; seed < seeds; seed++) {
            WorldState start = new WorldGenerator(StatecraftConfig.defaults(), seed)
                    .generate(CULTURES, 0.0, 0.0, eras.byOrdinal(0).id(), 0);
            int total = start.nations().size();
            survivorsAtEra3.add(playTo(start, engine, eras, 2, cfg).livingNations().size());
            WorldState end = playTo(start, engine, eras, eras.size() - 1, cfg);
            survivorsAtEnd.add(end.livingNations().size());
            System.out.printf("seed %2d：开局 %d 国 → 时代3 %d 国 → 末代 %d 国%n",
                    seed, total, survivorsAtEra3.get(seeds == 12 ? (int) seed : 0),
                    survivorsAtEnd.get((int) seed));
        }

        double avgEra3 = survivorsAtEra3.stream().mapToInt(Integer::intValue).average()
                .orElse(0.0);
        double avgEnd = survivorsAtEnd.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        int total0 = new WorldGenerator(StatecraftConfig.defaults(), 0L)
                .generate(CULTURES, 0.0, 0.0, eras.byOrdinal(0).id(), 0).nations().size();
        System.out.printf("平均：开局 %d 国 → 时代3 存活 %.1f → 末代存活 %.1f（%d 个 seed）%n",
                total0, avgEra3, avgEnd, seeds);

        for (int i = 0; i < seeds; i++) {
            assertTrue(survivorsAtEnd.get(i) >= 1,
                    "seed " + i + " 把世界打空了（一个都不剩）");
        }
        // 一半的下限：§八 要的是"兼并会发生"，不是"世界会清空"
        assertTrue(avgEnd >= total0 / 2.0,
                "末代平均只剩 " + avgEnd + " 国（开局 " + total0 + "）—— 世界自己清空了，"
                        + "而 300 小时的内容规划是以十几个国家为前提的");
    }

    /** 顺带把"死掉的国家不再参与结算"钉住（§十三）。 */
    @Test
    void deadNationsStopFighting() {
        SettlementConfig cfg = SettlementConfig.defaults();
        EraTable eras = sixEras();
        SettlementEngine engine = new SettlementEngine(cfg, StatecraftConfig.defaults(), eras);
        WorldState end = playTo(new WorldGenerator(StatecraftConfig.defaults(), 3L)
                .generate(CULTURES, 0.0, 0.0, eras.byOrdinal(0).id(), 0), engine, eras,
                eras.size() - 1, cfg);
        for (Nation n : end.nations()) {
            if (!n.alive()) {
                assertTrue(end.relations().stream().noneMatch(
                                r -> r.connects(n.id(), n.id()) && r.state().name().equals("WAR")),
                        "灭亡的国家不该还留着交战关系：" + n.id());
            }
        }
    }
}
