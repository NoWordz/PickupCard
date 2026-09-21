package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Align;
import dev.e33.trellis.layout.ContentSizer;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.UiTree;

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

    // -----------------------------------------------------------------------
    // 探针：一棵组件树，命中和绘制读同一份 bounds()（判据 1 的真机验证）
    // -----------------------------------------------------------------------

    /**
     * 建一棵配置列的组件树：根 = 列，每个直接子 = 一行，行内 = 标签 + 控件。
     *
     * <p>【为什么用 {@link Component} 而不是上一版那棵裸 {@code LayoutNode}】判据 1 要求
     * "绘制矩形和点击矩形必须是<b>同一个</b> {@code Rect} 对象"。裸节点有几何、没有命中，
     * 验不到那一条；换到组件层之后，{@link UiTree#hitTest} 读的是 {@link Component#bounds()}，
     * 描框画的也是它 —— 同一个对象，不是相等的两个。
     *
     * <p>树由<b>调用方持有</b>：{@link UiTree} 带着悬停状态，不能每帧重建。
     *
     * @param hasControl 每行<b>有没有控件</b>：小节头那一行只有标签、没有控件。
     *                   宿主的小节头也是"占一行、没有控件"，这里必须照建 ——
     *                   给小节头也造一个控件，布局是对的，但那是宿主根本不存在的东西，
     *                   描框和命中都会凭空多出一行。
     * @param topInset   宿主第一行相对列顶的内缩（传 {@code ConfigRows.ROWS_TOP_INSET}）。
     *                   <b>必须由宿主交进来、当成树自己的内边距用，不能在桥里事后补</b>。
     */
    public static UiTree buildColumn(boolean[] hasControl, float topInset) {
        Component root = new Box(columnStyle(topInset), false);
        for (boolean control : hasControl) {
            Component line = new Box(Style.row().withGap(GAP).withHeight(Sizing.fixed(ROW_H)), false);
            line.add(new Box(Style.row().withGrow(1f).withWidth(Sizing.atLeast(LABEL_MIN))
                    .withHeight(Sizing.fixed(ROW_H)), true));
            if (control) {
                line.add(new Box(Style.row()
                        .withWidth(Sizing.fraction(CONTROL_MIN, CONTROL_FRACTION, CONTROL_MAX))
                        .withHeight(Sizing.fixed(ROW_H)), true));
            }
            root.add(line);
        }
        return new UiTree(root);
    }

    /**
     * 把宿主自己那份 {@code ConfigLayout.Rect} 翻译成 Trellis 的 {@code Rect} 再布局。
     *
     * <p>两个 record 字段几乎一样却必须在这里互相翻译 —— 这正是"布局口径没统一"的证据，
     * 也是 Trellis 要收掉的东西。翻译只许发生在这一个地方（适配器的本职）；
     * 真正接的时候这段话应该消失（只留一套几何）。
     *
     * @param grid 网格间距（{@code 1 / guiScale} = 一个设备像素）
     */
    public static void layoutColumn(UiTree ui, ConfigLayout.Rect items, float grid) {
        ui.layout(new Rect(items.x(), items.y(), items.w(), items.h()), grid);
    }

    /**
     * 描出每个控件的 {@code bounds()}；指针正指着的那一行描成 {@code hoverArgb}。
     *
     * <p>"画的就是命中读的那个矩形"：两边都是 {@link Component#bounds()}。
     * 网格对齐由调用方在 {@link UiTree#layout(Rect, float)} 那一趟做 —— 对在布局层，
     * 这里描出来的本来就是设备整数，渲染层不必也不需要再挪。
     */
    public static void drawColumn(NvgCanvas host, UiTree ui, int argb, int hoverArgb) {
        dev.e33.trellis.render.nanovg.NvgCanvas canvas = attach(host);
        Component hoveredLine = hoveredLine(ui);
        for (Component line : ui.root().children()) {
            if (line.children().size() < 2) {
                continue;       // 小节头：那一行没有控件，不描
            }
            Rect control = line.children().get(1).bounds();
            canvas.strokeRoundRect(control, 2f, 1f, line == hoveredLine ? hoverArgb : argb);
        }
    }

    /** 指针所在的那<b>一行</b>（根的直接子节点）；没悬停就是 null。 */
    private static Component hoveredLine(UiTree ui) {
        for (Component c = ui.hovered(); c != null; c = c.parent()) {
            if (c.parent() == ui.root()) {
                return c;
            }
        }
        return null;
    }

    private static Style columnStyle(float topInset) {
        // 上 topInset / 下 0：底下那点不是留白，加了只会把列撑高。
        return Style.column().withPadding(Insets.of(topInset, PAD, 0f, PAD))
                .withGap(ROW_GAP).withAlign(Align.STRETCH);
    }

    /**
     * 探针用的最小盒子：只占位、不画内容。
     *
     * <p>描框由探针拿宿主画布直接画（见 {@link #drawColumn}）—— 这样"描的那个矩形"
     * 就是 {@code bounds()} 本身，中间不隔任何一层自己的几何。
     */
    private static final class Box extends Component {
        Box(Style s, boolean leaf) {
            style(s);
            if (leaf) {
                contentSizer(ContentSizer.EMPTY);
            }
        }

        @Override
        protected void drawContent(dev.e33.trellis.render.Canvas canvas) {
            // 探针不画内容：只描框。
        }
    }
}
