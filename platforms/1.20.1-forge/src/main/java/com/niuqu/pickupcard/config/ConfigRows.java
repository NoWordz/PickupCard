package com.niuqu.pickupcard.config;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.Tween;

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

    /** 全界面统一的行距/行高：放不下就滚，节奏不随内容变。 */
    static final int ROW_STEP = 20;
    static final int ROW_H = 18;

    /**
     * 第一行相对配置列顶的内缩 —— 贴着列顶会和标题行糊在一起，留 2px 呼吸。
     *
     * <p>【为什么放在这里而不是写在屏幕里】它是"行模型"的几何，而且现在有第二个消费者：
     * Trellis 试点要拿同一个数当布局树的内边距（{@code TrellisBridge.outlineControls}）。
     * 放一处、两边取同一个源。反过来做（桥里补个 2）就是又一份口径 —— 2026-09-21 真机
     * 实测过后果：整列 12 行集体高 2 逻辑 px。
     */
    static final int ROWS_TOP_INSET = 2;

    private final List<Row> list = new ArrayList<>();

    /** 一行选项（标签 + 控件 + 悬停说明）。 */
    void cell(String label, NvgWidget widget, String hint) {
        list.add(new Row(label, widget, hint));
    }

    /** 小节头：占一行、不接控件，把"这几行是一伙的"画出来。 */
    void header(String title) {
        list.add(new Row(title, null, null));
    }

    List<Row> all() {
        return list;
    }

    /** 这一帧配置项内容有多高（滚动的依据）。 */
    float contentHeight() {
        return list.size() * (float) ROW_STEP;
    }

    /**
     * 逐行摆：**一行一项**（标签左、控件右），行距恒 {@link #ROW_STEP}px，放不下就滚。
     *
     * @param scrollOffset 滚动偏移（扣掉它，控件与它画出来的位置才是同一个坐标系，
     *                     否则点了会"选错行"）
     * @param rowsTop      第一行的顶 y（预览搬到右列后，配置列从自己的顶部开始）
     */
    void layout(ConfigLayout lo, float scrollOffset, float rowsTop) {
        for (int i = 0; i < list.size(); i++) {
            Row row = list.get(i);
            float y = rowsTop + i * (float) ROW_STEP - Math.round(scrollOffset);
            row.yAt = y;
            if (!row.isHeader()) {
                row.widget().at(controlX(lo), y, controlW(lo), ROW_H);
            }
        }
    }

    // ---- 几何：按画布算，不写死（控件右对齐到配置列右缘，留滚动条那侧的呼吸位） ----

    static int controlW(ConfigLayout lo) {
        int room = Math.round(lo.items().w()) - 12;
        return Math.max(48, Math.min(130, room * 45 / 100));
    }

    static int labelX(ConfigLayout lo) {
        return Math.round(lo.items().x()) + 6;
    }

    static int controlX(ConfigLayout lo) {
        return Math.round(lo.items().right()) - 6 - controlW(lo);
    }

    /** 一行：标签 + 控件 + 悬停提示 + 悬停进度。
     * <p>【为什么不是 record】悬停进度是这一行的<b>状态</b>，每行一份；record 装不下。 */
    static final class Row {
        final String label;
        final NvgWidget widget;
        final String hint;
        final Tween hover = Tween.at(0f, 0L);
        /** 这一帧的行顶 y（小节头画字用；选项行以控件位置为准）。 */
        float yAt;

        Row(String label, NvgWidget widget, String hint) {
            this.label = label;
            this.widget = widget;
            this.hint = hint;
        }

        boolean isHeader() {
            return widget == null;
        }

        String label() {
            return label;
        }

        NvgWidget widget() {
            return widget;
        }

        String hint() {
            return hint;
        }
    }
}
