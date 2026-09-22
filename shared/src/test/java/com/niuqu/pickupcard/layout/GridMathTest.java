package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网格退让这件事的"已知答案"。
 * <p>
 * 【为什么专钉画布尺寸而不是 guiScale】退让的输入是"视口多宽 + u 多大"，guiScale 只是把这两个量
 * 一起改掉的开关。按 guiScale 钉的话，将来 {@code Units} 的参考高或下限一动，测试会以
 * <b>"数字变了"</b>的形态红；按画布尺寸钉，同样的改动会以 <b>"语义变了"</b>的形态红 ——
 * 后者才指得回真正变了的那件事。
 * <p>
 * 【为什么要连续扫】{@link StackLayoutTest} 那边是逐档钉的（档位少、语义单一）。这里多一层：
 * 列数是<b>阶梯函数</b>，逐档钉只能证"这几点是对的"，扫一遍才能证"阶梯本身没有反向的那一级"。
 */
class GridMathTest {

    private static final float EPS = 0.001f;

    /**
     * token 的设计单位值（u 之前）。<b>提议值</b> —— 落地后由 Trellis 的
     * {@code Tokens.Size.CELL_H} 接管，这里的 90 是"格子的高"在 u 单位下的提议。
     */
    private static final float CELL_H_UNITS = 90f;
    private static final float GAP_UNITS = 2f;
    private static final float PADDING_UNITS = 3f;

    /** 一档画布：视口宽（逻辑 px）、u、期望列数、期望每格宽、这一档是不是被下限钳住的。 */
    private record Tier(float viewportWidth, float u, int cols, float cellWidth, boolean clamped) {
    }

    /**
     * 五档真实画布。前四档对应 harness 的 {@code -PharnessGuiScale=1..4}，第五档是最小常见画布
     * （1280×720 @ guiScale 5），用来钉"钳到下限"那一条。
     */
    private static final Tier[] TIERS = {
            new Tier(1280.0f, 2.0f, 4, 314f, false),      // guiScale 1
            new Tier(640.0f, 1.8f, 3, 207f, false),       // guiScale 2
            new Tier(426.7f, 1.5f, 3, 137f, false),       // guiScale 3
            new Tier(320.0f, 1.5f, 2, 154f, false),       // guiScale 4
            new Tier(256.0f, 1.5f, 2, 122f, true),        // guiScale 5：floor 只给 1 列，被夹到下限 2
    };

    private static GridMath.Spec spec(float u) {
        return GridMath.Spec.of(CELL_H_UNITS * u, GAP_UNITS * u, PADDING_UNITS * u);
    }

    @Nested
    @DisplayName("退让阶梯")
    class Retreat {

        @Test
        @DisplayName("五档真实画布逐档对：1280/640/426.7/320/256 宽 → 4/3/3/2/2 列")
        void theFiveRealCanvasTiers() {
            for (Tier t : TIERS) {
                GridMath.Metrics m = GridMath.solve(24, t.viewportWidth(), spec(t.u()));
                assertEquals(t.cols(), m.cols(),
                        "画布 " + t.viewportWidth() + " 宽（u=" + t.u() + "）的列数不对");
                assertEquals(t.cellWidth(), m.cellWidth(), EPS,
                        "画布 " + t.viewportWidth() + " 宽（u=" + t.u() + "）的每格宽不对");
            }
        }

        @Test
        @DisplayName("退让只在必要时发生：没钳到下限的档，每格宽都不小于格高")
        void retreatsOnlyWhenItMust() {
            for (Tier t : TIERS) {
                if (t.clamped()) {
                    continue;
                }
                GridMath.Metrics m = GridMath.solve(24, t.viewportWidth(), spec(t.u()));
                assertTrue(m.cellWidth() >= m.cellHeight(),
                        "画布 " + t.viewportWidth() + " 宽：还有 " + m.cols() + " 列可选，"
                                + "每格却已经被压到 " + m.cellWidth() + " 宽（低于格高 "
                                + m.cellHeight() + "）—— 该少一列，不该缩格子");
            }
        }

        @Test
        @DisplayName("钳到下限那一档：宁可溢出，也不掉到 1 列")
        void clampsAtTheFloorRatherThanDroppingToASingleColumn() {
            GridMath.Metrics m = GridMath.solve(24, 256f, spec(1.5f));
            assertEquals(GridMath.MIN_COLS, m.cols(), "窄到下限时必须夹住，不能只给 1 列（那就不是网格了）");
            assertTrue(m.cellWidth() < m.cellHeight(),
                    "这一档就是被钳住的形状：格子确实比格高窄 —— 这是刻意的，不是 bug");
        }

        @Test
        @DisplayName("连续扫：画布变宽，列数绝不会变少（阶梯没有反向的那一级）")
        void columnsNeverDecreaseAsTheCanvasGrows() {
            int previous = -1;
            for (float w = 150f; w <= 1280f; w += 0.5f) {
                int cols = GridMath.columnsFor(w, spec(1.5f));
                assertTrue(cols >= previous,
                        "画布宽 " + w + " 时列数是 " + cols + "，比更窄时的 " + previous + " 还少");
                previous = cols;
            }
        }

        @Test
        @DisplayName("列数永远落在 [MIN_COLS, MAX_COLS] 里，两端都碰得到")
        void columnsStayInsideTheDeclaredRange() {
            boolean sawMin = false;
            boolean sawMax = false;
            for (float w = 1f; w <= 4096f; w += 1f) {
                int cols = GridMath.columnsFor(w, spec(1.5f));
                assertTrue(cols >= GridMath.MIN_COLS && cols <= GridMath.MAX_COLS,
                        "画布宽 " + w + " 给出了越界的列数 " + cols);
                sawMin |= cols == GridMath.MIN_COLS;
                sawMax |= cols == GridMath.MAX_COLS;
            }
            assertTrue(sawMin, "扫过的宽度里一次都没碰到下限 —— 那下限就没被验到");
            assertTrue(sawMax, "扫过的宽度里一次都没碰到上限 —— 那上限就没被验到");
        }
    }

    @Nested
    @DisplayName("格子宽与内容宽")
    class Widths {

        @Test
        @DisplayName("整行永不溢出：每格宽 × 列数 + 列缝 ≤ 内容宽")
        void aRowNeverOverflows() {
            for (float w = 150f; w <= 1280f; w += 0.5f) {
                for (float u : new float[]{1.5f, 1.8f, 2.0f}) {
                    GridMath.Spec s = spec(u);
                    GridMath.Metrics m = GridMath.solve(24, w, s);
                    float used = m.cellWidth() * m.cols() + s.gap() * (m.cols() - 1);
                    assertTrue(used <= m.contentWidth() + EPS,
                            "画布 " + w + " 宽（u=" + u + "）：一行占了 " + used
                                    + "，超过内容宽 " + m.contentWidth());
                }
            }
        }

        @Test
        @DisplayName("每格宽是「放得下的最大整数」：再加 1px 就溢出")
        void cellWidthIsTheLargestIntegerThatFits() {
            for (float w = 150f; w <= 1280f; w += 0.5f) {
                GridMath.Spec s = spec(1.5f);
                GridMath.Metrics m = GridMath.solve(24, w, s);
                if (m.cellWidth() <= 0f) {
                    continue;
                }
                float wider = (m.cellWidth() + 1f) * m.cols() + s.gap() * (m.cols() - 1);
                assertTrue(wider > m.contentWidth() - EPS,
                        "画布 " + w + " 宽：每格 " + m.cellWidth() + " 宽，再加 1px（" + wider
                                + "）仍然装得进 " + m.contentWidth() + " —— 说明算小了");
            }
        }

        @Test
        @DisplayName("内容宽 = 视口宽 − 2×左右内边距（内边距只减一次）")
        void contentWidthRemovesThePaddingExactlyOnce() {
            GridMath.Spec s = spec(1.5f);
            GridMath.Metrics m = GridMath.solve(24, 426.7f, s);
            assertEquals(426.7f - 2f * s.padding(), m.contentWidth(), EPS);
            assertEquals(s.padding(), m.padding(), EPS);
        }

        @Test
        @DisplayName("差不到 1e-4 就该认成整除：floor 的容差真的在起作用")
        void floorDoesNotLoseAPixelWhenTheDivisionIsJustUnderAnInteger() {
            // 【为什么不用 400 / 4】那在 IEEE754 里是精确的 100.0，把 GridMath 的
            // FLOOR_EPSILON 整条删掉它照样绿 —— 是个恒真断言（评审指出的）。
            // 【为什么也不用 299.99999f】它离 300 只有 1e-5，**小于 float 在 300 附近的
            // 半个间距（约 1.5e-5）**，于是直接舍入成 300.0f —— 又变成恒真。
            // （本喵第一版就是这么写错的，靠一次变异测试才发现。）
            // 要用 299.9999f：1e-4 大于半间距，会被舍到 300 − 3.05e-5 = 299.99997，
            // 于是 299.99997 / 3 = 99.9999898 —— 不加容差 floor 成 99（整行少 3px），
            // 而且列数那一趟也会掉到 2。加了容差才是 3 列各 100。
            GridMath.Spec s = new GridMath.Spec(100f, 0f, 0f, 2, 4);
            GridMath.Metrics m = GridMath.solve(6, 299.9999f, s);
            assertEquals(3, m.cols(), "这一档应当解出 3 列（不带容差会掉成 2 列）");
            assertEquals(100f, m.cellWidth(), EPS,
                    "99.9999898 该被容差认成整除（100），而不是掉成 99");
        }
    }

    @Nested
    @DisplayName("行数与内容高")
    class Rows {

        @Test
        @DisplayName("行数 = ceil(项目数 / 列数)，末行不足也算一行")
        void rowCountRoundsUp() {
            GridMath.Spec s = spec(2.0f);           // 1280 宽 → 4 列
            assertEquals(0, GridMath.solve(0, 1280f, s).rows());
            assertEquals(1, GridMath.solve(1, 1280f, s).rows());
            assertEquals(1, GridMath.solve(4, 1280f, s).rows());
            assertEquals(2, GridMath.solve(5, 1280f, s).rows(), "第 5 个要开第二行");
            assertEquals(2, GridMath.solve(8, 1280f, s).rows());
            assertEquals(3, GridMath.solve(9, 1280f, s).rows());
            assertEquals(6, GridMath.solve(24, 1280f, s).rows());
        }

        @Test
        @DisplayName("内容高把行缝算进去，末行之后不留缝")
        void contentHeightIncludesGapsButNotAfterTheLastRow() {
            GridMath.Spec s = spec(2.0f);
            GridMath.Metrics one = GridMath.solve(4, 1280f, s);
            assertEquals(s.cellHeight(), one.contentHeight(), EPS, "一行时内容高就是格高");

            GridMath.Metrics two = GridMath.solve(8, 1280f, s);
            assertEquals(2f * s.cellHeight() + s.gap(), two.contentHeight(), EPS,
                    "两行 = 两个格高 + 一条缝（末行之后不留缝）");
        }

        @Test
        @DisplayName("没有项目时不出行、内容高为 0，但列数照样算得出来")
        void emptyGridStillReportsItsColumnCount() {
            GridMath.Metrics m = GridMath.solve(0, 1280f, spec(2.0f));
            assertTrue(m.isEmpty());
            assertEquals(0, m.rows());
            assertEquals(0f, m.contentHeight(), EPS);
            assertEquals(4, m.cols(), "列数是「这个宽度放得下几列」，与有没有项目无关");
        }

        @Test
        @DisplayName("负数项目数按空处理，不炸也不出行")
        void negativeItemCountIsTreatedAsEmpty() {
            GridMath.Metrics m = GridMath.solve(-3, 1280f, spec(2.0f));
            assertTrue(m.isEmpty());
            assertFalse(m.rows() < 0, "行数不能是负的");
        }
    }

    @Nested
    @DisplayName("Spec 校验：错的口径当场抛，不静默算下去")
    class SpecValidation {

        @Test
        @DisplayName("格高必须为正，缝与内边距不能为负")
        void rejectsNonsenseNumbers() {
            assertThrows(IllegalArgumentException.class, () -> GridMath.Spec.of(0f, 2f, 3f));
            assertThrows(IllegalArgumentException.class, () -> GridMath.Spec.of(-1f, 2f, 3f));
            assertThrows(IllegalArgumentException.class, () -> GridMath.Spec.of(90f, -1f, 3f));
            assertThrows(IllegalArgumentException.class, () -> GridMath.Spec.of(90f, 2f, -1f));
        }

        @Test
        @DisplayName("列数区间不合法当场抛（下限 < 1、上限 < 下限）")
        void rejectsNonsenseColumnRanges() {
            assertThrows(IllegalArgumentException.class, () -> new GridMath.Spec(90f, 2f, 3f, 0, 4));
            assertThrows(IllegalArgumentException.class, () -> new GridMath.Spec(90f, 2f, 3f, 3, 2));
        }

        @Test
        @DisplayName("视口窄到装不下任何一列时，给下限列数并且每格宽不为负")
        void degenerateViewportDoesNotProduceNegativeWidths() {
            GridMath.Metrics m = GridMath.solve(10, 4f, spec(1.5f));
            assertEquals(GridMath.MIN_COLS, m.cols());
            assertEquals(0f, m.cellWidth(), EPS, "格子宽不能是负的");
            assertTrue(m.contentWidth() >= 0f, "内容宽不能是负的");
        }
    }
}
