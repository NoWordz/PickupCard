package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Align;
import dev.e33.trellis.layout.ContentSizer;
import dev.e33.trellis.layout.FlexLayout;
import dev.e33.trellis.layout.LayoutNode;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;

/**
 * <b>Trellis 试点：把 Trellis 接进 PickupCard 已有的 NanoVG 上下文。</b>
 *
 * <p>【它现在只做一件事，而且故意很小】用 Trellis <b>算出</b>配置列每一行
 * 「标签 + 控件」的几何，把控件那块描一圈，好在真机截图里<b>看见</b>它。
 * 描圈是临时的取证手段，不是最终形态 —— 最终形态是绘制整个走 Trellis。
 *
 * <p>【为什么先做这个而不是整屏】整屏换掉等于一上来就把渲染路径、事件、字体、
 * 生命周期全换了，出问题分不清是谁的锅。先证明最小的那条链：
 * <b>Trellis 算出的坐标真的出现在 MC 的帧里</b>。这一条通了，剩下的都是加法。
 *
 * <p>【字体为什么没接】Trellis 的文字走 NanoVG + TTF（仓库里那份 Inter），
 * 而 Minecraft 的字是<b>位图图集</b>，喂不进去。所以试点期间的分工是
 * <b>Trellis 管几何和形状，文字仍由 PickupCard 自己画</b>。
 * 这不只是权宜 —— 它顺手绕开了 Trellis 现在还画不了 CJK 的问题。
 */
public final class TrellisBridge {

    private TrellisBridge() {
        throw new AssertionError("no instances");
    }

    /** 与 {@link ConfigRows} 同一套几何口径。 */
    private static final float PAD = 6f;
    private static final float GAP = 6f;
    private static final float LABEL_MIN = 24f;
    private static final float CONTROL_MIN = 48f;
    private static final float CONTROL_MAX = 130f;
    private static final float CONTROL_FRACTION = 0.45f;
    // 行距/行高与 com.niuqu.pickupcard.config.ConfigRows 一致（20 / 18）。
    // 【为什么抄数而不是引用】那个类是包私有、且在另一个包（config），跨包看不见 ——
    // 这正是"同一个几何口径散在两个包"的样子，也是 Trellis 想收掉的那类东西。
    private static final float ROW_H = 18f;
    private static final float ROW_GAP = 20f - ROW_H;

    /** 接进宿主上下文。帧由宿主开也由宿主关，这里只画。 */
    private static dev.e33.trellis.render.nanovg.NvgCanvas attach(NvgCanvas host) {
        return dev.e33.trellis.render.nanovg.NvgCanvas.attach(host.handle());
    }

    /**
     * 用 Trellis 摆出配置列的每一行，并把控件那块描一圈。
     *
     * @param items 配置列矩形（{@link ConfigLayout} 已经算好的）
     * @param rows  行数
     */
    public static void outlineControls(NvgCanvas host, Rect items, int rows, int argb) {
        dev.e33.trellis.render.nanovg.NvgCanvas canvas = attach(host);
        LayoutNode root = column(rows);
        FlexLayout.solve(root, items);
        for (int i = 0; i < rows; i++) {
            Rect control = root.children().get(i).children().get(1).rect();
            canvas.strokeRoundRect(control, 2f, 1f, argb);
        }
    }

    /**
     * 声明式的配置列。
     *
     * <p>对照手算版本（{@link ConfigRows#controlW} 那一组）：那边是
     * {@code controlW = clamp(room * 45 / 100, 48, 130)}、{@code controlX = right - 6 - controlW}、
     * {@code labelW = max(24, controlX - labelX - 6)} —— 三个公式互为输入。
     * 这边只有结构：<b>一个绝对坐标都没有</b>。
     */
    private static LayoutNode column(int rows) {
        LayoutNode[] line = new LayoutNode[rows];
        for (int i = 0; i < rows; i++) {
            line[i] = LayoutNode.of(Style.row().withGap(GAP).withHeight(Sizing.fixed(ROW_H)),
                    LayoutNode.leaf(Style.row().withGrow(1f).withWidth(Sizing.atLeast(LABEL_MIN))
                            .withHeight(Sizing.fixed(ROW_H)), ContentSizer.EMPTY),
                    LayoutNode.leaf(Style.row()
                            .withWidth(Sizing.fraction(CONTROL_MIN, CONTROL_FRACTION, CONTROL_MAX))
                            .withHeight(Sizing.fixed(ROW_H)), ContentSizer.EMPTY));
        }
        // STRETCH 必须有：交叉轴默认 START 的话每行只占自然宽，
        // 控件会停在列中间（离线试点踩过一次，不报错，只是看起来不对）。
        return LayoutNode.of(Style.column().withPadding(Insets.symmetric(0f, PAD))
                .withGap(ROW_GAP).withAlign(Align.STRETCH), line);
    }
}
