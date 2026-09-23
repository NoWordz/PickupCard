package com.niuqu.pickupcard.render.nvg.ui;

import com.niuqu.pickupcard.layout.GridMath;
import dev.e33.trellis.geom.Insets;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Align;
import dev.e33.trellis.layout.Sizing;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.render.Canvas;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.ScrollContainer;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.ui.WidgetSlot;
import dev.e33.trellis.ui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>网格形态的建树器</b>（A-19）。和 {@link TrellisColumn} 同一条理由存在：
 * 几何是"几格、视口多大"进去、"每格在哪"出来的<b>纯计算</b>，把它关在 {@code Screen} 里
 * 就只能靠真机截图才发现"格子叠在一起了"。
 *
 * <p>【为什么不在 Screen 里建】{@code Screen} 要 MC 才能实例化，而这一片的验收
 * （根=视口、容器的矩形=剩余高、同行格子等宽、滚出去的格子点不到、方向键走到哪一格）
 * <b>一条都不需要启动游戏</b>。先例是 {@code TrellisScrollHitTest}：它调
 * {@code TrellisColumn.buildColumn(...)} + {@code NvgPalette.dark(...)}，全程零 MC。
 *
 * <p>【树形】
 * <pre>
 * 根 Box（列、STRETCH、四周 pad）
 * ├─ 标题盒（高 = ROW_H）
 * ├─ ScrollContainer（列、STRETCH、高 = fixed(0) + grow(1)、notchStep = 格高 + 缝）
 * │   └ 内容盒（列、STRETCH、gap = 缝、上下留白）
 * │       └ 行盒（横排、gap = 缝）× 行数
 * │           └ WidgetSlot（每格一个，宽 = Sizing.track(cols)、高 = fixed(格高)）
 * └─ 提示盒（高 = ROW_H）
 * </pre>
 *
 * <p>【格子宽由 L1 解析，不由调用方算】（A-22）{@code Sizing.track(cols)} = "我是这一行 cols
 * 等分里的一份"，缝由父容器扣。它替掉了从前的 {@code Sizing.fixed(GridMath.cellWidth())} ——
 * 那是调用方自己算成像素再传进来的数，正是 {@code Sizing.fraction} 的立案理由点名要根除的病。
 * 好处不只是"少一处计算"：旧的 floor 口径在 640 / 426.7 两档右边各白丢 0.33 / 0.23px。
 *
 * <p>【因此 STRETCH 从"好看"升成了承重】{@code track} 的分母是<b>父容器给了多少</b>，所以行盒
 * 必须有一个确定的宽度（这里靠内容盒与行盒上的 {@code Align.STRETCH} 一路传下来）。少了它，
 * 末行那两个格子会按"自己那一行的自然宽"去等分，只有满行格子的一半宽 —— 就是 A-19 那条症状。
 * 这一条由 {@code CardGridTreeTest} 的"末行格子与满行等宽"钉着（去掉 STRETCH 必须变红）。
 * ⚠️ 从前那条"内容盒宽 = 视口宽 − 2×pad"在 A-22 之后<b>已经丧失判别力</b>：轨道行的自然宽
 * 本身就是可用宽，去掉 STRETCH 它照样绿。判别力挪到了末行那条上。
 *
 * <p>【那处反直觉但必需的写法】滚动容器用 {@code Sizing.fixed(0f) + withGrow(1f)}。
 * <b>单独用 {@code withGrow(1f)} 是无效的</b>：容器的自然高 = 内容全高 →
 * {@code used} 超过可用高 → {@code distributeGrow} 在 {@code free <= 0} 时直接返回 →
 * 容器被摆成内容那么高、冲出屏幕，而 {@code maxOffsetY} 恒为 0（<b>滚不动，且不报错</b>）。
 * 参见 {@code LayoutNode.measure}（不夹 constraints）、{@code Sizing.resolve}（fixed(0) → 0）、
 * {@code LayoutNode.distributeGrow}（free <= 0 直接返回）。CSS 里 {@code flex:1} 的默认
 * {@code min-height:auto} 是同一个症状。
 */
public final class CardGridTree {

    private CardGridTree() {
        throw new AssertionError("no instances");
    }

    /**
     * 建好的一棵网格树 + 它的几何。
     * <p>几何<b>只有一处</b>：格子矩形归 {@code bounds()}（判据 1），行列数归 {@link #metrics()}，
     * 这里不另存一份坐标。
     */
    public static final class Grid {

        private final UiTree tree;
        private final ScrollContainer scroller;
        private final Component titleBox;
        private final Component hintBox;
        private final List<WidgetSlot> cells;
        private final GridMath.Metrics metrics;

        Grid(UiTree tree, ScrollContainer scroller, Component titleBox, Component hintBox,
             List<WidgetSlot> cells, GridMath.Metrics metrics) {
            this.tree = tree;
            this.scroller = scroller;
            this.titleBox = titleBox;
            this.hintBox = hintBox;
            this.cells = cells;
            this.metrics = metrics;
        }

        public UiTree tree() {
            return tree;
        }

        public ScrollContainer scroller() {
            return scroller;
        }

        public Component titleBox() {
            return titleBox;
        }

        public Component hintBox() {
            return hintBox;
        }

        /** 每一格的槽，<b>行优先</b>（第 i 格 = 第 i/cols 行、第 i%cols 列）。 */
        public List<WidgetSlot> cells() {
            return cells;
        }

        /**
         * 一格的实宽 —— <b>读第一格自己的矩形</b>，不重算一遍。
         * <p>
         * 【为什么读数要读矩形】（判据 1）绘制、命中、读数读同一个 {@code Rect}，所以
         * "读数与真实几何分家"这类病自己就会现形。从前这个数是从 {@code GridMath.cellWidth()}
         * 重算的（A-22 之后那个数已经不存在了），而重算的值和布局真正给的可以悄悄不一致 ——
         * 网格的"右边没对齐"就是这么来的。
         * <p>
         * 没有格子时是 0（空网格是合法的：{@code itemCount <= 0}）。
         */
        public float cellWidth() {
            return cells.isEmpty() ? 0f : cells.get(0).bounds().width();
        }

        /** 一格的实高，同上读矩形（A-22 之后格子宽由 L1 解析，高仍是调用方给的 fixed）。 */
        public float cellHeight() {
            return cells.isEmpty() ? 0f : cells.get(0).bounds().height();
        }

        public GridMath.Metrics metrics() {
            return metrics;
        }

        /** 滚动容器自己那份矩形 —— 它就是<b>视口</b>（不是内容矩形）。 */
        public Rect viewport() {
            return scroller.bounds();
        }

        /** 焦点落在第几行第几列；没有焦点时返回 -1。 */
        public int focusedIndex() {
            return cells.indexOf(tree.focused());
        }
    }

    /**
     * 建树。<b>不调用 MC</b>，所以离线可测。
     *
     * @param items           每格托着的控件（行优先）
     * @param viewportWidth   视口逻辑宽（退让算法的输入）
     * @param viewportHeight  视口逻辑高（只用来推 u 相关的高度？不 —— 高度由 u 与 token 定，
     *                        这里只为"根 = 视口"这条布局口径留个口子）
     * @param u               单位 u（token × u）
     * @param palette         这一帧的调色板（测试用 {@code NvgPalette.dark(...)}）
     */
    public static Grid build(List<Widget> items, float viewportWidth, float viewportHeight,
                             float u, NvgPalette palette) {
        GridMath.Metrics metrics = GridMath.solve(items.size(), viewportWidth, spec(u));
        float pad = Tokens.Space.STEP_3 * u;
        float gap = Tokens.Space.STEP_2 * u;
        float rowH = Tokens.Size.ROW_H * u;

        // 根 = 视口（FlexLayout 里视口的尺寸是权威的，根自己的 Sizing 会被覆盖）。
        // 左右留白只写在这一层：内容盒横向必须为 0，否则格子左缘 = pad + pad，与标题差一格。
        Component root = new Box(Style.column()
                .withAlign(Align.STRETCH)
                .withPadding(Insets.all(pad)));

        Component titleBox = root.add(new Box(Style.column().withHeight(Sizing.fixed(rowH))));

        // 固定头 + 可滚主体，见类注释：fixed(0) + grow 是唯一表达。
        // notchStep 必须由调用方给（框架不知道行高）；滚一格 = 一行。
        ScrollContainer scroller = root.add(new ScrollContainer(Style.column()
                .withAlign(Align.STRETCH)
                .withHeight(Sizing.fixed(0f))
                .withGrow(1f), metrics.cellHeight() + gap));

        // 纵向留白写在内容上（滚动时跟着走，与配置列同一条口径）；横向必须是 0。
        Component content = scroller.add(new Box(Style.column()
                .withAlign(Align.STRETCH)
                .withGap(gap)
                .withPadding(Insets.of(gap, 0f, pad, 0f))));

        List<WidgetSlot> slots = new ArrayList<>(items.size());
        for (int row = 0; row < metrics.rows(); row++) {
            // 行用 START（默认）：末行不足 cols 个时余数留在右边；SPACE_BETWEEN 会把余数摊进缝里，
            // 末行两个格子于是顶在两头、中间一个巨大的洞。
            Component rowBox = content.add(new Box(Style.row().withGap(gap)));
            for (int col = 0; col < metrics.cols(); col++) {
                int index = row * metrics.cols() + col;
                if (index >= items.size()) {
                    break;
                }
                WidgetSlot slot = rowBox.add(new WidgetSlot(items.get(index), Style.row()
                        // 【格子宽由 L1 解析，不由调用方算】（A-22）track(cols) = "我是这一行
                        // cols 等分里的一份"，缝由父容器扣。末行只有 2 个格子时，它们照样各拿
                        // "四等分里的一份"（n 是显式传的，不是数兄弟数出来的），右边自然留空 ——
                        // 从前用 grow 会让末行格子宽一倍，用 fixed 则要求调用方自己算 cellW。
                        .withWidth(Sizing.track(metrics.cols()))
                        .withHeight(Sizing.fixed(metrics.cellHeight())), palette));
                slots.add(slot);
            }
        }

        Component hintBox = root.add(new Box(Style.column().withHeight(Sizing.fixed(rowH))));

        UiTree tree = new UiTree(root);
        Grid grid = new Grid(tree, scroller, titleBox, hintBox, slots, metrics);
        // 建完当场布局一次：事件可能落在两帧之间，那时树已作废、下一帧还没到，
        // 读 bounds() 会 NPE（真机第 21 轮崩过）。
        layout(grid, viewportWidth, viewportHeight, 1f);
        return grid;
    }

    /** token 值 × u —— {@code shared} 那一层没有 Trellis 依赖，所以折算在这里。 */
    public static GridMath.Spec spec(float u) {
        return GridMath.Spec.of(
                Tokens.Size.CELL_H * u,
                Tokens.Space.STEP_2 * u,
                Tokens.Space.STEP_3 * u);
    }

    /**
     * 把树摆一次（视口 = 整屏）。
     *
     * @param pixelGrid 像素网格的粒度，传 {@code 1/guiScale} 让设备像素对齐生效
     *                  （不交的话退化成"对齐到整数逻辑坐标"，guiScale 3 下一条边最多挪 1.5 设备像素）
     */
    public static void layout(Grid grid, float viewportWidth, float viewportHeight, float pixelGrid) {
        grid.tree().layout(new Rect(0f, 0f, viewportWidth, viewportHeight), pixelGrid);
    }

    /**
     * <b>二维方向键导航。</b>
     * <p>框架的 {@code UiTree} 只有一维文档顺序（{@code focusNext} = "序号 ± 1"），
     * {@code Keys} 里也没有方向键 —— 但宿主手里本来就有"第几格"和 {@code cols}。
     * 要不要把"X 右边是谁"收进框架，等这一轮读数出来再定。
     * <p>【不环绕】走到边界就停：环绕会让"第一行按上跳到末行"看起来像 bug。
     */
    public static boolean navigate(Grid grid, int dCol, int dRow) {
        List<WidgetSlot> cells = grid.cells();
        if (cells.isEmpty()) {
            return false;
        }
        int current = grid.focusedIndex();
        if (current < 0) {
            return false;
        }
        int cols = grid.metrics().cols();
        int col = current % cols + dCol;
        int row = current / cols + dRow;
        if (col < 0 || col >= cols || row < 0) {
            return false;
        }
        int target = row * cols + col;
        if (target >= cells.size()) {
            return false;
        }
        grid.tree().requestFocus(cells.get(target));
        // 【焦点走到哪，视口跟到哪】方向键是宿主自己的导航，不在 `focusNext` 那条路上，
        // 所以这里显式跟一次 —— 不跟的话焦点环会被容器裁掉，看起来像"键盘失灵"。
        grid.tree().revealFocus();
        return true;
    }

    // ------------------------------------------------------------------
    // 读数：harness 与离线测试读的是同一批字符串（于是"测试绿"与"真机绿"同源）
    // ------------------------------------------------------------------

    /**
     * 定妆读数：列数 / 每格多大 / 内容多大 / 视口多大 / 行数 / 格子数。
     * <p>
     * 【每格宽读的是真矩形，而且印一位小数】（A-22）读真矩形是判据 1（与绘制、命中同源）；
     * 印小数是因为格子宽从"调用方 floor 过的整数"换成了"L1 的轨道槽宽"—— 640 与 426.7 两档
     * 不再是整数（207.3 / 137.2），印成整数正好会把这一轮要看的那个变化盖掉。
     * guiScale 1 / 4 两档是整除的，读数与 A-19 的记录逐位相同。
     */
    public static String dump(Grid g) {
        GridMath.Metrics m = g.metrics();
        Rect view = g.viewport();
        return String.format("网格读数: 列数=%d 每格=%.1fx%.0f 内容=%.0fx%.0f 视口=%.0fx%.0f 行数=%d 格子=%d",
                m.cols(), g.cellWidth(), g.cellHeight(),
                g.scroller().contentWidth(), g.scroller().contentHeight(),
                view.width(), view.height(), m.rows(), g.cells().size());
    }

    /** 焦点落在第几行第几列。 */
    public static String focusDump(Grid g) {
        int current = g.focusedIndex();
        if (current < 0) {
            return "焦点=无";
        }
        int cols = g.metrics().cols();
        return "焦点=行" + (current / cols) + "列" + (current % cols);
    }

    /**
     * 焦点走出视口那条探针：读数 = 焦点位置 + 容器偏移。
     * <p>框架<b>没有</b> {@code scrollIntoView}（全仓零命中），所以期望读数是
     * <b>"焦点动了、偏移没动"</b> —— 症状是焦点环被 {@code clipChildren} 裁掉，
     * 玩家看到的是"键盘失灵了"，而实际上什么都没坏。
     */
    public static String scrollProbeDump(Grid g) {
        int current = g.focusedIndex();
        String where = current < 0 ? "无"
                : "行" + (current / g.metrics().cols()) + "列" + (current % g.metrics().cols());
        Rect view = g.viewport();
        return String.format("焦点进视口探针: 焦点=%s 容器偏移=%.0f 视口高=%.0f 内容高=%.0f",
                where, g.scroller().offsetY(), view.height(), g.scroller().contentHeight());
    }

    /** 滚动的命中读数：滚出去的那一格应当点不到（由"祖先矩形必须包含该点"免费给出）。 */
    public static String scrollHitDump(Grid g) {
        Rect view = g.viewport();
        float cx = view.x() + g.cellWidth() / 2f;
        float aboveY = view.y() - g.cellHeight() / 2f;
        boolean aboveHit = g.tree().hitTest(cx, aboveY) != null;
        float insideY = view.y() + g.cellHeight() / 2f;
        boolean insideHit = g.tree().hitTest(cx, insideY) != null;
        return String.format("网格滚动命中: 偏移=%.0f 视口内命中=%s 视口上方误命中=%d",
                g.scroller().offsetY(), insideHit, aboveHit ? 1 : 0);
    }

    /** 只占位、自己不画东西的盒子（文字由宿主画在它的 {@code bounds()} 上）。 */
    private static final class Box extends Component {
        Box(Style style) {
            style(style);
            // 【必须关掉状态叠加层】基类那层白色覆盖是给"有底色的控件"做反馈的；这些盒子自己不画
            // 任何东西，叠上去就是凭空一层白雾（指针停在哪条撑开的 spacer 上就整条刷白）。
            stateOverlay(false);
        }

        @Override
        protected void drawContent(Canvas canvas) {
        }
    }
}
