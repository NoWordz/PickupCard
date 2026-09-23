package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.layout.Style;
import dev.e33.trellis.render.Canvas;
import dev.e33.trellis.ui.Component;
import dev.e33.trellis.ui.Surface;
import dev.e33.trellis.ui.UiEvent;
import dev.e33.trellis.ui.widget.Widget;

/**
 * 一格里那颗宿主控件：<b>几何归树（{@code bounds()} 就是这一格），行为仍归宿主控件</b>。
 *
 * <p>【它从哪儿来】A-10 第三步起它是 {@code TrellisColumn} 的私有内部类（叫 {@code ControlSlot}）；
 * A-17 编辑场也要把按钮放进树，于是提到这里共用 —— 两个界面都用同一套"槽"，就不会出现
 * 两套略有差别的适配（那种差别只有肉眼比两个界面才看得出来）。
 *
 * <p>【焦点与键盘都在这条路上】{@code focusable(true)} 开着，于是按下会走
 * {@code UiTree.pointerDown → requestFocus}；焦点一变，这一格就收到 BLUR / FOCUS 并转给控件，
 * 而树把键<b>只发给焦点组件</b>（{@code UiTree.keyDown → 这一格 → 控件}）。于是"控件拿着焦点"
 * 与"键到控件"来自同一个决定，控件不再自己挣焦点。
 *
 * <p>【"算不算一次点击"为什么在这里判】{@code UiTree.pointerUp} 保证抬起发给
 * <b>按下的那一个</b>（指针捕获），但 {@code CLICK} 是在 {@code POINTER_UP} <b>之后</b>才发的 ——
 * 在这里等 CLICK 的话，拖到格子外面松手就永远收不到"结束"，{@code pressed} 会留在控件上
 * （症状：松了手还亮着）。所以用 {@link Component#bounds()} 判落点在不在格子里：
 * 那是<b>同一份几何</b>（命中测试读的也是它），不是第二份。
 */
public final class WidgetSlot extends Component {

    private final Widget widget;

    /** 这一帧的表面（{@link #attachFrame} 每帧灌一次；null = 没接上，绘制时当场抛）。 */
    private TrellisColumn.Frame frame;

    public WidgetSlot(Widget widget, Style style, NvgPalette palette) {
        this.widget = widget;
        style(style);
        focusable(true);
        // 【焦点环（A-15）】画法与几何归框架（基类 `Component.focusRing`，加一次全体正确）；
        // 颜色由宿主给 —— 浅色主题下框架那个亮青在近白底上读不出来。
        // 环的圆角取调色板的 radius，和控件自己画的那条 outline 是同一个形状口径。
        focusRing(palette.focusRing, palette.radius);
        // 【只给圆角、不给底板（A-20）】基类那笔悬停/按下叠加层用的是 `surface.radius()`，
        // 而这里从前<b>没设底板</b>（Surface.NONE → 半径 0）—— 于是它是**直角**白雾，
        // 盖在控件自己画的**圆角**填充上，四个角会溢出到控件外面（涂在面板/背景上）。
        // 离屏量过：悬停角上最高 19/255、按下最高 57/255，且是硬边的方形（见
        // `examples/OverlayComparisonExample`，跑 `./gradlew :examples:overlayCompare` 出图）。
        //
        // fill / border / borderWidth 全 0 → `Surface.isEmpty()` 为真，**底板一个字都不画**
        // （画了就等于给每个控件凭空加一层底）；只有 radius 参与，那一笔因此跟着圆角走。
        // 主体的双重上色（+6 / +14 白）本次<b>保留</b>：它轻微，而彻底关掉要连
        // `Toggle` / `ColorChip` 一起改成自画状态，那是另一个决定（见 A-20 的遗留）。
        surface(new Surface(0, 0, palette.radius, 0f));
    }

    /** 这一格托着的控件。 */
    public Widget widget() {
        return widget;
    }

    /**
     * 把这一帧的表面接上来（{@code TrellisColumn.paint} / {@code TrellisColumn.surface} 每帧灌）。
     *
     * <p>灌 null 就是"这一帧结束了"：{@link #drawContent} 拿到 null 会当场抛，
     * 因为静默跳过会变成"控件在、点得到、屏幕上一块空白"。
     */
    void attachFrame(TrellisColumn.Frame frame) {
        this.frame = frame;
    }

    @Override
    protected boolean onEvent(UiEvent event) {
        switch (event.type()) {
            case POINTER_DOWN:
                // 几何给 {@code bounds()} 那个对象本身 —— 与命中读的是同一个（判据 1）。
                return widget.press(bounds(), event.x(), event.y(), 0);
            case POINTER_UP:
                widget.release(bounds(), event.x(), event.y(),
                        bounds().contains(event.x(), event.y()));
                return true;
            // 【键盘（A-11）】键只到这里来（树把键发给<b>焦点组件</b>），
            // 再由这一格转给控件。于是"控件收到键"与"控件拿着焦点"是同一件事的两个面，
            // 返回值也原样交回去：控件拒收（返回 false）时树照实说"没人吃掉"。
            case KEY_DOWN:
                return widget.keyPressed(event.keyCode(), event.modifiers());
            case CHAR:
                return widget.charTyped(event.character());
            // 【焦点事件是通知，一定吃掉】BLUR / FOCUS 本身就是"你交还了 / 你拿到了"，
            // 放它继续冒泡没有别的意思。顺序由树保证：先旧的 BLUR、后新的 FOCUS。
            case BLUR:
                widget.focusChanged(false);
                return true;
            case FOCUS:
                widget.focusChanged(true);
                return true;
            // 【KEY_UP 故意不接】现在没有任何控件需要它；这里返回 true 会把这个键
            // 从 MC 的默认路径上抢走（谁来收、收得对不对都看不出来）。
            // 等真有"按住/抬起"的手感可做时再回来加，别提前占位。
            default:
                return false;
        }
    }

    /**
     * <b>控件本体由这里画（A-10 第二步）。</b>
     *
     * <p>几何给 {@code bounds()} 本身、形状走这个画布、字形交回宿主 —— 三件事都在
     * {@link PaintCtx} 手里，控件自己不再存任何几何。宿主的 {@code row.widget().draw(ui)}
     * 因此停手：两个都画就是两份几何，正是判据 1 要根除的东西。
     *
     * <p>【没接上表面就抛】见 {@link #attachFrame}：静默跳过会变成"控件在、点得到、
     * 屏幕上一块空白"，那种 bug 只有肉眼能发现。
     */
    @Override
    protected void drawContent(Canvas canvas) {
        if (frame == null) {
            throw new IllegalStateException("这一趟没有表面：TrellisColumn.paint(...) "
                    + "没被调用，或者它在 ui.draw(...) 之外被调了。");
        }
        widget.draw(frame.ctxFor(bounds()));
    }
}
