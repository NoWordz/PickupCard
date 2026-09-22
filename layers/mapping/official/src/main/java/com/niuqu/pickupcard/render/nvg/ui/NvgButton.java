package com.niuqu.pickupcard.render.nvg.ui;

import java.util.function.Supplier;

/**
 * 一个按钮：<b>显示当前值，点一下做一件事</b>。
 *
 * <p>【为什么"循环选择"没有单独的类】旧版把 {@code CycleWidget} 单列一个类，但它和按钮的差别
 * 只有"点击做什么"：按钮是"做一件事"，循环是"换成下一个"。把动作交给构造参数之后，
 * 一个类就够了 —— 控件类按"长得不一样"分，不按"点击语义"分。
 *
 * <p>【几何从 {@code ctx.box()} 来（A-10 第二步）】见 {@link NvgToggle} 的同款说明。
 */
public final class NvgButton extends NvgWidget {

    private final Supplier<String> value;
    private final Runnable action;

    /**
     * @param value  现在显示什么（点完要重新问一次，所以是 Supplier 不是 String）
     * @param action 点一下做什么；{@code null} = 只读控件（画得暗一点，点了没反应）
     */
    public NvgButton(String label, Supplier<String> value, Runnable action) {
        super(label);
        this.value = value;
        this.action = action;
    }

    @Override
    public String value() {
        return value.get();
    }

    @Override
    protected void onActivate() {
        if (action != null) {
            action.run();
        }
    }

    @Override
    protected void paint(PaintCtx ctx) {
        NvgPalette p = ctx.palette();
        float w = ctx.width();
        float h = ctx.height();
        if (action == null) {
            // 只读：底更暗、不画描边 —— "这里有个东西，但它现在点不动"
            ctx.fillRoundRect(0f, 0f, w, h, p.radius, p.panel);
        } else {
            ctx.well(0f, 0f, w, h, wellColor(p));
        }
        int textColor = action == null ? p.textDim : p.text;
        // 【缩字不换行】值比盒宽就整体缩小（见 {@link GlyphPainter#textCenteredFitted}）——
        // 英文的循环选项值（"Same name + enchants"）在等分行布局里必然超宽，
        // 截断会让玩家认不出当前档位
        ctx.textCenteredFitted(value.get(), w / 2f, (h - ctx.lineHeight()) / 2f,
                textColor, w - 6f);
    }
}
