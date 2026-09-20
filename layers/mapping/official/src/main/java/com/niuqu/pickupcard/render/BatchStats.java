package com.niuqu.pickupcard.render;

/**
 * 我们把原版批次<b>提交</b>了几次 —— 一枚只给诊断用的计数器。
 *
 * <p>【为什么要数它】卡片的内容（图标每帧原版现渲、文字走字形图集）都在原版批次里排队，
 * 而每次改裁剪框或全局色之前都必须先把已排队的四边形提交掉 —— 裁剪与 {@code setShaderColor}
 * 都是"画的时候才生效"的（见 {@code NvgCardContent#paint} 与 {@code FadingItemBuffers}）。
 * 这些提交在代码里没有形状，但**每帧每卡发生数次**，而且每次提交都是一整轮渲染状态
 * 切换（装了光影时还要重绑程序）—— 一摞卡时它是帧开销里最容易失控的一项。
 *
 * <p>【为什么单独成类】提交点分属三个类（外壳、内容、图标），谁都不该拥有"这一帧提交了
 * 几次"这件事；它属于这一帧的账，读它的是 {@code CardStage}（{@link CardStage.Stats}）。
 */
public final class BatchStats {

    private static long flushes;

    private BatchStats() {
    }

    /** 记一次提交（{@code gui.flush()} 或 {@code endBatch()}）—— 调用方只加不减。 */
    public static void countFlush() {
        flushes++;
    }

    /** 自进程启动累计的提交次数；要"这一帧几次"就自己取差值（见 {@code CardStage#renderInto}）。 */
    public static long flushes() {
        return flushes;
    }
}
