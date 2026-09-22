package com.niuqu.pickupcard.render.nvg.ui;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 开关：左边一句「开 / 关」，右边一颗滑到两头的<b>圆钮</b>。
 *
 * <p>【为什么不用按钮显示"开/关"】按钮的语义是"点一下做一件事"，开关的语义是"现在是什么状态"。
 * 两者画得一样的话，玩家得先读字才知道能不能点；圆钮一摆，状态和可点性一眼都在。
 * 这也是这一层存在的意义：原版按钮画不出圆钮（它是九宫格贴图），NanoVG 一行就够。
 *
 * <p>【几何从 {@code ctx.box()} 来（A-10 第二步）】整个绘制体只有这一处几何来源 ——
 * 也就是"把这段绘制代码复制到滑条上不会串位置"的原因。
 */
public final class NvgToggle extends NvgWidget {

    private final Supplier<Boolean> state;
    private final Consumer<Boolean> onChange;

    public NvgToggle(String label, Supplier<Boolean> state, Consumer<Boolean> onChange) {
        super(label);
        this.state = state;
        this.onChange = onChange;
    }

    @Override
    public String value() {
        // 【文案走语言文件】shared 碰不到客户端 I18n，走 Component（与 CardMetrics 同款）；
        // 硬编码中文在英文客户端就是漏网的原文（英文截图抓到"开/关"混在英文界面里）。
        return Component.translatable(state.get()
                ? "pickupcard.config.toggle.on" : "pickupcard.config.toggle.off").getString();
    }

    @Override
    protected void onActivate() {
        onChange.accept(!state.get());
    }

    @Override
    protected void paint(PaintCtx ctx) {
        NvgPalette p = ctx.palette();
        float w = ctx.width();
        float h = ctx.height();
        boolean on = state.get();
        float pillW = Math.min(26f, w * 0.32f);
        float pillH = Math.max(8f, h - 8f);
        float pillX = w - pillW - 2f;
        float pillY = (h - pillH) / 2f;
        float r = pillH / 2f;

        // 开/关两个字在"轨道以左的自由区"里居中 —— 顶在最左边时，宽控件上像一行字掉队了
        ctx.text(value(), (pillX - ctx.textWidth(value())) / 2f,
                (h - ctx.lineHeight()) / 2f, p.text);

        // 轨道
        ctx.fillRoundRect(pillX, pillY, pillW, pillH, r, on ? p.accent : p.well);
        if (!on) {
            ctx.strokeRoundRect(pillX, pillY, pillW, pillH, r, p.outline);
        }
        // 钮：开在右边、关在左边（圆钮位置本身就是状态）
        // 钮面两色走调色板（A-13 评审列的"钮面颜色对"）：与 NvgSlider 是同一个角色，
        // 从前两处各写一遍 0xFFFFFFFF / 0xFFD5DAE5 —— 值一个字没改。
        float knobX = on ? pillX + pillW - r : pillX + r;
        // **半径 = 胶囊半径 - 1（基准下 4）—— 上面那两行说的是"色"同角色，半径不是**：
        // 这条钮是**胶囊内切**，必须比胶囊半径小 1，否则圆的切线顶出胶囊边（屏幕越大越明显）；
        // 滑条那颗是**独立圆钮**，不受任何胶囊约束（基准下 5）。A-13 评审判定"两条不同的规矩"，
        // 硬捏成一个数会改外观 —— 见 NvgSlider 同一处的注释。
        ctx.circle(knobX, pillY + r, r - 1f, on ? p.knobActive : p.knobIdle);
        if (hovered) {
            // 焦点不再画在这条描边上（A-15）：焦点环由框架基类画，见 NvgWidget.wellColor 的注释。
            ctx.strokeRoundRect(0f, 0f, w, h, p.radius, p.outline);
        }
    }
}
