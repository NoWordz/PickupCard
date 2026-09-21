package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.text.FontMetrics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

/**
 * <b>把 Minecraft 自己的字体喂进 Trellis 的度量层。</b>
 *
 * <p>【为什么必须有这一层】框架<b>不带字体</b>（带一份十几 MB 的 CJK 字体就不是"轻量"了），
 * 它随身的 Inter 里<b>一个汉字都没有</b> —— 度量层只能拿 {@code UnicodeFallback} 按
 * "东亚全角 = 1 em" 估宽度：布局是对的，屏幕上却是豆腐块，而且<b>什么都不报</b>。
 * 宿主手上就有一份能画中文的字体（MC 的位图图集 + unifont 兜底），把它按
 * {@link FontMetrics} 的形状喂进来，度量和字形就同源了。
 *
 * <p>【em 的基准取 MC 的行高 9】{@code GlyphAdvance} 的单位是 em（1.0 = 一个字号那么宽）。
 * MC 的字体没有"字号"这个概念，它只有一个行高；取 {@code EM = 行高} 之后，
 * {@code advance} 读出来就是"几个行高"，而宿主按 {@code fontSize = 9} 量时正好还原成像素。
 *
 * <p>【为什么 {@link #drawable()} 恒真】MC 的字体不会"画不出来"：缺字它有豆腐块兜底，
 * 那是<b>看得见</b>的失败。{@code drawable()} 存在的意义是抓"宽度对、屏幕上却什么都没有"的
 * 静默故障 —— MC 这条路上没有那种故障。
 */
public final class McFont implements FontMetrics {

    /** MC 字体的名义字号 = 它的行高（{@code Font.lineHeight}）。 */
    public static final float EM = 9f;

    /** 基线以上 / 以下的像素（MC 默认字体：行高 9 = 上 7 + 下 2）。 */
    public static final float ASCENT_PX = 7f;
    public static final float DESCENT_PX = 2f;

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    @Override
    public float advance(int codePoint) {
        // 逐码点问宽度：Font.width 自己会处理代理对，所以先把码点还原成字符串
        return font().width(new String(Character.toChars(codePoint))) / EM;
    }

    @Override
    public float ascent() {
        return ASCENT_PX / EM;
    }

    @Override
    public float descent() {
        return DESCENT_PX / EM;
    }

    @Override
    public float lineGap() {
        return 0f;
    }

    @Override
    public boolean drawable() {
        return true;
    }
}
