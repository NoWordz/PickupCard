package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.layout.LayoutSettings;
import net.minecraft.client.gui.Font;

/**
 * 一张卡该多大。跟排布分开是因为它需要字体（量文字宽度），而排布是纯数学。
 * <p>
 * 【结构】三段式：<b>稀有度竖条 | 物品图标格 | 名字+数量框</b>。三个框等高，
 * 高度由主题的图标倍率与垂直内边距推出来（{@code StyleModel.boxHeight()}）。
 * <p>
 * 【卡宽跟着内容走】这也是上一版走"烘位图贴图"路线最终失败的原因：贴图定宽，
 * 卡变宽只能横拉，圆角和描边全被重采样糊掉。所以这里按"现量现算"写。
 * <p>
 * 【上限】卡宽不超过 {@link #MAX_WIDTH_RATIO} × 屏宽。这个比例不是随手取的：
 * 之前定的 45% 是在"卡片左缘固定"的前提下算的（要给右边留空间），现在默认改成
 * 右缘固定了，那个约束就不存在了，唯一的要求只剩"别占满整屏"。
 * <p>
 * 【文本从哪来】名字与数量的文本、宽度、截断结果一律走这张卡的
 * {@link CardTextCache}（{@link CardView#text()}）：这里与绘制路每一帧各问一次，
 * 而它们在一张卡的一生里几乎不变 —— 从前每问一次都要新建 Component、逐码点量宽。
 */
public final class CardMetrics {

    /** 原版物品图标是 16×16，卡上的实际大小再乘主题里的倍率。 */
    public static final float ICON_PX = 16f;

    /**
     * 卡宽上限占屏宽的比例。
     * <p>
     * 【为什么是引用而不是再来一个 0.70】它和竖条锚点的"要给右边留多少"是<b>同一个数</b>：
     * 卡宽上限比锚点预留宽更大的话，每隔一段时间就会出现"卡宽到顶、竖条被软目标顶歪"的
     * 组合。两个地方各写一份，调了一处另一处不会跟着动 —— 上一版就是这么漂的。
     */
    public static final float MAX_WIDTH_RATIO = LayoutSettings.CONTENT_WIDTH_RATIO;

    private CardMetrics() {
    }

    /** 三个框的统一高度（已按缩放放大到屏幕像素）。 */
    public static float height(CardCanvas canvas, Font font) {
        return canvas.style().boxHeight() * canvas.scale();
    }

    /**
     * 这张卡允许的最大宽度（屏幕像素）= 屏宽比例上限。
     * <p>名字比这更长就截断加省略号 —— 收的是"名字"，不是字号（数量与图标才是卡上要一眼
     * 读到的东西，缩字体比截名字更伤）。
     */
    public static float maxWidth(CardCanvas canvas) {
        return canvas.guiWidth() * MAX_WIDTH_RATIO;
    }

    /**
     * 总宽 = 竖条 + 间隙 + 图标格 + 间隙 + 信息框。
     * <p>
     * 【信息框里有什么，由"显示物品名"决定】开着是「名字 + 间隙 + 数量」，关掉就只剩数量
     * —— 卡会明显变窄，而"竖条 + 图标 + 数量"这个最小组合仍然一眼能读。
     */
    public static float width(CardCanvas canvas, Font font, CardView view) {
        return naturalWidth(canvas, font, view) * canvas.scale();
    }

    /**
     * 同一张卡在 <b>100%</b> 下的宽度（不乘当前缩放）。
     * <p>
     * 【为什么要单独有一个】缩放在"知道卡有多宽"之前就得定下来（右侧条带装不下时要按宽度
     * 再收一次，见 {@code CardStage#layout}），而 {@link #width} 里已经乘过缩放了 ——
     * 用 {@code width()/scale} 反推的话，改缩放的那一刻就会拿错值。分成两个函数之后，
     * "未缩放宽度"只有一个出处。
     */
    public static float naturalWidth(CardCanvas canvas, Font font, CardView view) {
        var style = canvas.style();
        float gap = style.gap();
        CardTextCache text = view.text();
        text.update(canvas, font, view);
        // 数字滚动中按"旧值/新值里宽的那个"占位，否则 1 → 10 会在滚到一半时把卡撑宽
        float infoBox = style.paddingH() * 2f + text.countWidth();
        if (canvas.settings().showItemName()) {
            infoBox += gap + text.fittedWidth();
        }
        return style.barWidth() + gap + style.boxHeight() + gap + infoBox;
    }


    /**
     * 一张卡里<b>名字以外</b>固定要占的宽：竖条 + 间隙 + 图标格 + 间隙 + 左右内边距 + 数量。
     * <p>【为什么要单独成函数】名字的截断预算（这里）与配置预览面板的截断预算
     * （{@code PickupCardConfigScreen}，卡壳被面板夹窄时）要用<b>同一把尺</b>——
     * 各写一份的话"预览的名字溢出卡壳"那种错就会回来（2026-09-19 真踩过）。
     * <p>【热路径不走这里】跑着的卡走 {@link CardTextCache}（数量宽度已经量过了），
     * 这个函数是给"手上只有一个数量的调用方"（配置预览的探针）用的。
     */
    public static float namelessWidth(CardCanvas canvas, Font font, int count) {
        var style = canvas.style();
        float gap = style.gap();
        return style.barWidth() + gap + style.boxHeight() + gap
                + style.paddingH() * 2f + gap + font.width(canvas.countText(count));
    }
}
