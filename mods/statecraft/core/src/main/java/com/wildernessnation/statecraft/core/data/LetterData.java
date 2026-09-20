package com.wildernessnation.statecraft.core.data;

import com.wildernessnation.statecraft.core.letter.LetterConfig;
import com.wildernessnation.statecraft.core.letter.LetterKind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `letters.json` 读出来的东西：三种国书的定义 + 国书数值配置。
 *
 * <p>和 {@link NationData} 同一个套路：**文件里没写的字段一律退回代码里的默认值**，
 * 于是"默认值"只有一个来源，不会出现数据文件与代码两处默认值互相漂移。
 *
 * @param kinds  按 {@link LetterKind} 索引的定义表（三种必须齐全，缺一种就是内容没配全）
 * @param config 国书数值配置（期限、承诺期、各项态度增减）
 */
public record LetterData(Map<LetterKind, LetterKindData> kinds, LetterConfig config) {

    public LetterData {
        if (kinds == null) {
            throw new IllegalArgumentException("LetterData.kinds 不能为 null");
        }
        kinds = Map.copyOf(kinds);
        for (LetterKind k : LetterKind.values()) {
            if (!kinds.containsKey(k)) {
                throw new IllegalArgumentException("letters.json 缺少这种国书的定义：" + k);
            }
        }
        if (config == null) {
            throw new IllegalArgumentException("LetterData.config 不能为 null");
        }
    }

    public LetterKindData of(LetterKind kind) {
        LetterKindData d = kinds.get(kind);
        if (d == null) {
            throw new IllegalArgumentException("没有这种国书的定义：" + kind);
        }
        return d;
    }

    /** 按枚举声明顺序列出（遍历顺序必须确定，否则同一份数据能读出两种结果）。 */
    public List<LetterKindData> ordered() {
        Map<LetterKind, LetterKindData> byKind = new LinkedHashMap<>();
        for (LetterKind k : LetterKind.values()) {
            byKind.put(k, of(k));
        }
        return List.copyOf(byKind.values());
    }
}
