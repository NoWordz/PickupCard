package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Rect;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 滑条：上面一行值，下面一条轨道 + 一颗圆钮。<b>点哪儿跳哪儿，按住能拖。</b>
 *
 * <p>【为什么要"点哪儿跳哪儿"】原版滑条必须先按住那颗钮再拖 —— 而钮只有几像素宽，
 * 玩家第一次点会以为"这个界面点不动"。整条轨道都是热区之后，一次点击就能到位。
 *
 * <p>【吸附交给 step】时长 500..10000ms 铺在 90px 上，一像素 ≈ 100ms —— 不吸附就会拖出
 * "3987ms"这种数，玩家会觉得调不准。step 让每一格都是整得好看的数。
 *
 * <p>【几何从 {@code box} 来（A-10 第二步）】绘制读 {@code ctx.box()}，拖拽读调用方
 * 交进 {@code press/drag} 的那一份 —— 它们是同一个矩形（树那条路上都是 {@code bounds()}）。
 */
public final class NvgSlider extends NvgWidget {

    private final double min;
    private final double max;
    private final double step;
    private final Supplier<Double> value;
    private final Consumer<Double> onChange;
    private final Function<Double, String> format;

    /**
     * @param min      最小可取值（可以是 -1：竖条位置那一项 -1 就是"自动"，是个真值）
     * @param step     吸附步长；&lt;= 0 表示不吸附
     * @param format   值怎么显示（"480ms" / "自动"）
     */
    public NvgSlider(String label, double min, double max, double step,
                     Supplier<Double> value, Consumer<Double> onChange,
                     Function<Double, String> format) {
        super(label);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
        this.onChange = onChange;
        this.format = format;
    }

    @Override
    public String value() {
        return format.apply(value.get());
    }

    /** 轨道左缘（盒内坐标）：整块控件留一点边，圆钮才不会顶到框外。 */
    private static float trackX() {
        return 4f;
    }

    /** 轨道宽度（盒内坐标）。 */
    private static float trackW(float boxW) {
        return Math.max(1f, boxW - 8f);
    }

    /** 轨道纵坐标（盒内坐标）。 */
    private static float trackY(float boxH) {
        return boxH - 5f;
    }

    /** 点在滑条上就跳到那儿。命中已由调用方判过（行内控件是树），这里只管手感。 */
    @Override
    public boolean press(Rect box, double mouseX, double mouseY, int button) {
        if (!super.press(box, mouseX, mouseY, button)) {
            return false;
        }
        dragTo(box, mouseX);
        return true;
    }

    @Override
    public void drag(Rect box, double mouseX, double mouseY) {
        if (pressed) {
            dragTo(box, mouseX);
        }
    }

    private void dragTo(Rect box, double mouseX) {
        onChange.accept(snapped(SliderMath.ratio(mouseX, box.x() + trackX(), trackW(box.width()))));
    }

    private double snapped(double ratio) {
        double raw = min + ratio * (max - min);
        if (step > 0) {
            raw = Math.round(raw / step) * step;
        }
        return Math.max(min, Math.min(max, raw));
    }

    @Override
    protected void paint(PaintCtx ctx) {
        NvgPalette p = ctx.palette();
        float w = ctx.width();
        float h = ctx.height();
        double ratio = SliderMath.positionOf(value.get(), min, max);

        ctx.textCentered(format.apply(value.get()), w / 2f, 0f, p.text);

        float ty = trackY(h);
        float tx = trackX();
        float tw = trackW(w);
        // 细条厚度/圆角从调色板来（与屏幕滚动条是同一个角色，A-13 评审列的"细条 3u + 1.5u"）。
        float tt = p.trackThickness;
        float tr = p.trackRadius;
        // 轨道底（凹槽）→ 已选段（强调色）→ 圆钮：三段一眼看出"现在到哪了"
        ctx.fillRoundRect(tx, ty, tw, tt, tr, pressed ? p.wellPressed : p.well);
        float filled = (float) (tw * ratio);
        if (filled > 0.5f) {
            ctx.fillRoundRect(tx, ty, filled, tt, tr, p.accent);
        }
        float knobR = p.knobRadius;
        // **为什么这里是 5，而 NvgToggle 那颗是 4 —— 不是重复，是两条规矩**：
        // 滑条这颗是**独立圆钮**，半径自己说了算；开关那颗是**胶囊内切**，必须比胶囊半径小 1，
        // 否则圆的切线顶出胶囊边。A-13 评审判定"硬捏成一个数会改外观"，所以两个数各留各的 ——
        // 别下一次又当成重复收一遍（理由见 NvgToggle 同一处的注释）。
        // 钮面两色走调色板（A-13 评审列的"钮面颜色对"）：与 NvgToggle 是同一个角色，
        // 从前两处各写一遍 0xFFFFFFFF / 0xFFD5DAE5 —— 值一个字没改。
        ctx.circle(tx + filled, ty + tr, knobR,
                // 焦点不在这一档里（A-15）：钮面只反映"鼠标在它上面/正被按住"，
                // 键盘选中由焦点环表达 —— 两种状态混一个颜色，玩家就分不清是哪种。
                (hovered || pressed) ? p.knobActive : p.knobIdle);
        if (hovered) {
            // 焦点不再画在这条描边上（A-15）：焦点环由框架基类画，见 NvgWidget.wellColor 的注释。
            ctx.strokeRoundRect(0f, 0f, w, h, p.radius, p.outline);
        }
    }
}
