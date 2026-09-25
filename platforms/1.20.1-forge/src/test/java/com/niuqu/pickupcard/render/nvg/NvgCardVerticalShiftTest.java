package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.render.CardCanvas;
import com.niuqu.pickupcard.render.CardSlot;
import com.niuqu.pickupcard.render.CardView;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纵向位移单点出处 {@link NvgCardPainter#verticalShiftOf} 的账。
 * <p>
 * 【为什么值得钉】2026-09-20 镜像 bug 的教训：同一份几何写两遍必有一份错。verticalShiftOf
 * 之所以存在，就是让外壳（NanoVG）与内容（原版 pose）吃<b>同一个数</b> —— 本类钉的是这个数
 * 本身的语义：DROP 入场从锚线上方掉下来（负）、落地过冲带一记小弹（正）、FALL 退场加速下坠
 * （正）、别的档位一分都不给（恒 0）。接线两侧（谁调用它）钉不到 —— 那是渲染路径，离线起不来，
 * 由 harness 截图验证（计划 Task B4）。
 * <p>纯时间数学、不碰渲染，所以不必起游戏。fixture 走 {@code CardContent.Experience}
 * （空 record）绕开 ItemStack 的注册表依赖。
 */
class NvgCardVerticalShiftTest {

    /** 入场曲线的主题侧：enter 开、时长 480ms（与默认主题一致）。 */
    private static CardCanvas canvas(long now, LayoutSettings layout) {
        return new CardCanvas(now, new CardTimeline(480L, 160L, true, true),
                StyleModel.defaults(), PickupCardSettings.defaults(), layout, 426, 240, 1f);
    }

    /** 一张经验卡：bornAt 决定入场钟，exitStartAt >= 0 表示退场已开始。 */
    private static CardSlot slot(long now, long bornAt, long exitStartAt) {
        Notice<Inbox.Card> notice = new Notice<>("test-key", "test-key",
                new Inbox.Card(new CardContent.Experience(), false), 1, true, bornAt, bornAt, 0);
        CardView view = new CardView(notice);
        if (exitStartAt >= 0) {
            view.beginExit(exitStartAt);
        }
        return new CardSlot(view, 100f, 100f, 120f, 24f);
    }

    // ------------------------------------------------------------------
    // DROP 入场
    // ------------------------------------------------------------------

    @Test
    @DisplayName("DROP 入场早期：卡还在锚线上方，位移为负")
    void dropEntranceEarlyIsAboveTheAnchor() {
        // enter = 120/480 = 0.25，easeOutBack(0.25) ≈ 0.817 < 1 → 位移 -(1-p)*24 < 0
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.DROP, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        float shift = NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 10_000 - 120, CardView.NO_EXIT));
        assertTrue(shift < 0f, "入场早期应该在锚线上方（负位移），实际 " + shift);
        assertTrue(shift > -24f, "不该超出整个行程（-24），实际 " + shift);
    }

    @Test
    @DisplayName("DROP 入场过冲段：easeOutBack 越过 1，位移翻成正 —— 落地小弹")
    void dropEntranceOvershootsBelowTheAnchor() {
        // enter = 0.7，easeOutBack ≈ 1.08 > 1 → -(1-1.08)*24 > 0（弹到锚线下方再回来）
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.DROP, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        float shift = NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 10_000 - 336, CardView.NO_EXIT));
        assertTrue(shift > 0f, "过冲段应该落到锚线下方（正位移），实际 " + shift);
        assertTrue(shift < 3f, "小弹只是过冲一截（峰值≈1.1 → ≤2.4px），实际 " + shift);
    }

    @Test
    @DisplayName("DROP 入场完成：位移归零，稳态不悬空")
    void dropEntranceSettlesToZero() {
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.DROP, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        assertEquals(0f, NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 9_000, CardView.NO_EXIT)),
                1e-6f, "入场播完必须精确归零，否则稳态的卡永远悬在锚线上方");
    }

    // ------------------------------------------------------------------
    // FALL 退场
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FALL 退场中途：easeInQuad 加速下坠，位移为正")
    void fallExitMidwayFallsDownward() {
        // exit = 160/320 = 0.5 → easeInQuad = 0.25 → +0.25*24 = 6
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.FALL, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        float shift = NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 9_000, 10_000 - 160));
        assertEquals(6f, shift, 1e-4f, "退场半程应该正好下坠 6px（0.25*24）");
    }

    @Test
    @DisplayName("DROP 入场 + FALL 退场：入场播完后只剩退场分量 —— 两个分支相加不串味")
    void dropAndFallComposeWithoutInterference() {
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.DROP, LayoutSettings.Exit.FALL, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        assertEquals(6f, NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 9_000, 10_000 - 160)),
                1e-4f, "入场完成后入场分量归零，只剩 FALL 的 6px");
    }

    // ------------------------------------------------------------------
    // 非纵向档位恒 0
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SLIDE/TRAIN（默认档）：入场中途与退场中途都一分纵向位移都不给")
    void defaultModesNeverShiftVertically() {
        CardCanvas canvas = canvas(10_000, LayoutSettings.defaults());
        assertEquals(0f, NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 10_000 - 120, CardView.NO_EXIT)),
                1e-6f, "默认入场不该有纵向位移");
        assertEquals(0f, NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 9_000, 10_000 - 160)),
                1e-6f, "默认退场（TRAIN）不该有纵向位移");
    }

    @Test
    @DisplayName("FALL 退场只在真的在退场时算数：没退场的卡不往下坠")
    void fallShiftRequiresAnActualExit() {
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.FALL, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        assertEquals(0f, NvgCardPainter.verticalShiftOf(canvas, slot(10_000, 9_000, CardView.NO_EXIT)),
                1e-6f, "FALL 档但没在退场 → 位移必须是 0");
    }
}
