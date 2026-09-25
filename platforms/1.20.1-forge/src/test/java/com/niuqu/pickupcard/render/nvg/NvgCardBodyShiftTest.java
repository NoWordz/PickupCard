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
 * 水平位移 {@link NvgCardPainter#bodyShiftOf} 的路线账（评审 Y1 定案的钉子，2026-09-25）。
 * <p>
 * 【为什么值得钉】DROP 定案为「斜滑 + 掉落」：水平走火车路线、垂直由 verticalShiftOf 另算 ——
 * 也就是说 <b>DROP 的水平位移必须和 SLIDE 完全一致</b>，谁要是"顺手优化"成纯垂直掉落，
 * 这里的等式就会红。BOUNCE 钉的是过冲段：进度吃 easeOutBack(enterOf)（不是传入的 rise），
 * 过冲时 (1-p) 翻负、位移翻过终点 —— 这就是"冲过终点再被拉回"的数学形态。
 * CLIP 钉零：拉幕不位移内容。fixture 与 {@link NvgCardVerticalShiftTest} 同款（离线、无 MC）。
 */
class NvgCardBodyShiftTest {

    private static CardCanvas canvas(long now, LayoutSettings layout) {
        return new CardCanvas(now, new CardTimeline(480L, 160L, true, true),
                StyleModel.defaults(), PickupCardSettings.defaults(), layout, 426, 240, 1f);
    }

    private static CardSlot slot(long now, long bornAt) {
        Notice<Inbox.Card> notice = new Notice<>("test-key", "test-key",
                new Inbox.Card(new CardContent.Experience(), false), 1, true, bornAt, bornAt, 0);
        return new CardSlot(new CardView(notice), 100f, 100f, 120f, 24f);
    }

    private static LayoutSettings layout(LayoutSettings.Appear appear) {
        return new LayoutSettings(appear, LayoutSettings.Exit.TRAIN, LayoutSettings.Side.RIGHT,
                4f, 100, -1f, -1f, false, false);
    }

    /** 非镜像：内容从竖条（左）后面往右冒，位移为负（dir=-1）。 */
    private static final float BODY_W =
            120f - StyleModel.defaults().barWidth() - StyleModel.defaults().gap();

    @Test
    @DisplayName("DROP 与 SLIDE 的水平位移完全一致 —— 「斜滑」定案的钉子")
    void dropSlidesAlongTheSameTrackAsSlide() {
        long now = 10_000;
        CardSlot s = slot(now, now - 120);
        float slide = NvgCardPainter.bodyShiftOf(
                canvas(now, layout(LayoutSettings.Appear.SLIDE)), s,
                StyleModel.defaults(), 120f / 480f);
        float drop = NvgCardPainter.bodyShiftOf(
                canvas(now, layout(LayoutSettings.Appear.DROP)), s,
                StyleModel.defaults(), 120f / 480f);
        assertEquals(slide, drop, 1e-6, "DROP 的水平分量就是火车路线，一分不多一分不少");
        assertTrue(slide < 0f, "非镜像入场内容从竖条后面往右冒，水平位移为负");
    }

    @Test
    @DisplayName("BOUNCE 过冲段：位移翻过终点（翻正），与 SLIDE 同 rise 不同号")
    void bounceOvershootCrossesTheEndpoint() {
        long now = 10_000;
        // enter = 350/480 ≈ 0.729，easeOutBack ≈ 1.071 > 1 → (1-p) < 0 → 位移翻正
        CardSlot s = slot(now, now - 350);
        float rise = 350f / 480f;
        float bounce = NvgCardPainter.bodyShiftOf(
                canvas(now, layout(LayoutSettings.Appear.BOUNCE)), s,
                StyleModel.defaults(), rise);
        float slide = NvgCardPainter.bodyShiftOf(
                canvas(now, layout(LayoutSettings.Appear.SLIDE)), s,
                StyleModel.defaults(), rise);
        assertTrue(bounce > 0f, "过冲段内容冲过终点，位移翻正（被窗口右缘裁 ~6px，见 bodyShiftOf 注释）");
        assertTrue(slide < 0f, "同 rise 的 SLIDE 还没到终点");
        assertTrue(bounce > -0.01f * BODY_W, "过冲量约 0.07·bodyW，不该大得离谱");
    }

    @Test
    @DisplayName("CLIP 拉幕不位移内容，恒 0")
    void clipNeverShiftsTheBody() {
        long now = 10_000;
        CardSlot s = slot(now, now - 120);
        assertEquals(0f, NvgCardPainter.bodyShiftOf(
                canvas(now, layout(LayoutSettings.Appear.CLIP)), s,
                StyleModel.defaults(), 120f / 480f), 1e-6,
                "拉幕动的是可见范围，内容一分不动");
    }
}
