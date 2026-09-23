package com.niuqu.pickupcard.layout;

/**
 * 卡片网格的纯几何：<b>列数随可用宽度退让</b>（四列 → 三列 → 两列），以及内容盒与内容高。
 * <p>
 * 【为什么退让归这里，不归 Trellis 的布局层】"分几列"是<b>结构决定</b>，和"格子多大"是两件事。
 * Trellis 的 L1 只算坐标、不认识条件表达（{@code layout/package-info.java} 明确写了不做 shrink /
 * margin / wrap），而结构决定按该仓库自己的口径就该由调用方算 —— 原话见
 * {@code ConfigScreenParityTest}："结构决定（断点 / 退让顺序）必须由调用方算……但尺寸本身仍然
 * 交给 {@code Sizing}"。所以这里只算出<b>几列、几行、内容多高</b>。
 * <p>
 * 【<b>每格多宽已经不在这里了</b>（A-22）】从前这里用 {@code cellWidth()} 算格子宽
 * （{@code (内容宽 − 缝×(cols−1)) / cols} 再 floor），调用方拿这个数去写 {@code Sizing.fixed}。
 * 那正是 {@code Sizing.fraction} 的立案理由里点名要根除的病 ——"表达不出来的时候，调用方只能
 * 自己算成像素再传进来，于是那段 clamp 就跑到了布局层外面"。所以 Trellis 加了
 * {@code Sizing.track(cols)}（= CSS Grid 的 {@code repeat(n, 1fr)}，缝由父容器扣），
 * 格子宽由 <b>L1</b> 解析，这里不再出这个数。floor 也一并去掉了：旧口径在 640/426.7 两档
 * 右边各白丢 0.33 / 0.23px，而设备像素对齐本来由布局的事后一遍负责。
 * <p>
 * 【为什么单独成一个类】和 {@link StackLayout} 同一条理由：排布是"几张、屏幕多大"进去、
 * "每格在哪"出来的纯函数，不认识 {@code GuiGraphics}、不认识字体、不认识 Forge，也不认识
 * Trellis —— 所以它能在没有游戏的情况下跑单测。上一版把这类数学写进渲染器，只能靠真机截图
 * 才能发现"格子叠在一起了"。
 * <p>
 * 【为什么尺寸全是入参，不是在这里读 token】{@code shared} 这一层<b>没有</b> Trellis 依赖
 * （装配脚本 {@code gradle/pickupcard-layers.gradle} 里根本没有 dependencies 块），token 只有
 * 平台目标那一侧读得到。所以调用方把 token × {@code u} 算好再传进来 —— 和
 * {@link StackLayout#stack} 收 {@code gap} / {@code marginX} 是同一个形状。
 * <p>
 * 【退让方向是"少一列"，不是"缩格子"】判据 = <b>格子至少和它自己一样宽</b>（下限宽 = 格高）。
 * 宁可少放一列，也不把卡片压成竖条 —— 后者会让"放不下"变成看不见的观感问题，
 * 这正是 {@link LayoutSettings#MIN_AUTO_PERCENT} 那条账的同一口径。
 */
public final class GridMath {

    /** 列数下限：再窄也至少有这么多列（产品决定，不是几何推导出来的）。 */
    public static final int MIN_COLS = 2;

    /** 列数上限：再宽也不超过这么多列 —— 卡片太宽会离"一览"的意图太远。 */
    public static final int MAX_COLS = 4;

    /**
     * floor 之前加的极小容差。
     * <p>
     * 【为什么需要它】这个除法在本该整除时可能落在 {@code x.9999997}（浮点表示），
     * 直接 floor 就<b>少算一列</b>：四列掉成三列，整行少放一张卡，看着像"右边挤了"，
     * 而那是一个纯粹由浮点造成的、无法从代码上读出来的错。
     * <p>
     * 【A-22 之后只作用于列数那一趟】从前它还给格子宽兜底（四列时整行少 4px）；格子宽搬去
     * {@code Sizing.track} 之后，这里是唯一的用处 —— 判据见 {@code GridMathTest} 里
     * 那条"差不到 1e-4 就该认成整除"的测试（把常量改成 0 它必须变红）。
     */
    private static final float FLOOR_EPSILON = 1e-4f;

    /**
     * 一次求解要用的常量。<b>全部已经是 GUI 逻辑像素</b>（token × {@code u} 由调用方算好）。
     *
     * @param cellHeight 格子的高，<b>同时是它的下限宽</b>（退让判据见类注释）
     * @param gap        行缝 = 列缝，同一个数
     * @param padding    页面左右内边距（网格自己的左右留白，画在容器上而不是内容上）
     * @param minCols    列数下限
     * @param maxCols    列数上限
     */
    public record Spec(float cellHeight, float gap, float padding, int minCols, int maxCols) {

        public Spec {
            if (!(cellHeight > 0f)) {
                throw new IllegalArgumentException("cellHeight 必须为正：" + cellHeight);
            }
            if (gap < 0f) {
                throw new IllegalArgumentException("gap 不能为负：" + gap);
            }
            if (padding < 0f) {
                throw new IllegalArgumentException("padding 不能为负：" + padding);
            }
            if (minCols < 1 || maxCols < minCols) {
                throw new IllegalArgumentException(
                        "列数区间不合法：[" + minCols + ", " + maxCols + "]");
            }
        }

        /** 用默认列数区间（{@link #MIN_COLS} … {@link #MAX_COLS}）的三参快捷构造。 */
        public static Spec of(float cellHeight, float gap, float padding) {
            return new Spec(cellHeight, gap, padding, MIN_COLS, MAX_COLS);
        }
    }

    /**
     * 解出来的网格几何。
     * <p>
     * <b>这里没有"每格多宽"</b>—— 那由 L1 的 {@code Sizing.track(cols)} 解析（见类注释）。
     * 想知道实际多宽，读格子的 {@code bounds()}（判据 1：绘制、命中、读数读同一个对象）。
     *
     * @param cols          列数
     * @param rows          行数（{@code ceil(itemCount / cols)}；没有项目时为 0）
     * @param cellHeight    每格逻辑高（宽度不在这里，见上）
     * @param gap           行缝 = 列缝
     * @param padding       页面左右内边距
     * @param contentWidth  内容盒宽（= 视口宽 − 2×padding），供调用方与布局对账
     * @param contentHeight 内容盒高（行高 + 行缝，末行之后不留缝；没有项目时为 0）
     */
    public record Metrics(int cols, int rows, float cellHeight, float gap,
                          float padding, float contentWidth, float contentHeight) {

        /** 一个格子都没有（{@code itemCount <= 0}）。 */
        public boolean isEmpty() {
            return rows == 0;
        }
    }

    private GridMath() {
    }

    /**
     * 解一次：几个项目、视口多宽 → 几列、几行、内容多高。
     *
     * @param itemCount     项目个数（&lt;= 0 时 {@link Metrics#isEmpty()} 为真）
     * @param viewportWidth 网格可见区的逻辑宽（滚动容器自己的宽，不含 padding）
     * @param spec          常量，见 {@link Spec}
     */
    public static Metrics solve(int itemCount, float viewportWidth, Spec spec) {
        int cols = columnsFor(viewportWidth, spec);
        float contentWidth = innerWidth(viewportWidth, spec);
        int rows = itemCount <= 0 ? 0 : (itemCount + cols - 1) / cols;
        float contentHeight = rows == 0
                ? 0f
                : rows * spec.cellHeight() + (rows - 1) * spec.gap();
        return new Metrics(cols, rows, spec.cellHeight(), spec.gap(),
                spec.padding(), Math.max(0f, contentWidth), contentHeight);
    }

    /**
     * 退让本身：这个宽度放得下几列。
     * <p>
     * 公式 {@code floor((inner + gap) / (cellHeight + gap))} —— 分子那个 {@code + gap} 是
     * "每格占的是一条格子加一条缝"，而最后一条缝不存在，所以补一条回去。
     * 结果夹进 {@code [minCols, maxCols]}：<b>夹取是刻意保留的</b>，
     * 窄到下限时宁可溢出（由宿主裁），也不让列数掉到 1（那就不是网格了）。
     */
    public static int columnsFor(float viewportWidth, Spec spec) {
        float inner = innerWidth(viewportWidth, spec);
        if (inner <= 0f) {
            return spec.minCols();
        }
        float slot = spec.cellHeight() + spec.gap();
        if (!(slot > 0f)) {
            return spec.maxCols();
        }
        int fits = (int) Math.floor((inner + spec.gap()) / slot + FLOOR_EPSILON);
        return Math.max(spec.minCols(), Math.min(spec.maxCols(), fits));
    }

    /**
     * 内容盒宽（= 视口宽 − 2×左右内边距）。
     * <p>
     * 内边距<b>只在这里减一次</b>：树的左右 padding 写在根上、内容盒横向不再留白，
     * 否则格子左缘会等于 {@code padding + padding}，和标题左缘差一格。
     */
    public static float innerWidth(float viewportWidth, Spec spec) {
        return viewportWidth - 2f * spec.padding();
    }
}
