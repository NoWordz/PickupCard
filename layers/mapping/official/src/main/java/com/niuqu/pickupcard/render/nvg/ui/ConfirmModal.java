package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Align;
import dev.e33.trellis.layout.Justify;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.render.Canvas;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.text.TextAlign;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.Label;
import dev.e33.trellis.ui.Surface;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.ui.WidgetSlot;
import dev.e33.trellis.ui.widget.Button;
import dev.e33.trellis.ui.widget.WidgetPalette;

import java.util.List;

/**
 * <b>第五个形态：模态 / 对话框</b>（A-27）。
 *
 * <p>【它和前四个形态的差别在哪】配置列 / 编辑场 / 网格 / 常驻 HUD —— 四个里前面三个
 * <b>都在 {@code Screen} 里且一屏一棵树</b>；HUD 拿掉了 {@code Screen}，但还是一棵树。
 * 这一形态第一次出现<b>同屏两棵树</b>，而且要验的是它们之间那条规矩：
 * <b>输入独占</b> —— 模态开着的时候，落在它身上、以及落在它<b>外面</b>的输入都归它，
 * 底下的树<b>一点都收不到</b>。
 *
 * <p>【"输入独占到哪去验"是个真问题】MC 的 {@code Screen} 栈<b>白送</b>输入独占：
 * 压在上面的 Screen 收事件，底下的收不到（A-17 的编辑场就是这么做的，它 {@code setScreen}
 * 压了一层）。所以"再开一个 Screen 当对话框"<b>验不到框架</b> —— 那是 MC 在隔离输入。
 * 真正没验过的是：<b>不靠第二个 Screen，框架自己能不能做输入独占</b>。
 * 这正是本类的形状 —— 一屏、一个 {@code NvgUi}、两棵树，路由由调用方按"模态开不开"决定。
 *
 * <p>【框架为什么一行都不用改】因为 {@code core} 里<b>一个非 final 的静态字段都没有</b>
 * （{@code grep -rn "static" core/src/main/java | grep -v "static final" | grep ";"} → 0 命中，
 * 2026-09-23 复核）：树之间没有任何共享可变状态，两棵树天然共存。
 * 这是"可替换性"那一节的物理基础被第一次用上，也是这一形态唯一的框架级结论。
 *
 * <p>【为什么建树器在 {@code Screen} 外面】同 {@link HudStatusBar} / {@link CardGridTree}：
 * "视口多大进去、面板在哪出来"是<b>纯计算</b>。本类<b>不调用 MC</b>，所以 {@code ConfirmModalTest}
 * 全程离线。
 *
 * <p>【树形】
 * <pre>
 * 根 Box（列、justify=CENTER、align=CENTER）        ← 透明，把面板摆到整屏正中
 * └ 面板 Box（列、gap、内边距、底色=palette.panel()、圆角=palette.radius()）
 *   ├ 正文盒 Box（宽高由调用方量出来）             ← 自己不画，字由宿主画在 bounds() 上
 *   └ 按钮行 Box（横排、gap、justify=CENTER）
 *     ├ WidgetSlot（确认）
 *     └ WidgetSlot（取消）
 * </pre>
 */
public final class ConfirmModal {

    private ConfirmModal() {
        throw new AssertionError("no instances");
    }

    /** 一扇对话框 + 它的几何。几何<b>只有一处</b>：一律读各自的 {@code bounds()}（判据 1）。 */
    public static final class Modal {

        private final UiTree tree;
        private final Component panel;
        private final Label message;
        private final WidgetSlot confirm;
        private final WidgetSlot cancel;

        Modal(UiTree tree, Component panel, Label message, WidgetSlot confirm, WidgetSlot cancel) {
            this.tree = tree;
            this.panel = panel;
            this.message = message;
            this.confirm = confirm;
            this.cancel = cancel;
        }

        public UiTree tree() {
            return tree;
        }

        /** 面板本体的矩形（底色画在它上面）。 */
        public Rect panelBox() {
            return panel.bounds();
        }

        /** 正文的矩形（字由它自己即多行 {@link Label} 画，这里只把几何交出去）。 */
        public Rect messageBox() {
            return message.bounds();
        }

        public Rect confirmBox() {
            return confirm.bounds();
        }

        public Rect cancelBox() {
            return cancel.bounds();
        }

        /**
         * 这个点落在面板里吗 —— <b>输入独占的判据</b>。
         *
         * <p>读的是 {@link #panelBox()} 那一份真实几何，不是另算一个。落在外面 = 那次点击
         * 既不该穿到底下的树、也不该动作（模态的语义：外面被吃掉，但什么也不发生）。
         */
        public boolean hitInside(float x, float y) {
            return panelBox().contains(x, y);
        }

        /** 指针按下 → 树（焦点与按下都走它）。 */
        public boolean pointerDown(float x, float y) {
            return tree.pointerDown(x, y);
        }

        /** 指针抬起 → 树（抬起发给按下的那一个）。 */
        public boolean pointerUp(float x, float y) {
            return tree.pointerUp(x, y);
        }

        /** 键 → 树（只发给焦点组件）。 */
        public boolean keyDown(int keyCode, int modifiers) {
            return tree.keyDown(keyCode, modifiers);
        }

        /**
         * 这一帧被画过的钮的个数（自检：钮在树里、但这一帧没被画 = 静默空白）。
         *
         * <p>【时刻必须是树上那个纳秒值】{@code Widget.paintedIn} 的口径是树 {@code tick} 收的
         * 时刻（纳秒）—— 传毫秒会得到一个恒假的数（真机第 62 轮的自检报"17 个控件只画了 10 个"
         * 就是这个单位差）。
         */
        public int paintedCount(long nowNanos) {
            int n = 0;
            if (confirm.widget().paintedIn(nowNanos)) {
                n++;
            }
            if (cancel.widget().paintedIn(nowNanos)) {
                n++;
            }
            return n;
        }

        /** 一行读数。**读真矩形，不重算**。 */
        public String dump() {
            Rect p = panelBox();
            Rect c = confirmBox();
            Rect x = cancelBox();
            return String.format(java.util.Locale.ROOT,
                    "对话框: 面板=%.0fx%.0f@(%.0f,%.0f) 确认=%.0fx%.0f@(%.0f,%.0f)"
                            + " 取消=%.0fx%.0f@(%.0f,%.0f)",
                    p.width(), p.height(), p.x(), p.y(),
                    c.width(), c.height(), c.x(), c.y(),
                    x.width(), x.height(), x.x(), x.y());
        }

        /**
         * 焦点在哪颗钮上 —— {@code "confirm"} / {@code "cancel"} / {@code "-"}（没有焦点）。
         *
         * <p>【为什么按名字而不是按组件】读它的人是 harness 的日志与离线测试；把
         * {@code Component} 交出去只会让调用方去比身份（"是不是同一个对象"），而真正要回答的
         * 是"键会落到哪颗钮上"。名字够了。
         */
        public String focusedName() {
            Component f = tree.focused();
            if (f == confirm) {
                return "confirm";
            }
            if (f == cancel) {
                return "cancel";
            }
            return "-";
        }

        /**
         * 指针正悬在哪颗钮上 —— {@code "confirm"} / {@code "cancel"} / {@code "-"}。
         *
         * <p>读的是<b>树自己那一份悬停状态</b>（{@code UiTree.hovered()}），不是拿两个盒子
         * 再算一遍 —— 后者会变成第二份几何，悬停底画在 A 上而读数说 B 的时候就没人知道信谁。
         */
        public String hoveredName() {
            Component h = tree.hovered();
            if (h == confirm) {
                return "confirm";
            }
            if (h == cancel) {
                return "cancel";
            }
            return "-";
        }
    }

    /**
     * 建树。<b>不调用 MC</b>，所以离线可测。
     *
     * @param confirmLabel 确认钮的标签
     * @param cancelLabel  取消钮的标签
     * @param onConfirm    点确认做什么（<b>由调用方给</b> —— 动作归宿主，形状归这里）
     * @param onCancel     点取消 / 按 Esc 做什么
     * @param messageLines  正文的<b>各行</b>（换行由调用方切好 —— 框架不做断词，那是排版策略）
     * @param messageWidth  正文盒的宽，<b>由调用方量出来</b>（{@code font.width} 的换行结果），
     *                      不是从 token 猜的 —— A-24 的评审逮过"从 token 猜宽 → 字形缝把字缩成糊"
     * @param messageHeight 正文盒的高。⚠️<b>必须等于"行数 × 行高"</b>：正文在盒里是<b>整块居中</b>
     *                      摆的（{@code Label} 的多行语义），只有这个等式成立时"整块居中"才与
     *                      "从盒顶逐行往下排"逐像素相同。传高一点不会报错，只会让整段字整体下漂
     *                      {@code (h − 行数×行高)/2} —— 那种错只有截图看得见。
     * @param buttonWidth   每个钮的宽（{@code CONTROL_MIN_W} 之类，由调用方定）
     * @param u            单位 u（token × u）
     * @param palette      这一帧的调色板（测试用 {@code NvgPalette.dark(...)}）
     */
    public static Modal build(String confirmLabel, String cancelLabel,
                              Runnable onConfirm, Runnable onCancel,
                              List<String> messageLines,
                              float messageWidth, float messageHeight,
                              float buttonWidth, float u, WidgetPalette palette) {
        return build(confirmLabel, cancelLabel, onConfirm, onCancel,
                messageLines, messageWidth, messageHeight, buttonWidth, u, palette,
                palette.panel());
    }

    /**
     * 同上，但面板底色由调用方给。
     *
     * <p>【为什么留这个口子】{@code palette.panel()} 是给常驻控件用的<b>半透色</b>
     * （{@code 0xC0…}）—— 对话框面板是盖在压暗蒙层上的一整块，用半透色会把蒙层下的东西透出来，
     * 所以宿主传一个<b>不透明</b>色进来。给默认值（= palette.panel()）是为了测试与
     * "就用那套色"的调用方少写一个参数。
     */
    public static Modal build(String confirmLabel, String cancelLabel,
                              Runnable onConfirm, Runnable onCancel,
                              List<String> messageLines,
                              float messageWidth, float messageHeight,
                              float buttonWidth, float u, WidgetPalette palette,
                              int panelColor) {
        float pad = Tokens.Space.STEP_4 * u;
        float gap = Tokens.Space.STEP_2 * u;
        float rowH = Tokens.Size.ROW_H * u;

        // 根 = 定位层：透明、整屏、居中。它在布局里会被视口撑满，所以**绝不能**给它底色。
        Component root = new Box(Style.column()
                .withJustify(Justify.CENTER)
                .withAlign(Align.CENTER));

        // 面板 = 取自然尺寸（列的自然高 = 正文高 + 缝 + 按钮行高 + 两个内边距）。
        // 【底色由组件自己的 Surface 画，不是宿主的 fillRoundRect】否则同一块地方画两遍，
        // 而且后画的 Surface 会把先画的盖掉（A-27 评审指出：宿主那笔 opaque fill 是多余的）。
        Component panel = root.add(new Box(
                Style.column()
                        .withGap(gap)
                        .withPadding(Insets.all(pad))
                        .withAlign(Align.CENTER),
                new Surface(panelColor, 0, palette.radius(), 0f)));

        // 正文：一个多行 Label —— 自己经字形缝居中画每一行（从前是一个空盒子 + 宿主在外面
        // 逐行另画）。盒高 = 行数 × 行高时，整块居中与"从盒顶逐行往下排"逐像素相同。
        Label message = panel.add(new Label(messageLines, Style.row()
                .withWidth(Sizing.fixed(messageWidth))
                .withHeight(Sizing.fixed(messageHeight)))
                .color(palette.text())
                .align(TextAlign.H.CENTER));

        // 按钮行：两个钮居中排（面板宽于两个钮时，这行两侧留白均分）。
        Component buttonRow = panel.add(new Box(Style.row()
                .withGap(gap)
                .withJustify(Justify.CENTER)
                .withAlign(Align.CENTER)
                .withWidth(Sizing.fixed(buttonWidth * 2f + gap))));

        // 【钮上画的是 value、不是 label】{@code Button.paint} 用的是 {@code value.get()}，
        // 而 label 只是那个控件的身份（harness 按它找、日志按它写）。对话框的钮上要真的写出
        // 那两个字，所以 value 供给器回的就是传进来的 label —— 第一版回空串，真机上两颗钮
        // 是**空白方块**（截图当场逮到）。
        WidgetSlot confirm = buttonRow.add(new WidgetSlot(
                new Button(confirmLabel, () -> confirmLabel, onConfirm),
                Style.row().withWidth(Sizing.fixed(buttonWidth)).withHeight(Sizing.fixed(rowH)),
                palette));
        WidgetSlot cancel = buttonRow.add(new WidgetSlot(
                new Button(cancelLabel, () -> cancelLabel, onCancel),
                Style.row().withWidth(Sizing.fixed(buttonWidth)).withHeight(Sizing.fixed(rowH)),
                palette));

        UiTree tree = new UiTree(root);
        Modal modal = new Modal(tree, panel, message, confirm, cancel);
        // 建完当场布局一次：事件可能落在两帧之间，那时树已作废、下一帧还没到，
        // 读 bounds() 会 NPE（真机第 21 轮崩过）。
        // 视口由调用方随后的 layout 覆盖一次（这里给 0 只是"先有个合法的"）。
        layout(modal, 0f, 0f, 1f);
        return modal;
    }

    /**
     * 把树摆一次。<b>视口 = 整屏</b>：根被撑满，面板靠根那一层的 justify/align=CENTER 落在正中。
     *
     * @param pixelGrid 像素网格粒度，传 {@code 1/guiScale} 让设备像素对齐生效
     */
    public static void layout(Modal modal, float viewportWidth, float viewportHeight, float pixelGrid) {
        modal.tree().layout(new Rect(0f, 0f, viewportWidth, viewportHeight), pixelGrid);
    }

    /**
     * 结构容器：只占位、自己不画东西（根 / 面板 / 按钮行）。
     *
     * <p>【要显示文字的地方一律用 {@link Label}】从前正文也是一个 Box、字由宿主在外面逐行另画，
     * 那一笔已收进多行 {@link Label} —— 所以这里只剩纯结构用途。
     */
    private static final class Box extends Component {

        /** 无底色（定位层）。 */
        Box(Style style) {
            style(style);
            // 【必须关掉状态叠加层】基类那层白色覆盖是给"有底色的控件"做反馈的；这些盒子自己不画
            // 任何东西，叠上去就是凭空一层白雾（同 HudStatusBar / CardGridTree 的 Box）。
            stateOverlay(false);
        }

        /** 有底色（面板本体）。 */
        Box(Style style, Surface surface) {
            style(style);
            surface(surface);
        }

        @Override
        protected void drawContent(Canvas canvas) {
        }
    }
}
