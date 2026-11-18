package com.niuqu.pickupcard.render.nvg.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 列表滚动那点数学与视口。
 * <p>
 * 【为什么值得单独钉】滚动的坏法都不报错：滚到底露出空白、滚过头回不来、"看着在第 3 行、
 * 点下去选中第 4 行"。前两条在这里钉死，第三条靠"绘制与命中都走 {@code contentY}/rowAt"保证。
 * 全是纯函数，不必起游戏。
 */
class ScrollMathTest {

    @Test
    @DisplayName("内容比视口矮：不能滚，越界的偏移也要收回来")
    void noScrollWhenContentFits() {
        assertEquals(0f, ScrollMath.maxOffset(80f, 120f), 1e-4, "矮内容没有可滚距离");
        assertEquals(0f, ScrollMath.clamp(30f, 80f, 120f), 1e-4, "越界的偏移要收回来");
        assertEquals(0f, ScrollMath.maxOffset(120f, 120f), 1e-4, "正好一样高也不算能滚");
    }

    @Test
    @DisplayName("最大偏移 = 内容高 − 视口高：滚到底不会露出空白")
    void maxOffset() {
        assertEquals(200f, ScrollMath.maxOffset(500f, 300f), 1e-4);
        assertEquals(200f, ScrollMath.clamp(9_999f, 500f, 300f), 1e-4);
        assertEquals(0f, ScrollMath.clamp(-50f, 500f, 300f), 1e-4, "滚过头要收回来");
    }

    @Test
    @DisplayName("滚轮：一格三行；向下滚偏移变大，到头就不动")
    void wheel() {
        // 行高 20、一格 3 行、内容 500、视口 300（最大偏移 200）
        assertEquals(60f, ScrollMath.wheel(0f, -1, 20f, 3f, 500f, 300f), 1e-4, "向下滚一格 = 3 行");
        assertEquals(0f, ScrollMath.wheel(0f, 1, 20f, 3f, 500f, 300f), 1e-4, "向上滚已经在顶");
        assertEquals(200f, ScrollMath.wheel(200f, -1, 20f, 3f, 500f, 300f), 1e-4, "到底了不动");
    }

    @Test
    @DisplayName("视口内坐标 + 已滚距离 → 第几行")
    void rowAt() {
        assertEquals(0, ScrollMath.rowAt(5f, 0f, 20f));
        assertEquals(1, ScrollMath.rowAt(25f, 0f, 20f));
        assertEquals(2, ScrollMath.rowAt(5f, 40f, 20f), "滚了 40px，视口顶部就是第 2 行");
    }

    @Test
    @DisplayName("视口：绘制与命中减的是同一个偏移")
    void viewport() {
        NvgScroll scroll = new NvgScroll(10f, 20f, 100f, 60f);
        assertEquals(20f, scroll.contentY(0f), 1e-4, "没滚时行顶就是视口顶");
        assertEquals(0, scroll.rowAt(50, 25, 20f), "视口第一行（local 5）");
        assertEquals(1, scroll.rowAt(50, 45, 20f), "视口第二行（local 25）");
        assertEquals(-1, scroll.rowAt(5, 40, 20f), "x 在视口外面");
        assertEquals(-1, scroll.rowAt(50, 100, 20f), "y 在视口下面");
        assertTrue(scroll.scrollable(200f));
        assertFalse(scroll.scrollable(60f));

        assertTrue(scroll.wheel(-1, 200f, 20f), "向下滚一格应该真的动");
        assertEquals(60f, scroll.offset(), 1e-4);
        assertEquals(20f - 60f, scroll.contentY(0f), 1e-4, "行顶跟着偏移走");
        assertEquals(3, scroll.rowAt(50, 25, 20f), "滚了三行：同一屏幕位置现在指着第 3 行");
        scroll.reflow(60f);
        assertEquals(0f, scroll.offset(), 1e-4, "内容变矮之后 reflow 把偏移收回 0");
        assertFalse(scroll.wheel(-1, 60f, 20f), "装得下时滚轮什么都不该发生");
    }
}
