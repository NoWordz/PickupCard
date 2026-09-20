package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卡面三框几何的钉子。
 *
 * <p>【为什么值得单测】镜像这套坐标在外壳与内容两条路上各用过一遍，而它们曾经不一致 ——
 * 症状是"外壳从竖条那侧滑出、文字从反方向冒出来"，稳态截图完全看不出来，只有动画途中才现形
 * （2026-09-20 用户报）。这里把"静止时框在哪"钉死；"动起来往哪走"由调用方一律
 * {@code + shift} 保证（方向在 shift 里，见 {@code CardStage} 那条链）。
 */
class BodyGeometryTest {

    private static final float BAR = 4f;
    private static final float GAP = 3f;
    private static final float CARD_H = 22f;
    private static final float CARD_W = 120f;

    @Test
    @DisplayName("常规：竖条在左，图标格紧跟着竖条，信息框占剩下的宽")
    void plainLayout() {
        BodyGeometry g = BodyGeometry.of(CARD_W, CARD_H, BAR, GAP, false);
        assertEquals(BAR + GAP, g.iconLeft(), 1e-6, "图标格贴着竖条右侧");
        assertEquals(BAR + GAP, g.bodyLeft(), 1e-6);
        assertEquals(BAR + GAP + CARD_H + GAP, g.infoLeft(), 1e-6, "信息框在图标格右边");
        assertEquals(CARD_W - BAR - GAP, g.bodyWidth(), 1e-6);
        assertEquals(CARD_W - BAR - GAP - CARD_H - GAP, g.infoWidth(), 1e-6);
    }

    @Test
    @DisplayName("镜像：竖条在右，图标格紧贴竖条左侧，信息框从卡左缘起")
    void mirroredLayout() {
        BodyGeometry g = BodyGeometry.of(CARD_W, CARD_H, BAR, GAP, true);
        assertEquals(0f, g.infoLeft(), 1e-6, "镜像时信息框从卡左缘起");
        assertEquals(0f, g.bodyLeft(), 1e-6);
        assertEquals(CARD_W - BAR - GAP - CARD_H, g.iconLeft(), 1e-6,
                "图标格右缘正好落在内容区右缘（也就是竖条左侧）");
        assertEquals(CARD_W - BAR - GAP, g.bodyWidth(), 1e-6);
        assertEquals(CARD_W - BAR - GAP - CARD_H - GAP, g.infoWidth(), 1e-6);
    }

    @Test
    @DisplayName("镜像就是左右翻转：图标格是它自己的镜像，信息框也是")
    void mirrorIsAFlipAboutTheCard() {
        BodyGeometry plain = BodyGeometry.of(CARD_W, CARD_H, BAR, GAP, false);
        BodyGeometry mirror = BodyGeometry.of(CARD_W, CARD_H, BAR, GAP, true);
        assertEquals(CARD_W - (plain.iconLeft() + CARD_H), mirror.iconLeft(), 1e-6,
                "图标格镜像后仍在竖条旁边 —— 这条一破，图标就会跑到卡片另一头");
        assertEquals(CARD_W - (plain.infoLeft() + plain.infoWidth()), mirror.infoLeft(), 1e-6,
                "信息框镜像后仍贴另一条边");
        assertEquals(plain.bodyWidth(), mirror.bodyWidth(), 1e-6);
        assertEquals(plain.infoWidth(), mirror.infoWidth(), 1e-6, "翻转不改变任何宽度");
    }

    @Test
    @DisplayName("卡太窄时不出现负宽：宽度一律夹到 0 以上")
    void degenerateCardStaysNonNegative() {
        BodyGeometry tiny = BodyGeometry.of(6f, CARD_H, BAR, GAP, false);
        assertTrue(tiny.bodyWidth() >= 0f);
        assertTrue(tiny.infoWidth() >= 0f);
        BodyGeometry tinyMirror = BodyGeometry.of(6f, CARD_H, BAR, GAP, true);
        assertTrue(tinyMirror.iconLeft() >= 0f, "内容区装不下图标格时，格子停在内容区左缘而不是负数");
        assertTrue(tinyMirror.infoWidth() >= 0f);
    }
}
