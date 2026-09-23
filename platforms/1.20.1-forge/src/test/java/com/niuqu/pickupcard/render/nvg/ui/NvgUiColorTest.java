package com.niuqu.pickupcard.render.nvg.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link NvgUi} 的纯色函数（{@code fade} / {@code mix}）—— 离线可测，不碰 GL。
 *
 * <p>【为什么从这里分出来】这几条原在 {@code TweenTest} 里，但测的是 {@code NvgUi} 的颜色，
 * 与 {@code Tween} 没关系。A-30 把 {@code Tween} 收敛进框架的 {@code Animated}（删掉那个类），
 * 于是把它们挪到自己的文件 —— 免得跟着一起没了。
 */
class NvgUiColorTest {

    @Test
    @DisplayName("颜色的淡出与插值：只动 alpha、端点精确")
    void fadeAndMix() {
        assertEquals(0xFF112233, NvgUi.fade(0xFF112233, 1f), "不透明时原样返回");
        assertEquals(0x80112233, NvgUi.fade(0xFF112233, 0.5f), "alpha 按比例乘（255×0.5≈128）");
        assertEquals(0x00112233, NvgUi.fade(0xFF112233, 0f), "透明度 0 = 全透明，RGB 不动");
        assertEquals(0x40112233, NvgUi.fade(0x80112233, 0.5f), "已经半透的再乘一半");

        assertEquals(0xFF000000, NvgUi.mix(0xFF000000, 0xFFFFFFFF, 0f), "t=0 就是起点色");
        assertEquals(0xFFFFFFFF, NvgUi.mix(0xFF000000, 0xFFFFFFFF, 1f), "t=1 就是终点色");
        assertEquals(0xFF808080, NvgUi.mix(0xFF000000, 0xFFFFFFFF, 0.5f), "中点是中灰");
        assertEquals(0xFF00FF00, NvgUi.mix(0xFFFF0000, 0xFF00FF00, 1f), "越界的 t 要夹住");
    }
}
