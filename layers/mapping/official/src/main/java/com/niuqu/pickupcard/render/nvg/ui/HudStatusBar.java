package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.render.Canvas;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.Label;
import dev.e33.trellis.ui.Surface;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.ui.WidgetSlot;
import dev.e33.trellis.ui.widget.Button;
import dev.e33.trellis.ui.widget.WidgetPalette;

import java.util.function.Supplier;

/**
 * <b>第四个形态：常驻 HUD 的建树器</b>（A-24，2026-09-23）。
 *
 * <p>【为什么这一形态和前三个不是一回事】配置列 / 编辑场 / 网格<b>都在 {@code Screen} 里</b>，
 * 于是它们共享一整套前提：MC 会调 {@code init} 也会调 {@code removed}、有 resize 回调、
 * 每帧拿得到鼠标坐标、有地方调 {@code tick}、键鼠事件一定送得到。HUD 把这些前提
 * <b>全部拿掉</b>：没有生命周期回调、没有任何输入、F1（{@code hideGui}）时要整条消失。
 * 所以这一轮的产出不是"又一个界面"，而是<b>"框架在拿掉这些前提之后还成不成立"</b>的读数。
 *
 * <p>【它是只读的，而且这是设计不是缺口】HUD 收不到指针与键盘，所以这棵树上
 * {@code hitTest} / 焦点 / hover / 拖拽<b>整片用不上</b>。要验的恰恰是：框架会不会
 * <b>强迫</b>调用方走那条路（"不 tick 就不对"、"没有指针就炸"这类）。
 *
 * <p>【为什么建树器在 {@code Screen} 外面】与 {@link CardGridTree} 同一条理由：
 * "视口多大进去、每个盒子在哪出来"是<b>纯计算</b>；关在 {@code Screen} 里就只能靠真机截图
 * 才发现"面板压住了血条"。本类<b>不调用 MC</b>，所以 {@code HudStatusBarTest} 全程离线。
 *
 * <p>【树形】
 * <pre>
 * 根 Box（横排，左上加 STEP_3 的留白）              ← 透明，只用来把面板摆到左上
 * └ 面板 Box（横排、gap = STEP_2、内边距 STEP_2、底色 = palette.panel()、圆角 = palette.radius()）
 *   ├ WidgetSlot（只读按钮，文案由调用方给）        ← 宽 CONTROL_MIN_W、高 ROW_H
 *   └ Label（提示文字）                             ← 宽按实量文字宽、高 ROW_H，字自己画
 * </pre>
 *
 * <p>【面板为什么是"自然尺寸"而不是铺满】HUD 上留白是稀缺的：铺满会把世界压暗一片。
 * 根那层只有 {@code STEP_3} 的定位留白，面板取<b>自然尺寸</b> —— 于是"条有多宽"
 * = "内容有多宽"，而内容的每一段都各有一个能被点名的 token（README 的"不许裸数字"）。
 * 所以这一轮<b>没有新增 token</b>：{@code CONTROL_MIN_W} 是"控件最小宽"、
 * {@code LABEL_MIN_W} 是"标签最小宽"、{@code ROW_H} 是"一行"—— 三段各归其位。
 *
 * <p>【它为什么不是 {@code Screen}】那正是本轮要验的东西：{@code Screen} 会白送
 * 生命周期、输入与 tick；这一棵树的<b>每一帧都由调用方自己驱动</b>（{@code HudStatusPanel}）。
 */
public final class HudStatusBar {

    private HudStatusBar() {
        throw new AssertionError("no instances");
    }

    /**
     * 建好的一条状态条 + 它的几何。
     *
     * <p>几何<b>只有一处</b>（判据 1）：每个盒子在哪、多大，一律读它自己的 {@code bounds()}，
     * 这里不另存一份坐标 —— 重算出来的数不是任何东西的实际位置。
     */
    public static final class Bar {

        private final UiTree tree;
        private final Component panel;
        private final Label hint;
        private final WidgetSlot chip;

        Bar(UiTree tree, Component panel, Label hint, WidgetSlot chip) {
            this.tree = tree;
            this.panel = panel;
            this.hint = hint;
            this.chip = chip;
        }

        public UiTree tree() {
            return tree;
        }

        /** 面板本体的矩形（底色画在它上面）。 */
        public Rect panelBox() {
            return panel.bounds();
        }

        /** 提示文字的矩形 —— 字由 {@link Label} 自己画，这里只把几何交出去（读 {@code bounds()}）。 */
        public Rect hintBox() {
            return hint.bounds();
        }

        /** 只读芯片的矩形。 */
        public Rect chipBox() {
            return chip.bounds();
        }

        /** 这一帧被画过的控件数（给自检用：控件在、但这一帧没被画 = 静默空白）。 */
        public int paintedCount(long nowNanos) {
            return chip.widget().paintedIn(nowNanos) ? 1 : 0;
        }

        /**
         * 一行读数。**读真矩形，不重算**（判据 1）。
         *
         * <p>打三个量而不是一个：面板的宽是<b>推导出来的</b>（两段内容 + 两个内边距 + 一条缝），
         * 只报一个数的话，"面板宽了 4px"到底是哪一段胖了看不出来。
         */
        public String dump() {
            Rect p = panelBox();
            Rect c = chipBox();
            Rect h = hintBox();
            return String.format(java.util.Locale.ROOT,
                    "HUD 状态条: 面板=%.0fx%.0f@(%.0f,%.0f) 芯片=%.0fx%.0f@(%.0f,%.0f)"
                            + " 提示盒=%.0fx%.0f@(%.0f,%.0f) 盒间缝=%.0f",
                    p.width(), p.height(), p.x(), p.y(),
                    c.width(), c.height(), c.x(), c.y(),
                    h.width(), h.height(), h.x(), h.y(),
                    h.x() - (c.x() + c.width()));
        }
    }

    /**
     * 建树。<b>不调用 MC</b>，所以离线可测。
     *
     * @param chipLabel 芯片的标识（只进诊断日志；<b>画出来的</b>是 {@code chipValue}）
     * @param chipValue 芯片的<b>值</b>，每帧现取 —— 数值会变而树不重建，
     *                  靠的就是这条 {@code Supplier}（A-18 定的口径）
     * @param hintText  提示文案，同样每帧现取（换了语言不用重建这棵树）
     * @param hintColor 提示文字的颜色 —— 配色归宿主（{@code WidgetPalette} 那条口径），
     *                  框架不替它决定
     * @param hintWidth 提示盒的宽，<b>由调用方量出来</b>（{@code font.width(提示串)}），
     *                  不是从 token 猜的。⚠️ A-24 的评审逮到过这一条：本喵第一版把它写成
     *                  {@code LABEL_MIN_W × u} = 24px，而 en_us 的 {@code "G config"} 实测
     *                  41px —— 于是字形缝把它缩到 58%、<b>缩成一团糊字</b>。
     *                  本仓自己早否过这条路：{@code PickupCardConfigScreen} 的标签列就是因为
     *                  "textFitted 没有下限，长文案一路缩到看不清、而且什么都不报"才换成
     *                  {@code shrinkToFit} + 地板 + 省略号。盒子按<b>实量宽</b>给，那一步不会发生。
     * @param viewportWidth  视口逻辑宽（HUD 上就是画布宽）
     * @param viewportHeight 视口逻辑高（{@code u} 由调用方按它算好再传进来）
     * @param u         单位 u（token × u）
     * @param palette   这一帧的调色板（测试用 {@code NvgPalette.dark(...)}）
     */
    public static Bar build(String chipLabel, Supplier<String> chipValue,
                            Supplier<String> hintText, int hintColor, float hintWidth,
                            float viewportWidth, float viewportHeight,
                            float u, WidgetPalette palette) {
        float pad = Tokens.Space.STEP_2 * u;
        float inset = Tokens.Space.STEP_3 * u;
        float rowH = Tokens.Size.ROW_H * u;

        // 根 = 定位层：透明、只有左上留白。它在布局里会被视口撑满（根的 Sizing 会被视口覆盖），
        // 所以**绝不能**给它底色 —— 那会把整屏刷成一块面板。
        // ⚠️ `Insets.of` 是「上 右 下 左」（Insets.java:43 的原文），不是「上右下左」里的"左上"：
        // 本喵第一版写成 `(inset, inset, 0, 0)` = 上 + **右**，面板被摆在了 x=0
        // —— 是 `HudStatusBarTest` 的"面板落在左上留白处"那条当场逮住的。
        Component root = new Box(Style.row().withPadding(Insets.of(inset, 0f, 0f, inset)));

        // 面板 = 取自然尺寸（横排的自然宽 = 子项宽 + 缝 + 两个内边距）。
        // 底色由 Surface 给；圆角跟控件同一套（palette.radius()），否则条是方角、钮是圆角。
        Component panel = root.add(new Box(
                Style.row()
                        .withGap(pad)
                        .withPadding(Insets.all(pad)),
                new Surface(palette.panel(), 0, palette.radius(), 0f)));

        // 只读芯片：action = null。A-18 验过"只读钮照样吃掉 Enter/Space，只是不做事" ——
        // 在 HUD 上没人送键，这条性质在这里既用不到也无害。
        WidgetSlot chip = panel.add(new WidgetSlot(
                new Button(chipLabel, chipValue, null),
                Style.row()
                        .withWidth(Sizing.fixed(Tokens.Size.CONTROL_MIN_W * u))
                        .withHeight(Sizing.fixed(rowH)),
                palette));

        // 提示：一个 Label —— 自己经字形缝画字（从前是一个空盒子 + 宿主在外面另画一笔）。
        // 宽 = 调用方量出来的文字宽（**盒子按内容给**，字形缝那一步才不会去缩它）。
        Label hint = panel.add(new Label(hintText, Style.row()
                .withWidth(Sizing.fixed(hintWidth))
                .withHeight(Sizing.fixed(rowH))).color(hintColor).fitted(true));

        UiTree tree = new UiTree(root);
        Bar bar = new Bar(tree, panel, hint, chip);
        // 建完当场布局一次：事件可能落在两帧之间，那时树已作废、下一帧还没到，
        // 读 bounds() 会 NPE（真机第 21 轮崩过）。
        // ⚠️ 必须用调用方给的视口。本喵第一版写的是 `layout(bar, 0f, 0f, 1f)`，于是
        // `HudStatusBarTest` 里那两条"换个视口看面板变不变"的测试**测的是一个被忽略的参数** ——
        // 恒真，换任何错误实现都照样绿。评审逮到，现在视口真接上了，那两条才有判别力。
        layout(bar, viewportWidth, viewportHeight, 1f);
        return bar;
    }

    /**
     * 把树摆一次。<b>视口 = 整屏</b>：根会被撑满，面板靠根那一层留白落在左上。
     *
     * <p>【为什么视口是整屏而不是"面板那么大"】面板的尺寸是<b>算出来的</b>（自然尺寸），
     * 布局之前不知道 —— 先问"面板多大"再布局就成了循环。给整屏、让根撑满、读面板的结果，
     * 才是单向的。（读出来之后也能知道真正占了多大，{@link Bar#panelBox()}。）
     *
     * @param pixelGrid 像素网格粒度，传 {@code 1/guiScale} 让设备像素对齐生效
     *                  （不交的话退化成"对齐到整数逻辑坐标"，guiScale 3 下一条边最多挪 1.5 设备像素）
     */
    public static void layout(Bar bar, float viewportWidth, float viewportHeight, float pixelGrid) {
        bar.tree().layout(new Rect(0f, 0f, viewportWidth, viewportHeight), pixelGrid);
    }

    /**
     * 结构容器：只占位、自己不画东西。
     *
     * <p>【它和 {@link Label} 的分工】这里只留"根 / 面板"这类<b>纯结构</b>盒子（要底色就带
     * {@link Surface}）；<b>要显示文字的地方一律用 {@link Label}</b> —— 从前提示盒也是一个 Box、
     * 字由宿主在外面另画一笔，那笔已经收进 Label 了（它在字上画，不需要基类那层白雾叠加）。
     */
    private static final class Box extends Component {

        /** 无底色（定位层）。 */
        Box(Style style) {
            style(style);
            // 【必须关掉状态叠加层】基类那层白色覆盖是给"有底色的控件"做反馈的；这些盒子自己不画
            // 任何东西，叠上去就是凭空一层白雾（同 CardGridTree 的 Box）。
            stateOverlay(false);
        }

        /** 有底色（面板本体）。底色 + 圆角 = 一块能压住世界的背板。 */
        Box(Style style, Surface surface) {
            style(style);
            surface(surface);
        }

        @Override
        protected void drawContent(Canvas canvas) {
        }
    }
}
