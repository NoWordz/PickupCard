package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.render.FadingItemBuffers;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.ui.widget.BoxPainter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * <b>宿主自绘盒的实现</b>（A-25）：把树里的一个盒子交给原版批次，画成一个 MC 物品。
 *
 * <p>【它补的是哪一道缺口】A-17 撞过一次：Trellis 的 L4 <b>画不了 MC 物品</b> ——
 * 物品图标走原版 {@code ItemRenderer} 的现渲（要 pose、要光照、要原版批次），
 * 那不是任何 {@code Canvas} 原语。框架侧的答案是"退一步"：{@link BoxPainter} 只交出盒子，
 * 画什么由宿主定；本类就是那一跳的宿主侧。
 *
 * <p>【必须延迟到帧尾 —— 这不是优化，是正确性】物品渲染是<b>立即的</b> GL 调用，而调用本类的
 * 那一刻，绘制正跑在 {@code nvgBeginFrame / nvgEndFrame} <b>里面</b>（树的绘制在
 * {@code NvgUi} 那一帧里）。宿主的既有口径是"先画 NanoVG 外壳、{@code nvg.end()} 之后再走
 * 原版批次"（{@code NvgCardPainter} 就是这个顺序）。所以这里<b>不直接画</b>，
 * 而是登记到 {@link NvgUi#register} —— 它会在 {@code canvas.end()} 之后统一提交，
 * 并且<b>带上登记时的裁剪框</b>。于是"滚出视口的格子不该画它那颗图标"这件事是自动的
 * （原版 {@code renderItem} 认 GL 的 scissor），与格子的字走同一条规矩。
 *
 * <p>【pose 由谁设】{@link FadingItemBuffers#drawIcon} 的坐标是"调用方已把 pose 变换到
 * 那块空间"的局部坐标 —— 所以本类先把 pose 平移到盒子左上角，再把盒子中心交给它。
 * （卡堆那条路是 {@code NvgCardPainter} 把 pose 变换到卡空间，同一个分工。）
 *
 * <p>【{@code clipped} 为什么是 false】那个参数指的是卡堆的<b>入场/退场裁剪队列</b>
 * （见 {@code FadingItemBuffers} 的类注释），网格里没有那套动画。滚动裁剪走的是
 * 上面那条 {@code NvgUi} 的帧尾分组，不是它。
 */
public final class McBoxPainter implements BoxPainter {

    /** 这一帧的 NanoVG 上下文 —— 登记到它的帧尾上去。 */
    private final NvgUi ui;

    /** 这一帧的画布。它与 {@link #ui} 必须来自同一帧（同一个调用点 new 出来的）。 */
    private final GuiGraphics gui;

    /**
     * @param ui  这一帧的 {@link NvgUi}（帧尾提交由它做）
     * @param gui 这一帧的画布（必须与 {@code ui} 同帧）
     */
    public McBoxPainter(NvgUi ui, GuiGraphics gui) {
        this.ui = ui;
        this.gui = gui;
    }

    @Override
    public void paint(Rect box, Object payload) {
        // 没有东西可画是**正常情况**（格子可能是空的），不是接线错误 ——
        // 接线错误由框架侧那个会抛的 UNWIRED 负责响，两者别混。
        if (!(payload instanceof ItemStack stack) || stack.isEmpty()) {
            return;
        }
        ui.register(() -> {
            var pose = gui.pose();
            pose.pushPose();
            try {
                pose.translate(box.x(), box.y(), 0f);
                FadingItemBuffers.drawIcon(gui, stack,
                        box.width() / 2f, box.height() / 2f,
                        Math.min(box.width(), box.height()), 1f, false);
            } finally {
                pose.popPose();
            }
        });
    }
}
