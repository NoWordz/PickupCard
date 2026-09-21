package com.niuqu.pickupcard.render.nvg.ui;

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

    /** 轨道左缘与宽度：整块控件留一点边，圆钮才不会顶到框外。 */
    private float trackX() {
        return x + 4f;
    }

    private float trackW() {
        return Math.max(1f, w - 8f);
    }

    private float trackY() {
        return y + h - 5f;
    }

    /** 点在滑条上就跳到那儿。命中已由调用方判过（行内控件是树），这里只管手感。 */
    @Override
    public boolean press(double mouseX, double mouseY, int button) {
        if (!super.press(mouseX, mouseY, button)) {
            return false;
        }
        dragTo(mouseX);
        return true;
    }

    @Override
    public void mouseDragged(double mouseX, double mouseY) {
        if (pressed) {
            dragTo(mouseX);
        }
    }

    private void dragTo(double mouseX) {
        onChange.accept(snapped(SliderMath.ratio(mouseX, trackX(), trackW())));
    }

    private double snapped(double ratio) {
        double raw = min + ratio * (max - min);
        if (step > 0) {
            raw = Math.round(raw / step) * step;
        }
        return Math.max(min, Math.min(max, raw));
    }

    @Override
    protected void paint(NvgUi ui) {
        NvgPalette p = ui.palette;
        double ratio = SliderMath.positionOf(value.get(), min, max);

        ui.textCentered(format.apply(value.get()), x + w / 2f, y, p.text);

        float ty = trackY();
        float tx = trackX();
        float tw = trackW();
        // 轨道底（凹槽）→ 已选段（强调色）→ 圆钮：三段一眼看出"现在到哪了"
        ui.fillRoundRect(tx, ty, tw, 3f, 1.5f, pressed ? p.wellPressed : p.well);
        float filled = (float) (tw * ratio);
        if (filled > 0.5f) {
            ui.fillRoundRect(tx, ty, filled, 3f, 1.5f, p.accent);
        }
        float knobR = p.knobRadius;
        ui.circle(tx + filled, ty + 1.5f, knobR,
                (hovered || pressed || focused) ? 0xFFFFFFFF : 0xFFD5DAE5);
        if (hovered || focused) {
            ui.strokeRoundRect(x, y, w, h, p.radius, p.outline);
        }
    }
}
