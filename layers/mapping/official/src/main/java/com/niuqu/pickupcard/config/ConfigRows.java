package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import dev.e33.trellis.ui.widget.Widget;
import dev.e33.trellis.motion.Animated;
import dev.e33.trellis.motion.Easing;
import dev.e33.trellis.tokens.Tokens;

import java.util.ArrayList;
import java.util.List;

/**
 * 「配置列」的<b>行模型</b>：这一列有哪些行、行高多少、每行摆在哪。
 * <p>
 * 【为什么从界面类里拆出来（2026-09-20 审计后动刀）】配置界面类曾经 1082 行，其中
 * "行模型"（一行 = 标签 + 控件 + 悬停提示，等行距、可滚动、放不下就滚）与"绘制/事件"
 * 是两件事：模型只回答"有几行、第 i 行的 y 和控件矩形"，绘制只回答"这一行长什么样"。
 * 挤在一起的结果是改一行绘制要在一千行里找状态。拆出来之后，模型是纯几何 + 纯列表，
 * 不碰 NanoVG 也不碰输入。
 * <p>
 * 【小节头也是一行】{@code widget == null} 的行是小节头：占同样的行距、画暗色小字、
 * 没有控件也不接悬停 —— 分组信息用"节奏"表达，不引入第二种行高。
 */
final class ConfigRows {

    /**
     * 行距/行高/列顶内缩 —— <b>全是 token 的 N 个 u，u 每帧按画布高算</b>。
     *
     * <p>【为什么不再是 {@code static final}】从前写 {@code Tokens.Size.ROW_H * Tokens.Unit.BASE}，
     * 把"基准 u"烤进编译期常量。A-14 起 u 是活的（{@code Units.u(画布高)}，见框架
     * {@code dev.e33.trellis.tokens.Units}），任何 {@code static final} 都会**静默沿用基准 2** ——
     * 屏幕换一档 guiScale，行距却不动。所以改成一族**纯函数**，u 由调用方每帧给。
     *
     * <p>【为什么取整】这一列的行 y 走整数算术（{@code Math.round(scrollOffset)} 那套）；
     * Trellis 适配器读同一份 token 时也照**同样的取整**（{@code Math.round(N * u)}）——
     * 竖直方向必须逐位同（A-4 的 1.3px 是水平差，竖直从来没错过一格）。
     */
    static int rowH(float u) {
        return Math.round(Tokens.Size.ROW_H * u);
    }

    /** 行缝 = 1u：行距是"行高 + 行缝"，不是第三个独立常数（20 = 18 + 2）。 */
    static int rowGap(float u) {
        return Math.round(Tokens.Space.STEP_1 * u);
    }

    static int rowStep(float u) {
        return rowH(u) + rowGap(u);
    }

    /**
     * 第一行相对配置列顶的内缩 = 1u —— 贴着列顶会和标题行糊在一起。
     *
     * <p>【为什么放在这里而不是写在屏幕里】它是"行模型"的几何，而且有第二个消费者：
     * Trellis 组件列要拿同一个数当布局树的内边距（{@code TrellisColumn.buildColumn(..., topInset, u)}）。
     * 放一处、两边取同一个源。反过来做（适配器里补个 2）就是又一份口径 —— 2026-09-21 真机
     * 实测过后果：整列 12 行集体高 2 逻辑 px。
     */
    static int topInset(float u) {
        return Math.round(Tokens.Space.STEP_1 * u);
    }

    private final List<Row> list = new ArrayList<>();

    /** 一行选项（标签 + 控件 + 悬停说明）。 */
    void cell(String label, Widget widget, String hint) {
        list.add(new Row(label, widget, hint));
    }

    /** 小节头：占一行、不接控件，把"这几行是一伙的"画出来。 */
    void header(String title) {
        list.add(new Row(title, null, null));
    }

    List<Row> all() {
        return list;
    }

    /**
     * 逐行摆：**一行一项**（标签左、控件右），行距恒 {@link #rowStep(float)} px，放不下就滚。
     *
     * @param scrollOffset 滚动偏移（扣掉它，控件与它画出来的位置才是同一个坐标系，
     *                     否则点了会"选错行"）
     * @param rowsTop      第一行的顶 y（预览搬到右列后，配置列从自己的顶部开始）
     * @param u            这一帧的自适应单位（由屏幕按画布高算）
     */
    void layout(ConfigLayout lo, float scrollOffset, float rowsTop, float u) {
        float step = rowStep(u);
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            float y = rowsTop + i * step - Math.round(scrollOffset);
            row.yAt = y;
            // 【控件的格子不在这里摆了（A-10 第二步）】行内控件的几何由 Trellis 的树算
            // （{@code TrellisColumn.controlBox}），控件自己不再存 {@code x/y/w/h} ——
            // 从前这一句 {@code at(...)} 就是"第二份几何"，与树里的盒子差 1.3 逻辑 px（A-4）。
        }
    }

    // ---- 几何：按画布算，不写死（控件右对齐到配置列右缘，留滚动条那侧的呼吸位） ----
    //
    // 【这是对照侧，不是生产路径（A-14 起也跟着 u 缩放）】下面三条是**独立实现**的一份算术：
    // 生产上控件矩形由 Trellis 的树给（{@code TrellisColumn.controlBox}），宿主不再自己算。
    // 留着它是为了让 {@code TrellisHitParityTest} 有一份"别人写一遍"的口径可比 ——
    // 那 1.3 逻辑 px 的水平差就是这么量出来的。u 变了它必须跟着缩，否则对的就不是同一件事了。

    static int controlW(ConfigLayout lo, float u) {
        int room = Math.round(lo.items().w()) - Math.round(2f * Tokens.Space.STEP_3 * u);
        return Math.max(Math.round(Tokens.Size.CONTROL_MIN_W * u),
                Math.min(Math.round(Tokens.Size.CONTROL_MAX_W * u), room * 45 / 100));
    }

    static int labelX(ConfigLayout lo, float u) {
        return Math.round(lo.items().x()) + Math.round(Tokens.Space.STEP_3 * u);
    }

    static int controlX(ConfigLayout lo, float u) {
        return Math.round(lo.items().right()) - Math.round(Tokens.Space.STEP_3 * u) - controlW(lo, u);
    }

    /** 一行：标签 + 控件 + 悬停提示 + 悬停进度。
     * <p>【为什么不是 record】悬停进度是这一行的<b>状态</b>，每行一份；record 装不下。 */
    static final class Row {
        /**
         * 悬停淡入 / 淡出的时长（毫秒）。<b>住在这里</b>而不是界面类里：它是这一行的动画属性，
         * 而 {@link #driveHover} / {@link #hoverValueAt} 也要读它 —— 三个东西放一起，
         * 才有人能离线钉住它们（{@code ConfigHoverAnimationTest}）。
         */
        static final long HOVER_IN_MS = 110L;
        static final long HOVER_OUT_MS = 150L;

        final String label;
        final Widget widget;
        final String hint;
        final Animated hover = Animated.of(0f);
        /** 这一帧的行顶 y（小节头画字用；选项行以控件位置为准）。 */
        float yAt;

        Row(String label, Widget widget, String hint) {
            this.label = label;
            this.widget = widget;
            this.hint = hint;
        }

        /**
         * 把这一行的悬停进度朝目标推一步（{@code nowMs} 是这一帧的毫秒时刻）。
         *
         * <p>【为什么收在这里】这一小段同时管两件容易写错的事：<b>单位换算</b>
         * （宿主是毫秒、{@link Animated} 要纳秒，乘错 1e6 会让动画静默地快/慢 1000 倍）
         * 与<b>时长选择</b>（淡入/淡出不同）。留在界面里就只能靠肉眼看动画；搬到模型上，
         * 离线测试就能对帧断言。调用序必须是"先 drive、后取"（{@link #hoverValueAt}），
         * 与旧 {@code Tween} 一致。
         */
        void driveHover(boolean on, long nowMs) {
            hover.retarget(on ? 1f : 0f, (int) (on ? HOVER_IN_MS : HOVER_OUT_MS),
                    Easing.EASE_OUT_CUBIC, nowMs * 1_000_000L);
        }

        /** 这一帧的悬停进度（0 = 没悬停，1 = 完全悬停）。{@code nowMs} 与 {@link #driveHover} 同一个时刻。 */
        float hoverValueAt(long nowMs) {
            return hover.at(nowMs * 1_000_000L);
        }

        boolean isHeader() {
            return widget == null;
        }

        String label() {
            return label;
        }

        Widget widget() {
            return widget;
        }

        String hint() {
            return hint;
        }
    }
}
