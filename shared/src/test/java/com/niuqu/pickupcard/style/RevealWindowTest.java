package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 隧道口的钉子。
 *
 * <p>【为什么值得单测】这条几何曾经在两条绘制路径上各算了一遍：一条把窗口左边当成卡片
 * 左缘（0）、另一条根本没有窗口。结果是内容"从左往右穿过竖条"——而它是渲染路径上的东西，
 * 出了真机截图才看得见。所以把"窗口在哪"从渲染里搬出来，用已知答案钉住。
 */
class RevealWindowTest {

    private static final float BAR = 4f;
    private static final float GAP = 3f;
    private static final float CARD = 90f;

    @Test
    @DisplayName("窗口左边是竖条宽 + 间隙，不是卡片左缘")
    void windowStartsAfterTheBar() {
        var w = RevealWindow.of(BAR, GAP, CARD, false, 1f);
        assertEquals(BAR + GAP, w.left(), 1e-6, "窗口从竖条右侧起，内容才会从竖条后面冒出来");
        assertTrue(w.left() > 0f, "0 = 内容会画到竖条上面（穿透）");
    }

    @Test
    @DisplayName("火车档：窗口全程是内容自然宽，内容自己在里面平移")
    void slideKeepsFullWidth() {
        float full = RevealWindow.contentWidth(CARD, BAR, GAP);
        for (float rise : new float[]{0f, 0.5f, 1f}) {
            assertEquals(full, RevealWindow.of(BAR, GAP, CARD, false, rise).width(), 1e-6);
        }
        assertEquals(CARD, RevealWindow.of(BAR, GAP, CARD, false, 0.5f).right(), 1e-6);
    }

    @Test
    @DisplayName("拉幕档：窗口从 0 长到内容自然宽")
    void clipGrowsWithRise() {
        float full = RevealWindow.contentWidth(CARD, BAR, GAP);
        assertEquals(0f, RevealWindow.of(BAR, GAP, CARD, true, 0f).width(), 1e-6);
        assertEquals(full * 0.25f, RevealWindow.of(BAR, GAP, CARD, true, 0.25f).width(), 1e-6);
        assertEquals(full, RevealWindow.of(BAR, GAP, CARD, true, 1f).width(), 1e-6);
    }

    @Test
    @DisplayName("窗口永远不会伸到卡片外面去")
    void windowNeverExceedsTheCard() {
        for (boolean clip : new boolean[]{false, true}) {
            for (float rise : new float[]{0f, 0.33f, 1f}) {
                var w = RevealWindow.of(BAR, GAP, CARD, clip, rise);
                assertTrue(w.left() >= 0f && w.right() <= CARD + 1e-6f,
                        "窗口越界会把内容画到隔壁去：" + w);
            }
        }
    }

    @Test
    @DisplayName("窗口和竖条不重叠 —— 内容再往外滑也碰不到竖条")
    void windowNeverOverlapsTheBar() {
        for (boolean clip : new boolean[]{false, true}) {
            for (float rise : new float[]{0f, 0.5f, 1f}) {
                var w = RevealWindow.of(BAR, GAP, CARD, clip, rise);
                assertTrue(w.left() >= BAR,
                        "窗口左边压到竖条上了：内容会从竖条前面滑过去（「穿透」）" + w);
            }
        }
    }

    // ------------------------------------------------------------------
    // 镜像卡片（2026-09-20）：竖条在右缘，窗口贴竖条左缘、从右往左长
    // ------------------------------------------------------------------

    @Test
    @DisplayName("镜像：窗口右缘贴着竖条左侧，全宽时左缘抵到卡左缘")
    void mirroredWindowHugsTheBarOnTheRight() {
        float full = RevealWindow.contentWidth(CARD, BAR, GAP);
        var w = RevealWindow.of(BAR, GAP, CARD, false, 1f, true);
        assertEquals(CARD - (BAR + GAP), w.right(), 1e-6, "锚定边是窗口右缘（贴竖条）");
        assertEquals(CARD - (BAR + GAP) - full, w.left(), 1e-6, "全宽时窗口一直伸到卡左缘");
    }

    @Test
    @DisplayName("镜像拉幕：从竖条侧往左展开，锚定边不动")
    void mirroredClipGrowsLeftward() {
        float full = RevealWindow.contentWidth(CARD, BAR, GAP);
        for (float rise : new float[]{0f, 0.25f, 0.5f, 1f}) {
            var w = RevealWindow.of(BAR, GAP, CARD, true, rise, true);
            assertEquals(full * rise, w.width(), 1e-6, "宽度与非镜像同公式");
            assertEquals(CARD - (BAR + GAP), w.right(), 1e-6, "锚定边（右缘）不许动");
        }
        var closed = RevealWindow.of(BAR, GAP, CARD, true, 0f, true);
        assertEquals(0f, closed.width(), 1e-6, "rise=0 全挡住");
    }

    @Test
    @DisplayName("镜像窗口同样不越界、不压竖条")
    void mirroredWindowStaysInsideAndOffTheBar() {
        for (boolean clip : new boolean[]{false, true}) {
            for (float rise : new float[]{0f, 0.33f, 1f}) {
                var w = RevealWindow.of(BAR, GAP, CARD, clip, rise, true);
                assertTrue(w.left() >= 0f && w.right() <= CARD + 1e-6f, "越界：" + w);
                assertTrue(w.right() <= CARD - BAR + 1e-6f,
                        "窗口右边压到竖条（镜像时竖条在右缘）：" + w);
            }
        }
    }
}
