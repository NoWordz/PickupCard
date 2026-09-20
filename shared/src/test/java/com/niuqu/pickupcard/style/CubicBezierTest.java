package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 贝塞尔缓动。参考值不是"我觉得"，是拿 cubic-bezier(.22,.9,.28,1) 的定义式
 * 反解 x(t) 后算出来的 y —— 也就是浏览器会给出的那个数。
 */
class CubicBezierTest {

    /** 参考设计里 .slot 的那条曲线。 */
    private static final CubicBezier SLOT = CubicBezier.SLOT;

    @Test
    void matchesTheBrowsersNumbers() {
        assertEquals(0f, SLOT.at(0f), 1e-6f);
        assertEquals(1f, SLOT.at(1f), 1e-6f);
        assertEquals(0.757441f, SLOT.at(0.25f), 0.002f, "t=0.25");
        assertEquals(0.951547f, SLOT.at(0.5f), 0.002f, "t=0.5");
        assertEquals(0.993334f, SLOT.at(0.75f), 0.002f, "t=0.75");
    }

    @Test
    void isMonotonicAndNeverOvershoots() {
        float previous = -1f;
        for (int i = 0; i <= 100; i++) {
            float v = SLOT.at(i / 100f);
            assertTrue(v >= previous, "单调递增，t=" + i);
            assertTrue(v <= 1.0001f, "参考设计这条曲线不过冲，t=" + i + " v=" + v);
            previous = v;
        }
        assertFalse(SLOT.overshoots());
    }

    /** 过冲的曲线要能被识别出来 —— 之前用 easeOutBack 做位移就是"没人注意到它在过冲"。 */
    @Test
    void detectsOvershoot() {
        assertTrue(new CubicBezier(0.34f, 1.56f, 0.64f, 1f).overshoots());
    }

    /** 起点极快、收尾极慢，这是这条曲线的形状特征（t=0.25 就到 0.7 以上）。 */
    @Test
    void isAFastOutSlowInCurve() {
        assertTrue(SLOT.at(0.25f) > 0.7f, "t=0.25 应该已经走了七成以上");
    }
}
