package com.wildernessnation.statecraft.core.rng;

/**
 * 可复现随机：所有取值都来自 hash(seed, seq, tag)，不使用可变状态。
 *
 * <p>为什么不用 {@link java.util.Random}：它是有状态的流，调用顺序一变结果就全变，
 * 于是"同 seed 复现""崩溃后重放""单测固定断言"都做不了。这里把随机变成<strong>纯函数</strong>：
 * 只要 (seed, 结算序号, 用途标签) 相同，任何机器任何时刻都得到同一个值。
 */
public final class DeterministicRandom {

    private final long seed;

    public DeterministicRandom(long seed) {
        this.seed = seed;
    }

    /** SplitMix64 的混合步：雪崩性好、无依赖、跨 JVM 稳定。 */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 用途标签的稳定哈希（不用 String.hashCode，避免受 JVM 实现影响）。 */
    private static long mixTag(long h, String tag) {
        for (int i = 0; i < tag.length(); i++) {
            h = mix(h ^ (tag.charAt(i) * 0x100000001B3L));
        }
        return h;
    }

    public long hash(long seq, String tag) {
        return mixTag(mix(seed ^ (seq * 0x9E3779B97F4A7C15L)), tag);
    }

    /** [0,1) 的双精度数（取高 53 位，与 double 的尾数精度一致）。 */
    public double nextDouble(long seq, String tag) {
        return (hash(seq, tag) >>> 11) * 0x1.0p-53;
    }

    /** [0, boundExclusive) 的整数。 */
    public int nextInt(long seq, String tag, int boundExclusive) {
        if (boundExclusive <= 0) {
            throw new IllegalArgumentException("bound 必须 > 0，实际 " + boundExclusive);
        }
        return (int) Math.floorMod(hash(seq, tag), boundExclusive);
    }

    /** [minInclusive, maxInclusive] 的整数。 */
    public int range(long seq, String tag, int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException(
                    "max 不能小于 min：" + minInclusive + ".." + maxInclusive);
        }
        return minInclusive + nextInt(seq, tag, maxInclusive - minInclusive + 1);
    }

    /** [min, max] 的双精度数。 */
    public double rangeDouble(long seq, String tag, double min, double max) {
        return min + nextDouble(seq, tag) * (max - min);
    }
}
