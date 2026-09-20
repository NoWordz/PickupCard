package com.niuqu.pickupcard.render.nvg;

import com.niuqu.pickupcard.PickupCard;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;

/**
 * NanoVG 上下文的生命周期，以及"一帧"的边界。
 *
 * <p>【它只干四件事】建上下文、开帧、关帧、销毁。画什么、怎么画是
 * {@link NvgCardPainter} 的事 —— 混在一起会长成上帝类，之前已经栽过一次。
 *
 * <p>【拿不到上下文时没有第二条路】2026-09-17 之前这里是"返回 null，调用方退回 SDF 图层"。
 * 那条回退已经被删掉了：同一张卡面有两份实现，就等于每次改卡面都要改两处，而漏掉的那一处
 * 只会在 native 起不来的机器上现形（用户报的"影子还在穿透"就是同一形状的第二个实例）。
 * 现在只有一条：{@link NvgCanvas#shared()} 拿不到就<b>不画</b>，并且把原因响亮地写进日志 ——
 * 静默地少画半张卡比不画更糟。
 *
 * <p>【每帧进出都要过 {@link GlStateGuard}】NanoVG 是直接调 GL 的，它不知道 MC
 * 的状态缓存；开帧前存、关帧后放回，原因见那个类。这里保证 begin/end 成对，
 * 关帧时即使 NanoVG 抛了也会把状态放回去（finally）——状态漏在 NanoVG 那边
 * 的症状是"卡片画完之后别的界面全花"，比卡片不画严重得多。
 */
public final class NvgCanvas implements AutoCloseable {

    /** 上一次 {@link #end()} 里 {@code nvgEndFrame} 与状态恢复各花了多少微秒（诊断读，见 {@link #end}）。 */
    static long endFrameUs;
    static long restoreUs;

    /**
     * NVG_ANTIALIAS：解析抗锯齿，必须开（关掉边缘就是硬的）。
     * NVG_STENCIL_STROKES：描边走模板缓冲，让交叠的描边不出现自相交的暗斑 ——
     * 我们画的是 1px 细边，不开这个的话圆角处会脏。
     */
    private static final int FLAGS = NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES;

    private long ctx;
    private GlStateGuard guard;

    private NvgCanvas(long ctx) {
        this.ctx = ctx;
    }

    /**
     * 必须在渲染线程、且 GL 上下文已就绪时调用。
     *
     * @return 不可用时返回 null，不抛异常
     */
    public static NvgCanvas create() {
        try {
            long ctx = NanoVGGL3.nvgCreate(FLAGS);
            if (ctx == 0L) {
                PickupCard.LOGGER.error("[nvg] nvgCreate 返回 0：GL3 后端初始化失败，本帧不画卡");
                return null;
            }
            PickupCard.LOGGER.info("[nvg] NanoVG 上下文已建立 flags={}", FLAGS);
            return new NvgCanvas(ctx);
        } catch (Throwable t) {
            // native 不在 classpath 上时这里就是 UnsatisfiedLinkError。
            // 抓 Throwable 而不是 Exception：链接错误不是 Exception，漏掉它会直接崩游戏。
            PickupCard.LOGGER.error("[nvg] 加载 NanoVG 失败，本帧不画卡", t);
            return null;
        }
    }

    private static NvgCanvas shared;
    private static boolean sharedTried;

    /**
     * 全局共享的上下文：每帧都要用，不能每帧建一个。拿不到时返回 null，
     * 而且<b>只尝试一次</b> —— 每帧重试的代价是每帧一条错误日志，日志会没法看。
     */
    public static NvgCanvas shared() {
        if (!sharedTried) {
            sharedTried = true;
            shared = create();
        }
        return shared;
    }

    public boolean valid() {
        return ctx != 0L;
    }

    /** 给绘制层用的原生上下文句柄。 */
    public long handle() {
        return ctx;
    }

    /**
     * 开一帧。
     *
     * @param windowWidth   逻辑宽度（MC 的 GUI 缩放后宽度，不是像素）
     * @param windowHeight  逻辑高度
     * @param pixelRatio    GUI 缩放（逻辑 -> 设备像素的倍数）
     */
    public void begin(int windowWidth, int windowHeight, float pixelRatio) {
        if (!valid()) {
            return;
        }
        guard = GlStateGuard.save();
        NanoVG.nvgBeginFrame(ctx, windowWidth, windowHeight, pixelRatio);
    }

    /** 关一帧，并把 MC 的 GL 状态放回去。begin 没成功时是空操作。 */
    public void end() {
        if (!valid() || guard == null) {
            return;
        }
        long t0 = endProfile ? System.nanoTime() : 0L;
        try {
            NanoVG.nvgEndFrame(ctx);
        } finally {
            long t1 = System.nanoTime();
            guard.restore();
            guard = null;
            if (endProfile) {
                NvgCanvas.endFrameUs = (t1 - t0) / 1_000L;
                NvgCanvas.restoreUs = (System.nanoTime() - t1) / 1_000L;
            }
        }
    }

    /** 分段计时开关（诊断用；由 NvgCardPainter 的开关注入）。 */
    static boolean endProfile;

    @Override
    public void close() {
        if (ctx == 0L) {
            return;
        }
        // 同样要在渲染线程上调。
        NanoVGGL3.nvgDelete(ctx);
        ctx = 0L;
    }
}
