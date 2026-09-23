package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.render.nvg.NvgCanvas;
import dev.e33.trellis.render.Canvas;
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
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.ComponentEnv;
import dev.e33.trellis.ui.ScrollContainer;
import dev.e33.trellis.ui.UiEvent;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.ui.WidgetSlot;
import java.util.List;
import dev.e33.trellis.ui.widget.GlyphPainter;
import dev.e33.trellis.ui.widget.PaintCtx;
import dev.e33.trellis.ui.widget.Widget;
import dev.e33.trellis.ui.widget.WidgetPalette;

/**
 * <b>配置列的组件树 —— 宿主与框架之间的适配器。</b>
 * （A-10 第三步"删探针"收编；原名 {@code TrellisBridge}：描框那层取证手段早删了
 * （A-6b 起树自己在真帧里画），那个名字因此名不副实。）
 *
 * <p>【它现在管什么】配置列那一棵组件树（{@link #buildColumn}）由 Trellis 建、布局、命中、
 * 绘制（悬停底 + 控件本体）；标签的<b>文本框</b>和<b>适配字号</b>由它给（{@link #labelBox} /
 * {@link #fitLabel}）；"指着哪一行、点到哪一行、谁正被按住"也从它回答
 * （{@link #controlRowAt} / {@link #pressedControlRow} / {@link #focusedControlRow}）。宿主那侧不再自己画控件、不再自己存控件
 * 几何、不再自己维护悬停；它每帧要做的只剩两条：把这一帧的信息交给它（列矩形与滚动偏移、
 * 指针位置、帧号），以及给字形落笔。
 *
 * <p>【适配器的本职只有两件】把宿主那份几何翻成 Trellis 的 {@code Rect}（{@link #layoutColumn}），
 * 把宿主的 NanoVG 上下文接成 Trellis 的 {@code Canvas}（{@link #attach} / {@link #surface}）。
 * 真正接完的时候第一件应该消失（只留一套几何），第二件应该由 L4 提供。
 *
 * <p>【为什么只覆盖一列而不是整屏】整屏换掉等于一上来就把渲染路径、事件、字体、
 * 生命周期全换了，出问题分不清是谁的锅。先证明最小的那条链：
 * <b>Trellis 算出的坐标真的出现在 MC 的帧里</b>。这一条通了，剩下的都是加法。
 *
 * <p>【字形为什么还是 MC 的】Trellis 的文字走 NanoVG + TTF（仓库里那份 Inter），
 * 而 Minecraft 的字是<b>位图图集</b>，喂不进去（{@code Canvas.drawImage} 连源矩形都没有）。
 * 所以分工是 <b>Trellis 管几何、位置与适配，字形由宿主喂</b>：
 * {@link McFont} 把 MC 的度量灌进文本层（A-7），{@link #fitLabel} 把 Trellis 量出的
 * 位置与缩放交回给宿主去 {@code drawString}。
 *
 * <p>【"组件边界拿宿主能力"那个缺口已经收掉了（A-23）】从前这一段是它的补丁：
 * {@code Component.drawContent(Canvas)} 只收得到一个画布，而画一个控件还要配色、要让宿主
 * 落笔写字、要知道"这是哪一帧"—— 于是这里每帧把 {@link Frame} 递归灌进每一格、画完再清掉。
 * 现在那是框架的机制了（{@code dev.e33.trellis.ui.ComponentEnv} + {@code WidgetSlot}）：
 * 宿主能力顺着绘制那一趟传下去，<b>那一段注入整段消失</b>，宿主只剩"把画布接进来"这一件本职。
 * （当初记的两个候选落点是"L4 的 {@code Canvas} 带宿主字形能力"或"L2 出宿主字形接口"；
 * 实际落点在 L6：{@code Canvas} 只解决字形那一半，而配色不是画布的事。）
 */
public final class TrellisColumn {

    private TrellisColumn() {
        throw new AssertionError("no instances");
    }

    /**
     * 这一列的几何口径：<b>只从 L0 Token 来，一个裸数字都不留</b>。
     *
     * <p>【为什么是一族局部变量，不是 {@code static final} 常量】从前这里是
     * {@code private static final float U = Tokens.Unit.BASE;} 加一串 {@code Tokens.* * U} ——
     * 把"基准 u"烤进了编译期常量。A-14 起 u 是<b>每帧按画布高算</b>的（{@code Units.u}），
     * 那些常量只会静默沿用基准 2（屏幕换档它不动）。所以 u 从 {@link #buildColumn} 的参数进来，
     * 每次建树现算 —— 这不是"改 token 就够了"能解决的问题，是常量必须消失。
     *
     * <p>【为什么行高/行缝要跟着 {@code Math.round}} 宿主那份行模型（{@code ConfigRows}）
     * 的行 y 走整数算术；竖直方向两边必须**逐位同**（A-4 那个 1.3px 是水平差），
     * 所以这里对 rowH / ROW_GAP 取同样的整。其余（内边距、控件上下限）保留浮点 ——
     * 它们只影响水平排列，本来就是"允许差一个边缘带"的那部分。
     */
    private static final float STEP_PAD = Tokens.Space.STEP_3;
    private static final float STEP_LABEL_MIN = Tokens.Size.LABEL_MIN_W;
    private static final float STEP_CONTROL_MIN = Tokens.Size.CONTROL_MIN_W;
    private static final float STEP_CONTROL_MAX = Tokens.Size.CONTROL_MAX_W;
    private static final float STEP_ROW_H = Tokens.Size.ROW_H;

    /**
     * 滚轮一格滚几行 —— <b>9</b>。
     *
     * <p>【这个数为什么是 9，而不是注释里那个 3】旧宿主那一行是
     * {@code itemsScroll.wheel(delta, contentHeight, ConfigRows.rowStep(u) * 3f)}，
     * 而 {@code ScrollMath.wheel} 内部<b>又乘了一次</b> {@code NvgScroll.ROWS_PER_NOTCH = 3} ——
     * 所以**实际一格滚了 9 行**，尽管那行注释写的是"一格滚三行"（代码与注释不符，谁都没发现）。
     * 评审把这个算术翻出来之后，用户 2026-09-22 拍板：**按实际行为（9 行）保留已发布手感**，
     * 注释改成实话。`TrellisWheelStepTest` 钉住这个数 —— 它从来没被任何测试钉过。
     *
     * <p>步长按"行"而不是像素给：行高随 u 变，写死像素会让同一格滚轮在大画布上滚得少、
     * 小画布上滚得多。
     */
    private static final float ROWS_PER_NOTCH = 9f;

    /**
     * 接进宿主上下文。帧由宿主开也由宿主关，这里只画。
     *
     * <p>【必须把宿主的设备倍数交进去】接进来的画布没有 {@code begin}，不交的话它按 1 算 ——
     * 设备像素对齐会退化成"对齐到整数逻辑坐标"，guiScale 3 下一条边最多挪 1.5 个设备像素
     * （A-10 第二步量到的）。见 {@code NvgCanvas.attach(long, float)}。
     */
    private static dev.e33.trellis.render.nanovg.NvgCanvas attach(NvgCanvas host, float pixelRatio) {
        return dev.e33.trellis.render.nanovg.NvgCanvas.attach(host.handle(), pixelRatio);
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
    // 组件列：命中和绘制读同一份 bounds() —— 判据 1 的真机验证就落在这一处
    // -----------------------------------------------------------------------

    /**
     * 建一棵配置列的组件树：根 = 列，每个直接子 = 一行，行内 = 标签 + 控件。
     *
     * <p>【为什么用 {@link Component} 而不是上一版那棵裸 {@code LayoutNode}】判据 1 要求
     * "绘制矩形和点击矩形必须是<b>同一个</b> {@code Rect} 对象"。裸节点有几何、没有命中，
     * 验不到那一条；换到组件层之后，{@link UiTree#hitTest} 读的是 {@link Component#bounds()}，
     * 描框画的也是它 —— 同一个对象，不是相等的两个。
     *
     * <p>树由<b>调用方持有</b>：{@link UiTree} 带着悬停/按下状态，不能每帧重建。
     *
     * <p>【控件为什么要交给树】A-10 起"指着哪一行、点到了哪一行"由树说了算
     * （见 {@link #controlRowAt}）：控件那一格是 {@link WidgetSlot}，它拿着的
     * {@link Widget} 只负责<b>行为</b>（按下/松开/悬停），几何一条都不留。
     *
     * @param controls   每行的控件；<b>{@code null} = 小节头</b>（只有标签、没有控件）。
     *                   宿主的小节头也是"占一行、没有控件"，这里必须照建 ——
     *                   给小节头也造一个控件，布局是对的，但那是宿主根本不存在的东西，
     *                   命中会凭空多出一行。
     * @param topInset   宿主第一行相对列顶的内缩（传 {@code ConfigRows.topInset(u)}）。
     *                   <b>必须由宿主交进来、当成树自己的内边距用，不能在适配器里事后补</b>。
     * @param palette    这一帧的配色 —— 取它的 {@code focusRing}（见 {@link NvgPalette#focusRing}）。
     */
    public static UiTree buildColumn(Widget[] controls, float topInset, float u,
                                     NvgPalette palette) {
        float pad = STEP_PAD * u;
        float gap = STEP_PAD * u;
        float labelMin = STEP_LABEL_MIN * u;
        float controlMin = STEP_CONTROL_MIN * u;
        float controlMax = STEP_CONTROL_MAX * u;
        // 竖直两格跟宿主取同样的整（见上面 STEP_* 的注释：竖直必须逐位同）
        float rowH = Math.round(STEP_ROW_H * u);
        float rowGap = Math.round(Tokens.Space.STEP_1 * u);

        Component root = new Box(columnStyle(topInset, pad, rowGap), false);
        for (Widget control : controls) {
            Component line = new Box(Style.row().withGap(gap).withHeight(Sizing.fixed(rowH)), false);
            line.add(new Box(Style.row().withGrow(1f).withWidth(Sizing.atLeast(labelMin))
                    .withHeight(Sizing.fixed(rowH)), true));
            if (control != null) {
                line.add(new WidgetSlot(control, Style.row()
                        .withWidth(Sizing.fraction(controlMin,
                                Tokens.Size.CONTROL_WIDTH_FRACTION, controlMax))
                        .withHeight(Sizing.fixed(rowH)), palette));
            }
            root.add(line);
        }
        // 【内边距落在内容上，不落在容器上】滚的是"一整块内容"：topInset 是内容自己的上内缩，
        // 滚起来它跟着走（宿主那份 ConfigRows.layout 也是这么算的）。挪到容器上的话，
        // 滚到下面就变成"顶边永远空着 topInset"。
        // 【A-16：滚动的几何归框架了】容器自己的矩形 = 视口（宿主给的 items），内容才是这些行。
        // 从前是宿主把整棵树按偏移上移，于是命中读的矩形和可见带在滚动时错开 —— 两头都坏，
        // 实测数据见 docs/plan.md A-16 那张表。
        // 【STRETCH 不能省】内容必须在交叉轴上被拉满：容器默认的 START 会让内容按自己的
        // 自然宽摆，于是行收窄到标签那点宽 —— 实测标签盒从 95 掉到 41、控件左缘整体左移 32px。
        // 旧的那棵树是"根被视口矩形撑满"，所以看着也一样满；换一层之后这份拉伸得明写出来。
        ScrollContainer list = new ScrollContainer(
                Style.column().withGrow(1f).withAlign(Align.STRETCH),
                (rowH + rowGap) * ROWS_PER_NOTCH);
        list.add(root);
        // u 挂到树上：{@code layoutColumn} 之后控件自己就能问"这一帧的 u 是多少"。
        return new UiTree(list, u);
    }

    /**
     * 把宿主自己那份 {@code ConfigLayout.Rect} 翻译成 Trellis 的 {@code Rect} 再布局。
     *
     * <p>两个 record 字段几乎一样却必须在这里互相翻译 —— 这正是"布局口径没统一"的证据，
     * 也是 Trellis 要收掉的东西。翻译只许发生在这一个地方（适配器的本职）；
     * 真正接的时候这段话应该消失（只留一套几何）。
     *
     * <p>【A-16 起：视口就是视口】从前这里必须把 {@code scrollOffset} 从视口上减掉
     * （框架还没有滚动容器，只能拿"内容矩形"当根）—— 于是命中读的那份矩形在滚动时
     * 和可见带错开。现在偏移由 {@link ScrollContainer} 自己持有，宿主交进来的矩形
     * 就是它本来的意思：<b>裁剪框</b>。顺带少了一份几何。
     *
     * @param grid 网格间距（{@code 1 / guiScale} = 一个设备像素）
     */
    public static void layoutColumn(UiTree ui, ConfigLayout.Rect items, float grid) {
        ui.layout(new Rect(items.x(), items.y(), items.w(), items.h()), grid);
    }

    /**
     * 这一列的滚动容器（{@link #buildColumn} 建的那棵树，<b>根就是它</b>）。
     *
     * <p>偏移、内容高、最多能滚多远都从它读：宿主画滚动条、harness 读数、
     * 标签那一趟要的偏移，全部只认这一处 —— 不再另存一份"滚到哪了"。
     */
    public static ScrollContainer scrollList(UiTree ui) {
        return (ScrollContainer) ui.root();
    }

    /**
     * 让组件树<b>自己画</b>：底板（悬停底）与控件本体都在这一趟里。
     *
     * <p>【描框为什么删了】描框只能证明"坐标算对了"。真让组件树画，同时证明两件事：
     * 画出来的矩形就是命中读的那个 {@code bounds()}（判据 1），而悬停效果来自基类那一处
     * （判据 2）。所以配置列的<b>悬停底由 Trellis 画</b> —— 宿主那边对应的画法已停手，
     * 两个都画就是"两份几何各画一条带子"，正是判据 1 要根除的东西。
     *
     * <p>【控件本体也归这一趟（A-10 第二步）】{@code WidgetSlot.drawContent} 现在真的画控件：
     * 几何用 {@code bounds()} 那个对象，形状走 Trellis 原语（每个原语自带状态 —— 判据 3），
     * 字形交回宿主（{@link GlyphPainter}：MC 的字是位图图集，喂不进 {@code Canvas.drawText}）。
     * 宿主那趟 {@code row.widget().draw(ui)} 已经删了。
     *
     * <p>【宿主能力顺着绘制那一趟传了（A-23）】这一段从前是：递归把 {@link Frame} 灌进每一格
     * （{@code setFrame}）、画完再清掉 —— 因为 {@code drawContent} 只收得到一个 {@code Canvas}。
     * 现在配色与字形由 {@link ComponentEnv} 顺着 {@code UiTree.draw} → {@code Component.draw}
     * 传到每个组件，{@code WidgetSlot} 在自己家里问 {@code env()} 拿：<b>那一整段注入消失了</b>。
     *
     * <p>【层序：控件本体没挪，悬停底挪进裁剪里了】控件本体画在宿主从前那趟控件循环的位置
     * （配置项的滚动裁剪里、标签标记之后、滚动条之前）—— 与旧路径一致。**悬停底变了**：
     * 它从前在 {@code drawChrome} 那一趟画（配置项的裁剪<b>之外</b>），现在跟树一起进了裁剪 ——
     * 顺带修掉"滚出视口的行，悬停带还糊在标签列/预览面板上"，那一类正是裁剪该管的事。
     *
     * <p>表面由调用方给（一帧一个，见 {@link #surface}）：接宿主上下文这件事只做一次。
     */
    public static void paint(Frame frame, UiTree ui) {
        ui.draw(frame.canvas(), frame.env());
    }

    /**
     * 这一帧的"表面"：画布 + 宿主能力（配色与字形缝）。
     *
     * <p>【时刻为什么不在这里了】（A-23）从前它在这里多传了一份。
     * ⚠️ <b>补正一句本喵当场写错的话</b>：那两个时刻<b>并不相等</b> —— 这里是<b>毫秒</b>
     * （{@code System.currentTimeMillis()}），而树那条 {@code UiTree.tick} 收的是<b>纳秒</b>
     * （宿主传 {@code now * 1_000_000L}）。所以那不是"同一个值传两处"，是"两个不同单位的
     * 时刻各传一处"，比前者更糟。现在只剩树上那一份（{@code Component.nowNanos()}），
     * 于是<b>控件的"帧号"单位跟着变成了纳秒</b> —— 谁问 {@code Widget.paintedIn(frame)}
     * 就必须拿树上那个值（真机第 62 轮的自检报"17 个控件只画了 10 个"就是这个单位差）。
     */
    public record Frame(Canvas canvas, ComponentEnv env) {

        /**
         * 给<b>树外</b>控件的绘制上下文：标签列与切样例芯片那一族。
         *
         * <p>【谁真的在树外】全仓 {@code ctxFor} 只有 {@code PickupCardConfigScreen} 那两处 ——
         * 配置屏的 4 个页签与 5 颗样例芯片，它们的几何由宿主的 {@code ConfigLayout} 给
         * （{@code chipBoxes}），还不归树管。⚠️ <b>编辑场那两颗按钮不在这一列</b>：它们是以
         * {@code WidgetSlot} 建进树的（见 {@code AnchorEditScreen}），整帧只走
         * {@code TrellisColumn.paint}，从不调这里（本喵第一版 javadoc 把它们也写进来了，评审逮到）。
         *
         * <p>【为什么时刻要传进来】树里那一份时刻由树给（{@code Component.nowNanos()}，
         * 来源是 {@code UiTree.tick}，<b>纳秒</b>），而树外的控件不在树里 —— 它们的排布者手里
         * 就有这一帧的时刻，直接交过来。两边必须是同一个值：控件的"帧号"只有这一个来源
         * （单位是纳秒，见 {@code PaintCtx.now}）。
         */
        public PaintCtx ctxFor(Rect box, long now) {
            return new PaintCtx(box, canvas, env.palette(), env.glyphs(), now);
        }
    }

    /**
     * 这一帧的"表面"：把宿主的画布接进来（<b>带上 GUI 倍数</b>），再配上宿主能力。
     *
     * <p>【为什么倍数必须交进去】见 {@link #attach}：不交它按 1 算，设备像素对齐会退化成
     * "对齐到整数逻辑坐标"（guiScale 3 下一条边最多挪 1.5 个设备像素）。
     *
     * <p>【为什么一帧只建一个】接进宿主上下文这件事没有副作用、但没必要做第二遍：
     * 树内控件与树外控件画的是同一帧、同一个上下文。
     */
    public static Frame surface(NvgCanvas host, WidgetPalette palette, GlyphPainter glyphs,
                                float pixelRatio) {
        return new Frame(attach(host, pixelRatio), new ComponentEnv(palette, glyphs));
    }

    // -----------------------------------------------------------------------
    // 几何：行内控件的盒子只有树这一个出处（A-10 第二步起宿主不再存一份）
    // -----------------------------------------------------------------------

    /**
     * 装行的那个组件：<b>滚动容器的唯一子节点</b>。
     *
     * <p>【为什么要有这两个助手】A-16 起树的根是滚动容器，行在它下面又深了一层 ——
     * "根的孩子就是行"这句话不再成立。取行只许走这里：别处再写一遍
     * {@code root().children()} 会拿到容器那一层，症状是 {@code IndexOutOfBounds} 或者
     * 更坏的"行号整体错位一格"。
     */
    private static Component rowHolder(UiTree ui) {
        return ui.root().children().get(0);
    }

    /** 行那一层（小节头也是行，位置与 {@code controls} 下标一一对应）。 */
    private static List<Component> rowsOf(UiTree ui) {
        return rowHolder(ui).children();
    }

    /**
     * 第 {@code rowIndex} 行<b>控件那一格</b>的几何；那一行是小节头（没有控件）时返回 {@code null}。
     *
     * <p>【为什么宿主不再自己算】从前控件自己带一份 {@code x/y/w/h}（宿主的整数取整版），
     * 与树里的盒子差 1.3 逻辑 px（A-4）—— 拖拽、harness 的坐标、日志里的读数全都要用它，
     * 于是"差一点"会同时出现在三个地方。现在只有 {@code bounds()} 这一个对象。
     *
     * <p>返回的是 {@code bounds()} <b>那个对象本身</b>，不是另算一个相等的矩形（判据 1）。
     */
    public static Rect controlBox(UiTree ui, int rowIndex) {
        Component line = rowsOf(ui).get(rowIndex);
        return line.children().size() < 2 ? null : line.children().get(1).bounds();
    }

    /**
     * 正<b>被按住</b>的那一行控件（没有就是 {@code -1}）。
     *
     * <p>【拖拽为什么要问它】"谁正被按着"这件事在树里（按下发给命中的那个组件），
     * 宿主不该再维护第二份。滑条拖拽只需要两样：哪一行、那一行的盒子 —— 都从这里出去。
     */
    public static int pressedControlRow(UiTree ui) {
        List<Component> lines = rowsOf(ui);
        for (int i = 0; i < lines.size(); i++) {
            Component line = lines.get(i);
            if (line.children().size() >= 2
                    && line.children().get(1) instanceof WidgetSlot slot
                    && slot.pressed()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 键焦点落在<b>哪一行的控件</b>上（不在任何控件上就是 {@code -1}）。
     *
     * <p>【为什么它可以只问树】焦点只有一份真相（{@link UiTree#focused()}），控件那一份是
     * 被同步过去的 —— 所以"哪一行拿着键盘"不需要再看任何控件自己的布尔值。
     * 与 {@link #pressedControlRow} 同一个形状：读树、翻出它落在第几个 {@code WidgetSlot} 上。
     *
     * <p>调用方：harness 的键盘路由读数（"焦点行"这个数只能从这里出来）。
     */
    public static int focusedControlRow(UiTree ui) {
        Component focused = ui.focused();
        if (focused == null) {
            return -1;
        }
        List<Component> lines = rowsOf(ui);
        for (int i = 0; i < lines.size(); i++) {
            List<Component> cells = lines.get(i).children();
            if (cells.size() >= 2 && cells.get(1) == focused) {
                return i;
            }
        }
        return -1;
    }

    // -----------------------------------------------------------------------
    // 命中：指着哪一行、点到哪一行，都从这棵树回答
    // -----------------------------------------------------------------------

    /**
     * 指针指着<b>哪一行的控件</b>（不在任何控件上就是 {@code -1}）。
     *
     * <p>【为什么只认控件那一半】标签那一半不是点击目标，也不是悬停说明的对象 ——
     * 宿主的语义一直是"指着控件才算指着这一行"，这里照着它来（换语义是另一件事，
     * 得连着悬停底一起谈）。读的是 {@link UiTree#hitTest} → {@link Component#bounds()}，
     * 也就是命中和绘制共用的那一个矩形。
     *
     * <p>调用方：宿主的悬停缓动、底部说明、点击路由（A-10）。
     */
    public static int controlRowAt(UiTree ui, float x, float y) {
        Component hit = ui.hitTest(x, y);
        if (hit == null) {
            return -1;
        }
        Component holder = rowHolder(ui);
        Component line = hit;
        while (line.parent() != null && line.parent() != holder) {
            line = line.parent();
        }
        if (line.parent() != holder || line.children().size() < 2) {
            return -1;      // 小节头那一行没有控件
        }
        return line.children().get(1).isAncestorOf(hit) ? holder.children().indexOf(line) : -1;
    }

    /**
     * 把<b>树里的悬停状态</b>推给控件（{@link Widget#hover(boolean)}）。
     *
     * <p>悬停从此只有一份真相：树按 {@code pointerMove} 判，控件只是接收者。
     * 宿主那边每帧那一趟 {@code widget.mouseMoved(...)} 因此不再需要（树外的控件除外）。
     */
    public static void syncHover(UiTree ui) {
        pushHover(ui.root());
    }

    private static void pushHover(Component component) {
        if (component instanceof WidgetSlot slot) {
            slot.widget().hover(component.hovered());
        }
        for (Component child : component.children()) {
            pushHover(child);
        }
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
        Component line = rowsOf(ui).get(rowIndex);
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

    private static Style columnStyle(float topInset, float pad, float rowGap) {
        // 上 topInset / 下 0：底下那点不是留白，加了只会把列撑高。
        return Style.column().withPadding(Insets.of(topInset, pad, 0f, pad))
                .withGap(rowGap).withAlign(Align.STRETCH);
    }

    /**
     * 占位盒子：只占位，不画内容 —— 几何与命中由基类负责。
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
