package com.niuqu.pickupcard.style;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动画时间轴的钉子：过冲是特性、开关必须生效、退场单调。
 * 这些行为玩家是"看得见"的——弹性没了或退场闪烁，都从这里查起。
 */
class CardTimelineTest {

    private static final CardTimeline TL = new CardTimeline(320L, 300L, true, true);

    @Test
    void enterProgressesLinearlyToCompletion() {
        assertEquals(0f, TL.enter(1_000L, 1_000L), 1e-6);
        assertEquals(0.5f, TL.enter(1_160L, 1_000L), 1e-6);
        assertEquals(1f, TL.enter(1_400L, 1_000L), 1e-6);
        // 超时不越界：easing 在渲染层做，时间轴只给归一化 t
        assertEquals(1f, TL.enter(9_999L, 1_000L), 1e-6);
    }

    @Test
    void bumpStaysOneWhenNeverMerged() {
        assertEquals(1f, TL.bump(5_000L, -1L), 1e-6);
    }

    /**
     * 入场两段必须走 {@code CardTimeline} 里那两条曲线，不能是匀速。
     *
     * <p>【这条是给谁钉的】原来 {@link CardTimeline} 只把 t 归一化就交出去，靠一句注释
     * "easing 在渲染层做"顶着 —— 而渲染层从来没做，真机上的评价是"很僵硬，没有曲线"。
     * 曲线是这两条进度的<b>定义</b>，不是画的时候顺手加的装饰，所以钉在这里。
     *
     * <p>【为什么窗口也钉在这儿】2026-09-17 把入场从 800ms 压到 480ms，竖条窗口从"前 30%"
     * 挪到"前 50%"，内容窗口收成 20%→84%。窗口就是节奏本身，所以和曲线一起钉：
     * {@code TL} 的 enterMs=320 → 50% = 160ms、20% = 64ms、84% = 269ms。
     */
    @Test
    void entranceSegmentsFollowTheDraftCurves() {
        // 竖条窗口 = 头 50%（0..160ms）：enter=0.25 落在窗口中点 → 该走 0.950（匀速只会是 0.5）
        assertEquals(0.949947f, TL.bar(1_080L, 1_000L), 2e-3, "竖条中段");
        assertEquals(0f, TL.bar(1_000L, 1_000L), 1e-6);
        assertEquals(1f, TL.bar(1_160L, 1_000L), 1e-6, "竖条 50% 处必须已经长满");

        // 内容窗口 = 20% → 84%（64..269ms）
        assertTrue(TL.content(1_060L, 1_000L) < 0.02f, "20% 之前内容不该动");
        float mid = TL.content(1_166L, 1_000L);
        assertTrue(mid > 0.74f && mid < 0.82f, "内容中段该走 Material 曲线（≈0.78），实际 " + mid);
        assertEquals(1f, TL.content(1_269L, 1_000L), 1e-6, "84% 处必须已经到位");
        // 【尾巴要静止】末尾 16% 是留给"到位"被看见的，不许再有动作
        assertEquals(1f, TL.content(1_300L, 1_000L), 1e-6);
        assertEquals(1f, TL.content(1_320L, 1_000L), 1e-6);
    }

    /** 总时长与节奏：480ms 那一档的窗口必须真的是 240ms / 403ms（换数字时这条会先红）。 */
    @Test
    void entranceBudgetIsWhatTheUserApproved() {
        CardTimeline tl = new CardTimeline(480L, 300L, true, true);
        assertEquals(1f, tl.bar(1_240L, 1_000L), 1e-6, "240ms 时竖条长满");
        assertTrue(tl.content(1_240L, 1_000L) < 1f, "240ms 时内容还在路上（两段重叠，不串行）");
        assertTrue(tl.content(1_403L, 1_000L) > 0.999f, "403ms 时内容已经到位");
    }

    @Test
    void bumpCompletesAndReturnsToOne() {
        assertEquals(0f, TL.bump(2_000L, 2_000L), 1e-6);
        assertEquals(1f, TL.bump(2_400L, 2_000L), 1e-6);
    }

    @Test
    void disabledAnimationsAreInstant() {
        CardTimeline off = new CardTimeline(320L, 300L, false, false);
        assertEquals(1f, off.enter(1_000L, 1_000L), 1e-6);
        assertEquals(1f, off.bump(1_000L, 1_000L), 1e-6);
    }

    @Test
    void exitIsMonotonicAndBounded() {
        assertEquals(0f, CardTimeline.exit(1_000L, 1_000L, 320L), 1e-6);
        assertEquals(0.5f, CardTimeline.exit(1_160L, 1_000L, 320L), 1e-3);
        assertEquals(1f, CardTimeline.exit(2_000L, 1_000L, 320L), 1e-6);
    }

    @Test
    void exitWithZeroDurationIsImmediatelyDone() {
        assertEquals(1f, CardTimeline.exit(1_000L, 1_000L, 0L), 1e-6);
    }

    @Test
    void exitAlphaIsOneMinusEaseOutCubicOfProgress() {
        for (long t = 0; t <= 480; t += 20) {
            float p = CardTimeline.exit(1_000L + t, 1_000L, 480L);
            assertEquals(1f - Easing.easeOutCubic(p),
                    CardTimeline.exitAlpha(1_000L + t, 1_000L, 480L), 1e-6, "t=" + t);
        }
        assertEquals(1f, CardTimeline.exitAlpha(1_000L, 1_000L, 480L), 1e-6);
        assertEquals(0f, CardTimeline.exitAlpha(1_480L, 1_000L, 480L), 1e-6);
    }

    /**
     * 「淡到看不见」的门槛落在淡出的哪一刻 —— 这条钉的是用户 2026-09-18 报的那件事。
     * <p>救回的不透明度是从"已经淡到哪儿"补回来的，所以救回发生在淡出末段时，屏幕上是
     * 一张已经看不见的卡凭空冒出来（也就是"最后一帧文字和图标突然闪一下"）。0.15 这个门槛
     * 配 easeOutCubic 意味着：<b>默认 480ms 的淡出里，前 225ms 值得救回，之后不值得</b>。
     * 数字被改了的话这条会红 —— 那时候要重新想"救回 vs 重播入场"的分界。
     */
    @Test
    void theTooFadedToReviveThresholdSitsJustBeforeHalfTheFade() {
        assertEquals(0.15f, CardTimeline.exitAlpha(1_225L, 1_000L, 480L), 0.005f);
        assertTrue(CardTimeline.exitAlpha(1_200L, 1_000L, 480L) > 0.15f, "前半段还看得见，该救回");
        assertTrue(CardTimeline.exitAlpha(1_250L, 1_000L, 480L) < 0.15f, "过了就没救了，该重播入场");
    }

    @Test
    void easeOutBackOvershootsThenSettles() {
        // 过冲是"弹性"档的灵魂：中途必须超过 1，终点必须恰好落回 1
        assertTrue(Easing.easeOutBack(0.6f) > 1f, "easeOutBack 应该过冲");
        assertEquals(1f, Easing.easeOutBack(1f), 1e-6);
        assertEquals(0f, Easing.easeOutBack(0f), 1e-6);
    }

    @Test
    void pulsePeaksAtMiddleAndReturns() {
        assertEquals(1f, Easing.pulse(0f, 1.35f), 1e-6);
        assertEquals(1.35f, Easing.pulse(0.5f, 1.35f), 1e-4);
        assertEquals(1f, Easing.pulse(1f, 1.35f), 1e-6);
    }
}
