package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.ui.widget.PaintCtx;
import dev.e33.trellis.ui.widget.Widget;
import dev.e33.trellis.ui.widget.WidgetPalette;

/**
 * 网格里的一格：<b>纯色底 + 居中文字</b>（占位格，不是真卡面）。
 *
 * <p>【为什么是占位，不是真卡面】A-17 已经撞过一次：Trellis 的渲染层 L4 <b>画不了位图、
 * 也画不了 MC 物品</b>（{@code Canvas.drawImage} 没有源子矩形、MC 的物品渲染要 {@code GuiGraphics}）。
 * 真卡面要么补 L4、要么把那一笔留在宿主 —— 两条都会把"验第三个形态"漂成"造 L4"。
 * 本轮（2026-09-22 用户拍板）刻意用占位格避开它：这一轮要逼出来的是<b>布局与树</b>的缺件，
 * 不是渲染层的。真卡面留给下一轮。
 *
 * <p>【为什么继承 {@link Widget} 而不是 {@code Component}】两条，缺一不可：
 * <ol>
 *   <li>格子里的字要跟控件一起、在<b>同一个盒子里</b>登记绘制。{@code Component} 拿不到
 *       "这一帧的宿主能力"：{@code TrellisColumn.setFrame} 的注入只认 {@code instanceof WidgetSlot}，
 *       一个自定义 {@code Component} 想要配色与字形缝，只能自己再存一份、每帧再灌一次 ——
 *       那就是把 {@code attachFrame} 抄第二遍。</li>
 *   <li>它得是一个真正被树托着的控件，才能白拿焦点环、可聚焦、悬停、按下、键转交
 *       （见 {@code WidgetSlot} 的类注释）。</li>
 * </ol>
 * 于是格子是 {@code Widget}，由 {@link WidgetSlot} 托进树。
 *
 * <p>【⚠️ 但"在树内"≠"会被裁"】这是被评审逮到的一处真错，写在这里免得下一个人再推错：
 * 格子的字走 {@code PaintCtx → GlyphPainter → NvgUi}，而 {@code NvgUi} 的文字是
 * <b>先登记、{@code close()} 时统一补画</b>的，登记时记的是 <b>{@code NvgUi} 自己的裁剪框</b>
 * —— 那个框<b>只有 {@code NvgUi.pushClip} 会写</b>。树的 {@code clipChildren} 走的是
 * {@code Canvas.clip → nvgIntersectScissor}，<b>只管 NanoVG 那批形状，管不到原版那批延迟文字</b>。
 * 所以滚动区外面必须有宿主自己包的一对 {@code pushClip/popClip}
 * （{@code CardGridScreen.render} 里那对），否则形状被裁了、<b>文字照旧画在绝对坐标上</b>，
 * 滚出视口的"卡 N"会糊到标题和提示上 —— 静止看对、滚一下才对不上。
 * {@code NvgUi} 的类注释原话就是这条：「只设一套的症状是形状被裁了、文字糊在外面」。
 *
 * <p>【已知观感】{@code WidgetSlot} 没设 {@code surface}，所以基类那层状态叠加层用直角
 * （{@code radius} 默认 0），而这一格自己画的是圆角 —— 悬停时是"圆角填充 + 直角白雾"。
 * 在小控件上看不出来，在 300×180 的格子上会明显。这是既有欠账
 * （{@code docs/plan.md} 未决清单那条 overlay 离屏对比图）的<b>第二个样本</b>，
 * 本轮<b>不改</b>默认值。
 */
public final class PlaceholderCell extends Widget {

    /** 格子正中显示的那行字（占位；真卡面落地后这里换成卡名 + 缩略图）。 */
    private final String text;

    /**
     * @param label 控件的名字（harness 按名字找它，见 {@link Widget#label()}）
     * @param text  格子正中显示什么
     */
    public PlaceholderCell(String label, String text) {
        super(label);
        this.text = text;
    }

    @Override
    public String value() {
        return text;
    }

    /**
     * 画一格。<b>几何一律从 {@code ctx} 来</b>（就是 {@code bounds()} 那个对象本身），
     * 控件自己不存 {@code x/y/w/h} —— 存了就是第二份几何，判据 1 要根除的正是它。
     *
     * <p>底色走 {@link Widget#wellColor}（按下 &gt; 悬停 &gt; 常态）：于是"这一格能被点"
     * 这件事有视觉反馈，而反馈的<b>三个色仍由宿主的调色板给</b>（{@code NvgPalette} 不改值）。
     */
    @Override
    protected void paint(PaintCtx ctx) {
        WidgetPalette p = ctx.palette();
        float w = ctx.width();
        float h = ctx.height();
        ctx.fillRoundRect(0f, 0f, w, h, p.radius(), wellColor(p));
        ctx.strokeRoundRect(0f, 0f, w, h, p.radius(), p.outline());
        // 【缩字不换行】占位文字比格宽时整体缩小，不截断 —— 与 Button 同一条口径
        // （{@code GlyphPainter.textCenteredFitted}）。字形仍由宿主落笔（MC 位图字喂不进 Canvas）。
        ctx.textCenteredFitted(text, w / 2f, (h - ctx.lineHeight()) / 2f,
                p.text(), w - 6f);
    }
}
