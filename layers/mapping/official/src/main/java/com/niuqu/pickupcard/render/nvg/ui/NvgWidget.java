package com.niuqu.pickupcard.render.nvg.ui;

/**
 * 自绘控件的地基：<b>一块矩形 + 悬停/按下/焦点三个状态 + 一次激活</b>。
 *
 * <p>【为什么不做事件树/布局引擎】这个界面只有二十来个控件、都是同一行里等宽等高的格子，
 * 排布由配置界面按"第几行第几列"算一次就够。旧版那条注释写得很准：
 * <b>不许长成第二个 UI 框架</b> —— 我们不需要 View 树、焦点漫游、布局约束这些东西，
 * 它们解决的问题这个界面一个都没有。
 *
 * <p>【为什么形状与文字分两处提交】见 {@link NvgUi}：形状走 NanoVG、文字走原版批次，
 * 但控件不用管这件事 —— 它调 {@code ui.text(...)} 时只是登记，顺序由 {@link NvgUi#close()} 保证。
 */
public abstract class NvgWidget {

    private final String label;
    protected float x;
    protected float y;
    protected float w;
    protected float h;
    /** 鼠标悬停 / 被按下 / 持有键盘焦点。三个状态下控件长得不一样，这是"能操作"的唯一提示。 */
    protected boolean hovered;
    protected boolean pressed;
    protected boolean focused;

    protected NvgWidget(String label) {
        this.label = label;
    }

    /** 玩家看到的那一行名字（也是 harness 按名字找控件的键）。 */
    public final String label() {
        return label;
    }

    public final NvgWidget at(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    public final float x() {
        return x;
    }

    public final float y() {
        return y;
    }

    public final float width() {
        return w;
    }

    public final float height() {
        return h;
    }

    public final boolean hit(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** 控件里显示的那行值（滑条的数字、开关的开/关）。日志与 harness 读它。 */
    public String value() {
        return "";
    }

    /** 控件底的配色：按下 > 悬停 > 常态。 */
    protected final int wellColor(NvgPalette palette) {
        return pressed ? palette.wellPressed : (hovered || focused) ? palette.wellHover : palette.well;
    }

    /**
     * 画自己。<b>final</b>：界面靠这一次调用记下"这一帧画过谁"，子类只实现 {@link #paint}。
     * <p>
     * 【为什么控件要自己记这件事】"控件在、也能点，就是没画"是自绘界面的专属故障：没有原版
     * {@code children()} 那种"加了就会被画"的保证，漏一次绘制调用的症状是**屏幕上一块空白、
     * 而点击完全正常**（配置界面重构时标签列就这么整列空过）。控件自己知道有没有被画过，
     * 界面每帧问一次{@code paintedIn}就够了 —— 不记账的话这种 bug 只有肉眼能发现。
     */
    public final void draw(NvgUi ui) {
        this.paintedFrame = ui.now;
        paint(ui);
    }

    /** 子类在这里画自己（形状 + 登记文字）。 */
    protected abstract void paint(NvgUi ui);

    /** 这一帧画过我吗（{@code frame} 用 {@link NvgUi#now}）。 */
    public final boolean paintedIn(long frame) {
        return paintedFrame == frame;
    }

    /** 子类的绘制实现里要记的帧号 —— 就是当前这一帧的时刻。 */
    private long paintedFrame = -1L;

    // ------------------------------------------------------------------
    // 事件：命中由调用方判（行内控件是树，树外的控件是屏幕自己）
    // ------------------------------------------------------------------

    /**
     * 按下。<b>命中已经由调用方判过了</b>：行内控件由 Trellis 的树判（{@code Component.bounds()}），
     * 树外的控件（标签列、切样例按钮）由屏幕自己判。
     *
     * <p>【为什么控件不再自己判命中】判据 1 要的是"画出来的、点得到的、指到的是同一个
     * {@code Rect}"。行内控件的几何现在由树算，控件自己那份 {@code x/y/w/h} 是宿主的整数
     * 取整版 —— 让控件再判一次就是又一份几何，边带里会出现"树说点到了、控件说没有"。
     *
     * @return 这次按下要不要吃掉（按钮不对就吃不掉）
     */
    public boolean press(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        pressed = true;
        focused = true;
        return true;
    }

    /**
     * 松开。{@code activate} 是<b>调用方</b>的判断："按下与抬起是同一格"由它保证
     * （树那边用的就是 {@code bounds().contains(落点)} —— 同一份几何，不是第二份）。
     *
     * <p>无论如何都把 {@code pressed} 收回去：拖到格子外面松手，手感也必须结束。
     */
    public void release(double mouseX, double mouseY, boolean activate) {
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && activate) {
            onActivate();
        }
    }

    /** 悬停也由调用方给（行内控件来自树，树外的来自屏幕自己的命中）。 */
    public void hover(boolean value) {
        hovered = value;
        if (!value && !pressed) {
            focused = false;
        }
    }

    // ---- 下面三个是"命中由自己判"的那条老路：树外的控件还在用 ----

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return hit(mouseX, mouseY) && press(mouseX, mouseY, button);
    }

    public void mouseReleased(double mouseX, double mouseY) {
        release(mouseX, mouseY, hit(mouseX, mouseY));
    }

    /** 拖拽：滑条要靠它才有手感。默认什么都不做。 */
    public void mouseDragged(double mouseX, double mouseY) {
    }

    public void mouseMoved(double mouseX, double mouseY) {
        hover(hit(mouseX, mouseY));
    }

    /** 敲键盘（只有文本框这类需要）。 */
    public boolean keyPressed(int keyCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }

    /** 点在别处：交还焦点。 */
    public void blur() {
        focused = false;
        pressed = false;
    }

    /** 点一下释放时触发（按钮/开关/循环都用这个语义）。 */
    protected void onActivate() {
    }
}
