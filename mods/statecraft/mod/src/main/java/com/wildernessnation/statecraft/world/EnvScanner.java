package com.wildernessnation.statecraft.world;

import com.wildernessnation.statecraft.core.env.EnvironmentSnapshot;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 真实环境读数（`NATIONS.md` §10.5）：把世界读成
 * {@link EnvironmentSnapshot} 的那**四个数**，一个不多。
 *
 * <p>判定的阈值全在 core（`env/EnvironmentRules`），这里只负责"数格子"。
 * 刻意**不做形状检测**——§10.5 写明了为什么（挪一块砖就失效、跨区块误判、性能、
 * 与"地标手建"的自由冲突）。所以这里也**不**去认"这是不是一个情报站"，
 * 只回答"这个空间够不够像个屋子"。
 *
 * <h2>四个读数怎么算</h2>
 *
 * <ul>
 *   <li><b>hasRoof</b>：从锚点上方起，在高度范围内**有没有碰得到的东西**（有碰撞体就算）。</li>
 *   <li><b>volume</b>：从锚点上方那格开始**泛洪**（只走没有碰撞体的格子），
 *       数出连通的内部空间有多少格。</li>
 *   <li><b>enclosurePercent</b>：泛洪区域的**边界**里，多少面是"墙"、多少面是"外面"。
 *       出界也算"外面"——所以一个敞开的平台封闭度会很低。</li>
 *   <li><b>insideClaim</b>：交给 {@link ClaimProbe}（领地是另一个 mod 的事）。</li>
 * </ul>
 *
 * <p><b>有界</b>：泛洪的格子数有上限（{@link #MAX_CELLS}），撞到上限就停。
 * 一个没有墙的巨大空间（比如露天平原）会让泛洪跑满上限——那正是"封闭度极低"的情形，
 * 停在哪儿都不影响判定结论，但能保证这个函数不会在任意位置吃掉几百毫秒。
 */
public final class EnvScanner {

    /** 水平扫描半径（格）。§10.5 只说"内部体积 ≥12 格"，没给尺寸，取一个够装下小屋的值。 */
    public static final int RADIUS = 8;

    /** 垂直扫描高度（格）——从锚点上方算起。 */
    public static final int HEIGHT = 6;

    /** 泛洪上限，防"露天空间跑满整张图"。 */
    public static final int MAX_CELLS = 4096;

    private EnvScanner() {}

    /** 维度内扫描（不查领地：给"只看环境"的调试用）。 */
    public static EnvironmentSnapshot scan(ServerLevel level, BlockPos anchor) {
        return scan(level, anchor, ClaimProbe.fixed(false));
    }

    public static EnvironmentSnapshot scan(ServerLevel level, BlockPos anchor, ClaimProbe claims) {
        if (level == null || anchor == null) {
            throw new IllegalArgumentException("level 与 anchor 都不能为 null");
        }
        if (claims == null) {
            throw new IllegalArgumentException("claims 不能为 null（不确定就用 ClaimProbe.fixed(..)）");
        }

        // 内侧从锚点本身开始：锚点常常是"地板上的一个方块"（讲台、制图台），
        // 它上面那格才是屋里；但锚点也可能放在地上、人在它旁边——两条都试一次。
        BlockPos start = firstOpen(level, anchor);
        if (start == null) {
            return new EnvironmentSnapshot(hasRoof(level, anchor),
                    0.0, 0, claims.insideClaim(level, anchor));
        }

        Set<BlockPos> inside = flood(level, start);
        int solid = 0;
        int open = 0;
        for (BlockPos pos : inside) {
            for (Direction d : Direction.values()) {
                BlockPos n = d.step(pos);
                if (inside.contains(n)) {
                    continue;
                }
                if (outOfBounds(anchor, n) || isOpen(level, n)) {
                    open++;
                } else {
                    solid++;
                }
            }
        }
        double enclosure = solid + open == 0 ? 0.0 : solid * 100.0 / (solid + open);
        return new EnvironmentSnapshot(
                hasRoof(level, anchor), enclosure, inside.size(), claims.insideClaim(level, anchor));
    }

    /** 六个方向（刻意不用 mc 的 Direction，免得把它的 DOWN/UP 顺序当成语义）。 */
    private enum Direction {
        UP(0, 1, 0), DOWN(0, -1, 0),
        NORTH(0, 0, -1), SOUTH(0, 0, 1),
        WEST(-1, 0, 0), EAST(1, 0, 0);

        private final int dx;
        private final int dy;
        private final int dz;

        Direction(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        BlockPos step(BlockPos p) {
            return p.offset(dx, dy, dz);
        }
    }

    /** 锚点自己或它上面那格，谁先没有碰撞体就用谁（都堵着就是"没内部空间"）。 */
    private static BlockPos firstOpen(ServerLevel level, BlockPos anchor) {
        if (isOpen(level, anchor)) {
            return anchor;
        }
        BlockPos above = anchor.above();
        return isOpen(level, above) ? above : null;
    }

    /**
     * 从起点泛洪，只走"没有碰撞体"的格子（空气、草、火把都算内部）。
     *
     * <p>用碰撞体而不是"是不是空气"：§10.5 关心的是"人能不能站在里头"，
     * 而火把/告示牌/地毯这些东西不挡人，把它们当成墙会让一个装修好的房间算不出体积。
     */
    private static Set<BlockPos> flood(ServerLevel level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < MAX_CELLS) {
            BlockPos pos = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = d.step(pos);
                if (seen.contains(n)) {
                    continue;
                }
                if (level.isOutsideBuildHeight(n)) {
                    continue;
                }
                if (!isOpen(level, n)) {
                    continue;
                }
                seen.add(n);
                if (seen.size() >= MAX_CELLS) {
                    break;
                }
                queue.add(n);
            }
        }
        return seen;
    }

    /** 界内判据：以锚点为心的一段柱体（水平 ±{@link #RADIUS}，向上 {@link #HEIGHT} 格）。 */
    private static boolean outOfBounds(BlockPos anchor, BlockPos pos) {
        int dy = pos.getY() - anchor.getY();
        return Math.abs(pos.getX() - anchor.getX()) > RADIUS
                || Math.abs(pos.getZ() - anchor.getZ()) > RADIUS
                || dy < -1 || dy > HEIGHT;
    }

    private static boolean hasRoof(ServerLevel level, BlockPos anchor) {
        for (int dy = 2; dy <= HEIGHT + 2; dy++) {
            BlockPos p = anchor.above(dy);
            if (level.isOutsideBuildHeight(p)) {
                return false;
            }
            if (!isOpen(level, p)) {
                return true;
            }
        }
        return false;
    }

    /** 没有碰撞体 = 能站人 / 能走 = "内部"。 */
    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(level, pos);
        return shape.isEmpty();
    }
}
