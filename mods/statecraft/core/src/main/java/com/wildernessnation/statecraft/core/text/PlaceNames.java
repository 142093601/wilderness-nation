package com.wildernessnation.statecraft.core.text;

import com.wildernessnation.statecraft.core.persist.StateNode;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 虚构地名池（`NATIONS.md` §十六 `places.json`）：供周报与事件摘要取词。
 *
 * <p>设计里点名了四类：**都城 / 边镇 / 关隘 / 渡口**。示例句是
 * "赤沙国攻破白岩国边镇『石口』" —— 其中"石口"就来自这里。
 *
 * <p><strong>取词必须确定性</strong>：同一个事件（同 `seed` + 同 `seq` + 同用途标签）
 * 永远取到同一个地名。否则重放会产出不同的文本，"同 seed 同输入必得同结果"就破了
 * （事件的 textKey/params 是状态的一部分，渲染结果也是）。
 */
public record PlaceNames(Map<String, List<String>> pools) {

    public static final String CAPITAL = "capital";
    public static final String TOWN = "town";
    public static final String PASS = "pass";
    public static final String FORD = "ford";

    public PlaceNames {
        if (pools == null || pools.isEmpty()) {
            throw new IllegalArgumentException("地名池不能为空");
        }
        Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : pools.entrySet()) {
            String kind = e.getKey();
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("地名池的种类名不能为空");
            }
            List<String> names = e.getValue();
            if (names == null || names.isEmpty()) {
                throw new IllegalArgumentException("地名池「" + kind + "」不能为空");
            }
            List<String> cleaned = new ArrayList<>(names.size());
            for (String n : names) {
                if (n == null || n.isBlank()) {
                    throw new IllegalArgumentException("地名池「" + kind + "」里有空白项");
                }
                cleaned.add(n);
            }
            copy.put(kind, List.copyOf(cleaned));
        }
        pools = Map.copyOf(copy);
    }

    public Set<String> kinds() {
        return new LinkedHashSet<>(pools.keySet());
    }

    public boolean has(String kind) {
        return pools.containsKey(kind);
    }

    /**
     * 取一个地名。
     *
     * @param seq 事件所属的结算序号（保证同一事件永远取到同一个地名）
     * @param tag 用途标签（同一事件里要取多个地名时，用不同 tag 错开）
     */
    public String pick(String kind, DeterministicRandom rng, long seq, String tag) {
        List<String> list = pools.get(kind);
        if (list == null) {
            throw new IllegalArgumentException("没有这种地名池：" + kind
                    + "（现有：" + kinds() + "）");
        }
        int index = rng.nextInt(seq, "place#" + kind + "#" + tag, list.size());
        return list.get(index);
    }

    /** 从 `places.json` 读（根是 `{ "capital": [...], "town": [...], … }`）。 */
    public static PlaceNames fromNode(StateNode root) {
        Map<String, List<String>> pools = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, StateNode> e : root.asObj("places.json").fields().entrySet()) {
            List<StateNode> items = e.getValue()
                    .asArr("places.json." + e.getKey()).items();
            List<String> names = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                names.add(items.get(i).asString(
                        StateNode.index("places.json." + e.getKey(), i)));
            }
            pools.put(e.getKey(), names);
        }
        return new PlaceNames(pools);
    }
}
