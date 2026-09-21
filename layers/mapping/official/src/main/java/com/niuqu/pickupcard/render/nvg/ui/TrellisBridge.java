package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.geom.Point;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Align;
import dev.e33.trellis.layout.ContentSizer;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.text.TextAlign;
import dev.e33.trellis.text.TextLayout;
import dev.e33.trellis.text.TextMeasurer;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.UiTree;

/**
 * <b>Trellis 试点：把 Trellis 接进 PickupCard 已有的 NanoVG 上下文。</b>
 *
 * <p>【它现在管什么】配置列那一棵组件树（{@link #buildColumn}）由 Trellis 建、布局、命中、
 * 画悬停底；标签的<b>文本框</b>和<b>适配字号</b>也由它给（{@link #labelBox} / {@link #fitLabel}）。
 * 描框那层取证手段已经删了 —— 组件树自己在真帧里画（A-6b）。
 *
 * <p>【为什么先做这个而不是整屏】整屏换掉等于一上来就把渲染路径、事件、字体、
 * 生命周期全换了，出问题分不清是谁的锅。先证明最小的那条链：
 * <b>Trellis 算出的坐标真的出现在 MC 的帧里</b>。这一条通了，剩下的都是加法。
 *
 * <p>【字形为什么还是 MC 的】Trellis 的文字走 NanoVG + TTF（仓库里那份 Inter），
 * 而 Minecraft 的字是<b>位图图集</b>，喂不进去（`Canvas.drawImage` 连源矩形都没有）。
 * 所以分工是 <b>Trellis 管几何、位置与适配，字形由宿主喂</b>：
 * {@link McFont} 把 MC 的度量灌进文本层（A-7），{@link #fitLabel} 把 Trellis 量出的
 * 位置与缩放交回给宿主去 {@code drawString}。
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
     * "装下了没有"留的浮点余量（逻辑 px）。
     *
     * <p>缩到<b>刚好</b>装下时，{@code shrinkToFit} 量回来的宽会比盒宽多出百万分之几
     * （每个 advance 都乘过一次浮点）—— 那不是"装不下"。拿它去触发截断的后果很难看：
     * 整串字平白少掉最后几个字。真装不下（地板顶住）差的是好几个 px，量级差三个数量级。
     *
     * <p>【这不是猜的】第 14 轮真机就是这么现形的：`Entrance style` 缩到 0.978 正好贴合，
     * 却被打成"截→Entrance sty..."。
     */
    private static final float FIT_SLACK = 0.01f;

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
     * <p>【{@code scrollOffset} 为什么要从视口上去掉】宿主的滚动 = <b>内容整体上移 N px</b>
     * （{@code ConfigRows.layout} 里的 {@code - Math.round(scrollOffset)}）。
     * Trellis 现在<b>还没有</b>"滚动容器"这个组件，所以适配器只能把<b>视口</b>上移同样多 ——
     * 行于是落到宿主那一份位置上（根仍然"正好等于给定矩形"，只是这个矩形是内容矩形，不是裁剪框）。
     * 真正的解法是 L4 的滚动容器 + 自带裁剪，那是后面的事；在那之前，
     * <b>不传这个数就是两份几何在滚动时错开 N px</b>（悬停底、命中、标签全错）。
     *
     * @param scrollOffset 宿主当前的滚动偏移（逻辑 px，宿主自己会 {@code Math.round} 它）
     * @param grid 网格间距（{@code 1 / guiScale} = 一个设备像素）
     */
    public static void layoutColumn(UiTree ui, ConfigLayout.Rect items, float scrollOffset,
                                    float grid) {
        ui.layout(new Rect(items.x(), items.y() - scrollOffset, items.w(), items.h()), grid);
    }

    /**
     * 让组件树<b>自己画</b>。
     *
     * <p>【描框为什么删了】描框只能证明"坐标算对了"。真让组件树画，同时证明两件事：
     * 画出来的矩形就是命中读的那个 {@code bounds()}（判据 1），而悬停效果来自基类那一处
     * （判据 2）。所以这一版起，配置列的**悬停底由 Trellis 画** —— 宿主那边对应的画法已停手，
     * 两个都画就是"两份几何各画一条带子"，正是判据 1 要根除的东西。
     *
     * <p>对齐由调用方在 {@link UiTree#layout(Rect, float)} 那一趟做：对在布局层，
     * 这里画出来的本来就是设备整数，渲染层不必也不需要再挪。
     */
    public static void paint(NvgCanvas host, UiTree ui) {
        ui.draw(attach(host));
    }

    // -----------------------------------------------------------------------
    // 标签列：文本框与适配字号都从这里出去，字形由宿主画
    // -----------------------------------------------------------------------

    /**
     * 第 {@code rowIndex} 行的<b>标签文本框</b> —— 树里每行的第 1 个孩子（有控件的那行，
     * 第 2 个孩子才是控件）。
     *
     * <p>【为什么把盒子交出去，而不是让宿主接着手算 {@code labelX() + 3 / yAt + 5}】
     * 手算的那一份和树里的这一份必然漂：树里已经按设备网格对齐过、已经扣掉了控件与间距、
     * 也已经按宿主给的滚动偏移挪过。文字要画在树里，就必须读同一个盒子。
     *
     * <p>返回的是 {@code bounds()} <b>那个对象本身</b>，不是另算一个相等的矩形 ——
     * 判据 1 写在矩形上，文字的锚点也该是同一份几何。
     */
    public static Rect labelBox(UiTree ui, int rowIndex) {
        Component line = ui.root().children().get(rowIndex);
        return line.children().get(0).bounds();
    }

    /**
     * 把一行标签的<b>位置、字号与串</b>算出来交给宿主画（字形仍由宿主提供，见 A-7）。
     *
     * <p>【适配为什么问度量层，而不是宿主自己算宽度比】装不下时"整体缩小"这条路上有两个
     * 选择：宿主自己算 {@code k = maxW / tw}（没有下限，长文案一路缩到看不清），
     * 或者问 {@link TextMeasurer#shrinkToFit}（重测一个更小的字号 + <b>地板</b>）。
     * 后者知道得更多，而且它的口径写在签名里（{@code minFontSize}）——
     * 到了地板就不再缩，<b>宁可溢出</b>：溢出看得见，缩到看不见没有东西会报。
     *
     * <p>【到地板还装不下就截断（{@link TextMeasurer#ellipsize}）】这一档是 A-8 真机
     * 逼出来的：MC 的正文就是 9px、地板 8px 只留 11% 的缩字余量，英文长标签会一直
     * 压到控件上 —— 那从玩家眼里看是绘制 bug，不是"框架的取舍"。截断把"越界"换成
     * "少几个字 + 省略号"，而这一行是什么还能从宿主的悬停说明读回来。
     * <b>截断按字形簇切</b>（组合记号不会被剁下来），切点与省略号的宽度都是量出来的。
     *
     * <p>【垂直位置问 {@code TextAlign}】基线不是 {@code y - h / 2} 手算出来的
     * （那个 {@code h} 到底是字高、行高还是墨迹高，每人理解不同）——
     * {@link TextAlign#baseline} 按<b>行框</b>居中，换字体、混排 CJK 都不用重对。
     *
     * @param measurer    度量器（宿主喂的字体，真机上是 {@link McFont}）
     * @param fontSize    基准字号（MC 的正文就是 {@code McFont.EM}）
     * @param minFontSize 缩字地板
     * @param ellipsis    截断时接在末尾的串（由宿主给：形如 {@code "..."} 或 {@code "…"}，
     *                    能不能画出来是字体的事，不是度量层的事）
     */
    public static LabelFit fitLabel(TextMeasurer measurer, String text, Rect box,
                                    float fontSize, float minFontSize, String ellipsis) {
        TextLayout fitted = measurer.shrinkToFit(text, fontSize, box.width(), minFontSize);
        boolean overflow = fitted.width() > box.width() + FIT_SLACK;
        LabelFit.Mode mode = fitted.fontSize() < fontSize
                ? LabelFit.Mode.SHRUNK : LabelFit.Mode.ORIGINAL;
        if (overflow) {
            fitted = measurer.ellipsize(text, fitted.fontSize(), ellipsis, box.width());
            mode = LabelFit.Mode.ELLIPSIZED;
            overflow = fitted.width() > box.width() + FIT_SLACK;
        }
        Point baseline = TextAlign.baseline(box, fitted, TextAlign.H.START, TextAlign.V.CENTER);
        float scale = fitted.fontSize() / fontSize;
        // MC 的 drawString 吃"行框顶"、不是基线：从基线往上退<b>它自己的</b> ascent（缩放后那份）。
        // DESCENT 那边不用管 —— 行框高度由 MC 的 9 决定，与 fontSize 只差一个 scale。
        return new LabelFit(fitted.text(), baseline.x(),
                baseline.y() - McFont.ASCENT_PX * scale, scale, fitted.width(), mode, overflow);
    }

    /**
     * 一行标签的绘制参数（见 {@link #fitLabel}）。
     *
     * @param text     要画的那个串 —— <b>截断时带省略号</b>，不一定等于那一行的标签
     * @param x        文本行框左端
     * @param top      交给 MC {@code drawString} 的 y（行框顶）
     * @param scale    字形缩放倍数（= 适配字号 / 基准字号）
     * @param width    Trellis 量出来的字宽（宿主画出来的墨迹应当与它相等 —— A-7 那条对账）
     * @param mode     走了哪一档：原字号 / 缩小 / 截断
     * @param overflow 最终<b>还是越界</b>了没有（只有"连省略号都放不下"那一种可能）——
     *                 宿主据此才知道该改文案还是改布局。判定用的余量见 {@link #FIT_SLACK}，
     *                 所以宿主不必也不许再自己拿宽去比一次。
     */
    public record LabelFit(String text, float x, float top, float scale, float width, Mode mode,
                           boolean overflow) {

        /** 适配的三档。日志与验收都按它说话，比两个 boolean 读得清楚。 */
        public enum Mode {
            /** 原字号就装得下。 */
            ORIGINAL,
            /** 缩到更小的字号（在地板之上）才装下。 */
            SHRUNK,
            /** 缩到地板还装不下，截断 + 省略号。 */
            ELLIPSIZED
        }
    }

    private static Style columnStyle(float topInset) {
        // 上 topInset / 下 0：底下那点不是留白，加了只会把列撑高。
        return Style.column().withPadding(Insets.of(topInset, PAD, 0f, PAD))
                .withGap(ROW_GAP).withAlign(Align.STRETCH);
    }

    /**
     * 探针用的最小盒子：只占位，不画内容 —— 几何与命中由基类负责。
     *
     * <p>【悬停底是基类画的】这一版起"指到哪一行"的视觉来自 {@link Component} 那处
     * 唯一的实现（判据 2），这里的 {@code drawContent} 因此是空的。
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
            // 内容为空：底板与悬停底都由基类画（判据 2）。
        }
    }
}
