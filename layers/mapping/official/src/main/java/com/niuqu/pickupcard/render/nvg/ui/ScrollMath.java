package com.niuqu.pickupcard.render.nvg.ui;

/**
 * <b>⚠️ 现在只作对照侧：没有生产调用方了</b>（2026-09-22 / A-16 起）。
 * 偏移与夹取归了框架的 {@code dev.e33.trellis.ui.ScrollContainer}，本类与
 * {@link NvgScroll} 只剩 {@code ScrollMathTest} 在测。留着的理由见 {@link NvgScroll} 的注释
 * （它是当年滚轮口径唯一的成文证据）。
 *
 * <p>滚动那点数学。<b>纯函数，能离线单测</b>（{@code ScrollMathTest}）。
 * <p>
 * 【为什么单独一个类】列表要回答的其实只有三个问题：能挪多远、现在挪到哪、鼠标指着第几行。
 * 把这三点算清楚，界面那边就只剩"把 -offset 加到 y 上"。
 */
public final class ScrollMath {

    private ScrollMath() {
    }

    /** 能挪的最大距离；内容比视口矮时是 0 —— 不出现"能滚一点点"的鬼现象。 */
    public static float maxOffset(float contentHeight, float viewportHeight) {
        return Math.max(0f, contentHeight - viewportHeight);
    }

    /** 夹进 [0, maxOffset]：滚轮滚过头、内容变短之后越界，都在这里收回来。 */
    public static float clamp(float offset, float contentHeight, float viewportHeight) {
        return Math.max(0f, Math.min(offset, maxOffset(contentHeight, viewportHeight)));
    }

    /**
     * 滚轮：正值（向下滚）让内容往上走。
     * <p>
     * 【为什么步长按"行"而不是像素】行高随画布变，写死像素会让同一格滚轮在大画布上滚得少、
     * 小画布上滚得多。按行给，手感跨缩放档才一致。
     */
    public static float wheel(float offset, double delta, float rowHeight, float rowsPerNotch,
                              float contentHeight, float viewportHeight) {
        float moved = (float) (offset - delta * rowHeight * rowsPerNotch);
        return clamp(moved, contentHeight, viewportHeight);
    }

    /** 视口内坐标 + 已滚距离 -> 内容里的第几行。越界由调用方判（它知道一共几行）。 */
    public static int rowAt(float localY, float offset, float rowHeight) {
        if (rowHeight <= 0f) {
            return -1;
        }
        return (int) Math.floor((localY + offset) / rowHeight);
    }
}
