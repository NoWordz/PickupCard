package com.niuqu.pickupcard.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 峰值账本的记账规矩 —— 这是 A-25 遗留①那条"未归因的 15ms"缺的那半边。
 * <p>
 * 【为什么值得钉】它错起来不报错，只给一个<b>认不出帧</b>的 us 数：读的人拿着一个 15ms 的
 * worst，说不清它落在入场动画的哪个相位，于是分不清"第 1 帧的首次成本"与"中段的真回归"。
 * <p>⚠️ <b>这里钉的是账本本身</b>（帧号、拆分、极值、复位、空帧）。这个类的前身还犯过另一个
 * 更隐蔽的错、而且那个错<b>在这个测试里钉不到</b>：javadoc 写着"每轮复位"，而 {@code CardStage}
 * 里那个复位分支<b>永远走不到</b>（空卡堆会在调用方提前 return）—— 于是峰值实际是整个进程的
 * 极值。修法是调用方新增 {@code endRound()} 并挂旗延迟复位，那条<b>接线</b>只能在真机上验
 * （{@code CardStage} 要 MC 的类，离线起不来）。别把下面这几条读成"接线也验过了"。
 * <p>纯整数记账、不碰渲染，所以不必起游戏。
 */
class FramePeakTest {

    @Test
    @DisplayName("峰值记得住是第几帧：后面出现更大的帧，帧号要跟着挪过去")
    void worstFrameNoIdentifiesTheExpensiveFrame() {
        FramePeak p = new FramePeak();
        // 第 1 帧：整摞卡首次上屏，贵（真实读数就是它最大）
        p.observe(15_000, 1_000, 14_000, 5, 16, "进场=5 退场=0");
        // 第 2、3 帧：稳态，便宜
        p.observe(1_200, 40, 1_160, 5, 6, "进场=5 退场=0");
        p.observe(1_100, 40, 1_060, 5, 6, "进场=5 退场=0");

        assertEquals(3, p.frames(), "记了三帧");
        assertEquals(1, p.worstFrameNo(), "最贵的是第 1 帧");
        assertEquals(15_000, p.worstMicros());
        assertEquals(1_000, p.worstLayoutMicros(), "峰值那一帧自己的 layout 拆分");
        assertEquals(14_000, p.worstPaintMicros(), "峰值那一帧自己的 paint 拆分");
        assertEquals("进场=5 退场=0", p.worstShape(), "峰值那一帧的形态（进场/退场）");

        // 换一种情形：第 1 帧便宜、中段某一帧才贵 —— 帧号必须指向那一帧
        FramePeak q = new FramePeak();
        q.observe(800, 30, 770, 5, 6, "进场=5 退场=0");        // frame 1
        q.observe(900, 30, 870, 5, 6, "进场=5 退场=0");        // frame 2
        q.observe(16_500, 300, 16_200, 6, 17, "进场=1 退场=4"); // frame 3 ← 中段回归
        assertEquals(3, q.worstFrameNo(), "中段的回归要落在第 3 帧上，不能还指着第 1 帧");
        assertEquals(16_500, q.worstMicros());
        assertEquals(300, q.worstLayoutMicros());
        assertEquals(16_200, q.worstPaintMicros());
        assertEquals("进场=1 退场=4", q.worstShape(), "形态要跟着峰值那一帧走");
    }

    @Test
    @DisplayName("单帧最多提交数与「最慢那一帧」各记各的：它们不一定同时发生")
    void maxFlushesIsIndependentOfTheWorstFrame() {
        FramePeak p = new FramePeak();
        // 最慢的一帧提交 6 次；另一帧更便宜但提交了 17 次（合并滚动临时多提交）
        p.observe(15_000, 1_000, 14_000, 5, 6, "进场=5 退场=0");
        p.observe(5_500, 1_200, 4_300, 6, 17, "进场=1 退场=4");
        assertEquals(1, p.worstFrameNo(), "最慢还是第 1 帧");
        assertEquals(15_000, p.worstMicros());
        assertEquals(17, p.maxFlushes(), "提交次数的极值来自第 2 帧，与最慢那帧无关");
        assertEquals(6, p.worstFlushes(), "峰值那一帧自己的提交次数");
    }

    @Test
    @DisplayName("复位要真的清干净：帧号、各极值、总帧数一个不留")
    void resetTrulyClears() {
        FramePeak p = new FramePeak();
        p.observe(15_000, 1_000, 14_000, 5, 16, "进场=5 退场=0");
        p.observe(1_200, 40, 1_160, 5, 6, "进场=5 退场=0");
        p.reset();

        // 本轮还没结束时问它：应当"什么都没观察到"，不是"还记着上一轮的账"
        assertEquals(0, p.frames(), "复位后总帧数归零");
        assertEquals(0, p.worstFrameNo(), "复位后没有『最慢的一帧』");
        assertEquals(0, p.worstMicros());
        assertEquals(0, p.worstLayoutMicros());
        assertEquals(0, p.worstPaintMicros());
        assertEquals(0, p.worstCards());
        assertEquals(0, p.worstFlushes());
        assertEquals("", p.worstShape(), "复位后形态也归零（老代码漏了这条）");
        assertEquals(0, p.maxFlushes());

        // 新一轮：帧号从 1 重新数起
        p.observe(2_000, 50, 1_950, 4, 6, "进场=4 退场=0");
        assertEquals(1, p.frames(), "新一轮第 1 帧");
        assertEquals(1, p.worstFrameNo());
        assertEquals(2_000, p.worstMicros(), "只反映新一轮，上一轮那 15ms 不该被带进来");
    }

    @Test
    @DisplayName("空帧（0 张卡）不算数：不占帧号、不进峰值 —— 否则「第 1 帧」会被空转帧顶掉")
    void emptyFramesAreIgnored() {
        FramePeak p = new FramePeak();
        p.observe(2, 1, 1, 0, 0, "进场=0 退场=0");       // 空转帧，先来一发
        p.observe(12_000, 100, 11_900, 5, 16, "进场=5 退场=0"); // 这才是本轮第一帧
        p.observe(3, 1, 2, 0, 0, "进场=0 退场=0");       // 又一个空转帧
        assertEquals(1, p.frames(), "只数有卡的帧");
        assertEquals(1, p.worstFrameNo(), "第一帧有卡的那一帧才是第 1 帧");
        assertEquals(12_000, p.worstMicros(), "空转的 2us/3us 不该被记成峰值");
    }

    @Test
    @DisplayName("相等不算更新：后到的同值帧不抢帧号（最早的极值才算数）")
    void tiesKeepTheEarlierFrame() {
        FramePeak p = new FramePeak();
        p.observe(9_000, 100, 8_900, 5, 6, "进场=5 退场=0");   // frame 1
        p.observe(9_000, 100, 8_900, 5, 6, "进场=5 退场=0");   // frame 2，同值
        assertEquals(1, p.worstFrameNo(), "同值不上报新帧号 —— 先到的那帧才是首次突破");
    }
}
