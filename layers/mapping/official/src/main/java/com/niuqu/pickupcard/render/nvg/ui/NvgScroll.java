package com.niuqu.pickupcard.render.nvg.ui;

/**
 * <b>⚠️ 现在只作对照侧：没有生产调用方了</b>（2026-09-22 / A-16 起）。
 * 配置列那三件事 —— 偏移、夹取、裁剪框 —— 全部归了框架的
 * {@code dev.e33.trellis.ui.ScrollContainer}，这个类与 {@link ScrollMath} 只剩
 * {@code ScrollMathTest} 还在测。留着是有意的：它是当年那套滚轮口径唯一的成文证据，
 * 顺带也是"一格实际滚了 9 行、注释却写着 3 行"那个 bug 的现场
 * （见 {@code TrellisColumn.ROWS_PER_NOTCH} 与 {@code TrellisWheelStepTest}）。
 * 要删就这两个类连同测试一起删，别只删一半。
 *
 * <p>一个可滚动的列表视口：把"滚轮 / 裁剪 / 命中"这三件事收在一处。
 * <p>
 * 【为什么不是控件】它不画自己（滚动条要不要画由界面决定），只回答四个问题：
 * 能滚到哪、现在在哪、鼠标指着第几行、裁剪框推给谁。
 * <p>
 * 【坐标约定】构造时给的是<b>屏幕</b>逻辑坐标；{@link #contentY} 负责把内容坐标换成屏幕坐标。
 * 绘制一律用 {@code contentY(行顶)}，命中一律用 {@link #rowAt} —— 两边都减同一个 offset，
 * 才不会出现"看着在第 3 行、点下去选中第 4 行"。
 */
public final class NvgScroll {

    /** 滚轮一格滚几行。3 行是"能连贯看内容"的常见手感。 */
    public static final float ROWS_PER_NOTCH = 3f;

    private final float x;
    private final float y;
    private final float width;
    private final float height;
    private float offset;

    public NvgScroll(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float offset() {
        return offset;
    }

    /** 直接设偏移（换页清零 / 同页重建保留，都从这儿走）。越界值交给下一次 {@link #reflow} 夹。 */
    public void scrollTo(float value) {
        offset = value;
    }

    /** 内容坐标 -> 屏幕坐标。 */
    public float contentY(float contentTop) {
        return y + contentTop - offset;
    }

    /** 内容比视口高吗（要不要画滚动条）。 */
    public boolean scrollable(float contentHeight) {
        return ScrollMath.maxOffset(contentHeight, height) > 0f;
    }

    /** 内容变短/换页之后，把偏移收回合法范围。 */
    public void reflow(float contentHeight) {
        offset = ScrollMath.clamp(offset, contentHeight, height);
    }

    /**
     * 滚轮。
     *
     * @return 偏移真的变了才 true —— 界面据此决定要不要吞掉这次事件
     */
    public boolean wheel(double delta, float contentHeight, float rowHeight) {
        float next = ScrollMath.wheel(offset, delta, rowHeight, ROWS_PER_NOTCH, contentHeight, height);
        boolean changed = next != offset;
        offset = next;
        return changed;
    }

    /** 鼠标在这个视口里指着第几行；不在视口里返回 -1。 */
    public int rowAt(double mouseX, double mouseY, float rowHeight) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) {
            return -1;
        }
        return ScrollMath.rowAt((float) mouseY - y, offset, rowHeight);
    }

    /** 推裁剪框（配 {@code ui.popClip()}）。形状与文字两套裁剪由 NvgUi 一次设好。 */
    public void pushClip(NvgUi ui) {
        ui.pushClip(x, y, width, height);
    }
}
