package com.niuqu.pickupcard.render;

/**
 * 一轮卡堆的「最糟的一帧」账本：<b>帧号</b> + layout/paint 拆分 + 卡数 + 批次提交数。
 *
 * <p>【为什么必须记帧号】峰值是个只进不出的极值，而读它的人是<b>抽样</b>读的
 * （harness 一个 tick 问一次，而渲染帧比 tick 多 —— 见 {@code CardStage#renderInto}
 * 与 {@code DevHarness.tickHud}）—— 于是它报得出「有个 15ms 的 worst」，却
 * <b>认不出那是哪一帧</b>：说不清它落在入场动画的哪个相位，也就分不清「首帧的首次成本」
 * 与「中段的真回归」。A-25 遗留①卡死在这里（{@code docs/plan.md}），补上帧号之后
 * 问题就退化成一句「worst 在第 N 帧」。
 *
 * <p>【为什么单独成类】它是纯整数记账、不碰渲染，所以能在离线测试里被钉住
 * （见 {@code FramePeakTest}）。这条规矩很容易写错又看不出来 —— 本类的前身就踩过一次：
 * javadoc 写着「卡堆清空时复位（每轮各自记各自的）」，而代码里那个复位分支<b>永远走不到</b>
 * （空卡堆会在 {@code CardStage#renderInto} 里提前 return，根本轮不到那次记录）。
 * 于是峰值实际是<b>整个进程</b>的极值，不是「这一轮」的。
 */
final class FramePeak {

    /** 本轮已观察到的帧数（{@link #observe} 每调一次 +1），也用作当前帧的序号。 */
    private long frames;
    /** 本轮最慢一帧的序号（1 起）。0 = 本轮还没有任何一帧。 */
    private long worstFrameNo;
    private long worstMicros;
    private long worstLayoutMicros;
    private long worstPaintMicros;
    private int worstCards;
    private long worstFlushes;
    private String worstShape = "";
    /** 本轮单帧最多的批次提交次数（与「最慢那一帧」不一定同时发生）。 */
    private long maxFlushes;

    /** 开新一轮（卡堆从空变非空之前调；空帧上重复调是幂等的）。 */
    void reset() {
        frames = 0L;
        worstFrameNo = 0L;
        worstMicros = 0L;
        worstLayoutMicros = 0L;
        worstPaintMicros = 0L;
        worstCards = 0;
        worstFlushes = 0L;
        worstShape = "";
        maxFlushes = 0L;
    }

    /**
     * 记一帧。
     * <p><b>只算「真有卡在屏上」的帧</b>：没卡的帧是一两微秒的空转，记进去只会把峰值稀释掉，
     * 而且它会污染帧号（`第 1 帧` 就不再是"整摞卡第一次上屏"）。这个前提由本类自己守
     * —— 调用方传 {@code cards <= 0} 就直接忽略，别指望每个调用点都记得先判一次
     * （那种"调用方要记得"的约定，就是本类前身那个死分支的成因）。
     */
    void observe(long totalMicros, long layoutMicros, long paintMicros,
                 int cards, long flushes, String shape) {
        if (cards <= 0) {
            return;
        }
        frames++;
        if (totalMicros > worstMicros) {
            worstMicros = totalMicros;
            worstLayoutMicros = layoutMicros;
            worstPaintMicros = paintMicros;
            worstCards = cards;
            worstFlushes = flushes;
            worstShape = shape;
            worstFrameNo = frames;
        }
        maxFlushes = Math.max(maxFlushes, flushes);
    }

    /** 本轮已观察到的帧数，也就是当前帧的序号。 */
    long frames() {
        return frames;
    }

    long worstFrameNo() {
        return worstFrameNo;
    }

    long worstMicros() {
        return worstMicros;
    }

    long worstLayoutMicros() {
        return worstLayoutMicros;
    }

    long worstPaintMicros() {
        return worstPaintMicros;
    }

    int worstCards() {
        return worstCards;
    }

    long worstFlushes() {
        return worstFlushes;
    }

    String worstShape() {
        return worstShape;
    }

    long maxFlushes() {
        return maxFlushes;
    }
}
