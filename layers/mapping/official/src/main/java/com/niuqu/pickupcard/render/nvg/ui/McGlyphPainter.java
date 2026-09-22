package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.ui.widget.GlyphPainter;
/**
 * {@link GlyphPainter} 的真机实现：<b>转发给 {@link NvgUi}</b>（MC 的字形 + 延迟批次）。
 *
 * <p>【为什么是纯转发、不做任何换算】{@code NvgUi.text*} 收的就是"绝对逻辑坐标 + 行框顶"，
 * 与 {@link GlyphPainter} 的契约逐字相同。在这里再做一次取整或加减，等于把
 * "同一份位置"变成两份 —— 接管绘制这一轮要证的正是"像素与旧路径逐点相同"。
 *
 * <p>它引用 {@code NvgUi}，而 {@code NvgUi} 碰到 {@code Minecraft} 的类，
 * 所以这个类<b>只在真机上可用</b>（离线测试喂替身，见 {@link GlyphPainter}）。
 */
public final class McGlyphPainter implements GlyphPainter {

    private final NvgUi ui;

    public McGlyphPainter(NvgUi ui) {
        this.ui = ui;
    }

    @Override
    public float lineHeight() {
        return ui.font().lineHeight;
    }

    @Override
    public float textWidth(String text) {
        return ui.textWidth(text);
    }

    @Override
    public void text(String text, float x, float topY, int argb) {
        ui.text(text, x, topY, argb);
    }

    @Override
    public void textCentered(String text, float centerX, float topY, int argb) {
        ui.textCentered(text, centerX, topY, argb);
    }

    @Override
    public void textCenteredFitted(String text, float centerX, float topY, int argb,
                                   float maxWidth) {
        ui.textCenteredFitted(text, centerX, topY, argb, maxWidth);
    }

    @Override
    public void textFitted(String text, float x, float topY, int argb, float maxWidth) {
        ui.textFitted(text, x, topY, argb, maxWidth);
    }
}
