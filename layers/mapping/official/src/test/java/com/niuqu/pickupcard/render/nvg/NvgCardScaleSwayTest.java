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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 退场缩放与摇摆的<b>单点出处</b> {@link NvgCardPainter#cardScaleOf} /
 * {@link NvgCardPainter#swayAngleOf} 的账。
 * <p>
 * 【为什么值得钉】与 {@code NvgCardVerticalShiftTest} 同一条纪律：外壳（NanoVG）与内容
 * （原版 pose）必须同吃这两个数，谁另算一份谁迟早错（2026-09-20 镜像 bug 的教训）。
 * 本类钉数值语义：SCALE 退场 1 → 0.15（easeInQuad 收敛）、非 SCALE 档恒 1；
 * sway 只属于"入场播完且没在退场"的稳态卡，±1.2° 包络、按 key 散列错相。
 * 接线两侧由 harness 截图验证（计划 Task B4）。
 * <p>纯时间数学、不碰渲染，所以不必起游戏（{@code WARM_ICONS} 已惰性化，
 * 本类的 static init 不再拖注册表依赖）。
 */
class NvgCardScaleSwayTest {

    /** 一张经验卡：bornAt 决定入场钟，exitStartAt >= 0 表示退场已开始。 */
    private static CardSlot slot(long bornAt, long exitStartAt) {
        Notice<Inbox.Card> notice = new Notice<>("sway-test-key", "sway-test-key",
                new Inbox.Card(new CardContent.Experience(), false), 1, true, bornAt, bornAt, 0);
        CardView view = new CardView(notice);
        if (exitStartAt >= 0) {
            view.beginExit(exitStartAt);
        }
        return new CardSlot(view, 100f, 100f, 120f, 24f);
    }

    private static CardCanvas canvas(long now, LayoutSettings layout) {
        return new CardCanvas(now, new CardTimeline(480L, 160L, true, true),
                StyleModel.defaults(), PickupCardSettings.defaults(), layout, 426, 240, 1f);
    }

    // ------------------------------------------------------------------
    // SCALE 退场
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SCALE 退场半程：1 - 0.85 * easeInQuad(0.5) = 0.7875")
    void scaleExitMidwayShrinks() {
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.SCALE, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        float factor = NvgCardPainter.cardScaleOf(canvas, slot(9_000, 10_000 - 160));
        assertEquals(0.7875f, factor, 1e-4f, "退场半程应该缩到 78.75%（easeInQuad(0.5)=0.25）");
        assertTrue(factor > 0.15f, "半程不该已经缩到底（0.15 是终点）");
    }

    @Test
    @DisplayName("SCALE 退场播完：缩到 0.15 后恒定，不会翻成负数")
    void scaleExitEndsAtTheFloor() {
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.SCALE, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        assertEquals(0.15f, NvgCardPainter.cardScaleOf(canvas, slot(9_000, 9_000)),
                1e-4f, "退场进度已夹满（exitOf=1）→ 1 - 0.85 = 0.15");
    }

    @Test
    @DisplayName("非 SCALE 档、或 SCALE 档没在退场：恒为 1，一分都不缩")
    void noScaleWithoutScaleExit() {
        // 默认 TRAIN 档，退场中
        assertEquals(1f, NvgCardPainter.cardScaleOf(canvas(10_000, LayoutSettings.defaults()),
                slot(9_000, 10_000 - 160)), 1e-6f, "TRAIN 退场不该带缩放");
        // SCALE 档但还没开始退
        CardCanvas canvas = canvas(10_000, new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.SCALE, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false));
        assertEquals(1f, NvgCardPainter.cardScaleOf(canvas, slot(9_000, CardView.NO_EXIT)),
                1e-6f, "SCALE 档稳态卡不该缩");
    }

    // ------------------------------------------------------------------
    // sway 摇摆
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sway 关（默认）：稳态、入场中、退场中角度恒为 0")
    void swayDisabledIsAlwaysZero() {
        CardCanvas canvas = canvas(10_000, LayoutSettings.defaults());
        assertEquals(0f, NvgCardPainter.swayAngleOf(canvas, slot(9_000, CardView.NO_EXIT)));
        assertEquals(0f, NvgCardPainter.swayAngleOf(canvas, slot(10_000, CardView.NO_EXIT)));
        assertEquals(0f, NvgCardPainter.swayAngleOf(canvas, slot(9_000, 10_000 - 160)));
    }

    @Test
    @DisplayName("sway 开：没入场完、或正在退场时恒 0 —— 摇摆只属于稳态")
    void swayOnlyAppliesToSettledCards() {
        LayoutSettings layout = new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, true);
        CardCanvas canvas = canvas(10_000, layout);
        assertEquals(0f, NvgCardPainter.swayAngleOf(canvas, slot(10_000, CardView.NO_EXIT)),
                "刚出生（contentOf < 1）不该摇");
        assertEquals(0f, NvgCardPainter.swayAngleOf(canvas, slot(9_000, 10_000 - 160)),
                "正在退场不该摇（退场有自己的戏）");
    }

    @Test
    @DisplayName("sway 开 + 稳态：角度在 ±1.2° 包络内，且随时间真的在摆")
    void swayOscillatesWithinTheEnvelope() {
        LayoutSettings layout = new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, true);
        CardSlot settled = slot(0L, CardView.NO_EXIT);      // bornAt=0：扫描窗口内入场必然播完
        Float first = null;
        boolean moved = false;
        for (long now = 1_000L; now <= 5_000L; now += 100L) {   // 覆盖一个完整周期（2s）多一点
            float angle = NvgCardPainter.swayAngleOf(canvas(now, layout), settled);
            assertTrue(Math.abs(angle) <= 1.2f, "角度该在 ±1.2° 包络内，实际 " + angle);
            if (first == null) {
                first = angle;
            } else if (Math.abs(angle - first) > 0.05f) {
                moved = true;
            }
        }
        assertTrue(moved, "稳态卡的角度必须随时间变化 —— 不然就不是「摇摆」");
        assertNotEquals(0f, first, "抽样起点恰好为 0 也算可疑（相位散列不该全零）");
    }

    @Test
    @DisplayName("不同 key 的卡错相：同一时刻两卡角度不同（齐步摆就成迪厅了）")
    void swayPhasesDifferByKey() {
        LayoutSettings layout = new LayoutSettings(
                LayoutSettings.Appear.SLIDE, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, true);
        long now = 1_500L;
        float a = NvgCardPainter.swayAngleOf(canvas(now, layout), slot(0L, CardView.NO_EXIT, "a-key"));
        float b = NvgCardPainter.swayAngleOf(canvas(now, layout), slot(0L, CardView.NO_EXIT, "b-key"));
        assertNotEquals(a, b, "同一时刻两张不同 key 的卡不该同角度");
    }

    /** key 可指定的 slot 变体（错相测试用）。 */
    private static CardSlot slot(long bornAt, long exitStartAt, String key) {
        Notice<Inbox.Card> notice = new Notice<>(key, key,
                new Inbox.Card(new CardContent.Experience(), false), 1, true, bornAt, bornAt, 0);
        return new CardSlot(new CardView(notice), 100f, 100f, 120f, 24f);
    }
}
