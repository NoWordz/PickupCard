package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Rect;

/**
 * 自绘控件的地基：<b>一块矩形（由调用方给） + 悬停/按下/焦点三个状态 + 一次激活</b>。
 *
 * <p>【为什么不做事件树/布局引擎】这个界面只有二十来个控件、都是同一行里等宽等高的格子，
 * 排布由配置界面按"第几行第几列"算一次就够。旧版那条注释写得很准：
 * <b>不许长成第二个 UI 框架</b> —— 我们不需要 View 树、焦点漫游、布局约束这些东西，
 * 它们解决的问题这个界面一个都没有。
 *
 * <p>【A-10 第二步起：控件不再存几何】从前控件自己带着 {@code x/y/w/h}（{@code at(...)} 灌的，
 * 是宿主的整数取整版），而命中、悬停底、标签读的是 Trellis 的 {@code bounds()} ——
 * 两份几何，实测差 1.3 逻辑 px（A-4）。现在<b>谁画它，谁把那个 {@code Rect} 交进来</b>：
 * 行内控件由树给（给的就是 {@code bounds()} 本身），树外的控件（标签列、切样例按钮、
 * 锚点编辑场那两个按钮）由它们的排布者给。控件只认"盒子里画在哪"，不认"盒子在屏幕哪里"。
 *
 * <p>【形状与文字分两处提交】见 {@link PaintCtx}：形状走 Trellis 原语，文字走
 * {@link GlyphPainter}（MC 的位图字形只能由 MC 画）—— 控件不用管这件事，
 * 它调 {@code ctx.text(...)} 时只是把位置交出去。
 *
 * <p>【焦点为什么归树（A-11 起）】焦点只有一份真相，住在
 * {@code dev.e33.trellis.ui.UiTree} 里（那棵树知道谁拿着）；控件这一份是被它
 * <b>同步过来的状态</b>，和 {@link #hover} 那一份同一个性质 —— 由 {@link #focusChanged(boolean)}
 * 写，控件自己不许再有第二个地方写它。这样做才有"键只发给焦点那一个"这件事：
 * 从前焦点是控件自己挣的（按一下、悬停走开就清掉），宿主只能每帧遍历所有控件兜底转发。
 */
public abstract class NvgWidget {

    private final String label;
    /**
     * 鼠标悬停 / 被按下 / 持有键盘焦点。前两个由调用方给，焦点由 {@code UiTree} 同步 ——
     * 三个状态下控件长得不一样，这是"能操作"的唯一提示。
     */
    protected boolean hovered;
    protected boolean pressed;
    /** <b>树同步过来的</b>（唯一写入口是 {@link #focusChanged(boolean)}，见类注释）。子类只读。 */
    protected boolean focused;

    protected NvgWidget(String label) {
        this.label = label;
    }

    /** 玩家看到的那一行名字（也是 harness 按名字找控件的键）。 */
    public final String label() {
        return label;
    }

    /** 控件里显示的那行值（滑条的数字、开关的开/关）。日志与 harness 读它。 */
    public String value() {
        return "";
    }

    /** 控件底的配色：按下 > 悬停 > 常态。 */
    protected final int wellColor(NvgPalette palette) {
        // 【为什么只有 hovered（A-15）】焦点从 A-15 起有自己的视觉（框架基类画的焦点环），
        // 不再借悬停这一层 —— 借的话同一个控件会同时出现"悬停底色"和"焦点环"两种提示。
        return pressed ? palette.wellPressed : hovered ? palette.wellHover : palette.well;
    }

    /**
     * 画自己。<b>final</b>：界面靠这一次调用记下"这一帧画过谁"，子类只实现 {@link #paint}。
     *
     * <p>【为什么控件要自己记这件事】"控件在、也能点，就是没画"是自绘界面的专属故障：没有原版
     * {@code children()} 那种"加了就会被画"的保证，漏一次绘制调用的症状是<b>屏幕上一块空白、
     * 而点击完全正常</b>（配置界面重构时标签列就这么整列空过）。控件自己知道有没有被画过，
     * 界面每帧问一次 {@code paintedIn} 就够了 —— 不记账的话这种 bug 只有肉眼能发现。
     */
    public final void draw(PaintCtx ctx) {
        this.paintedFrame = ctx.now();
        paint(ctx);
    }

    /** 子类在这里画自己（形状 + 登记文字）。几何从 {@code ctx.box()} 来。 */
    protected abstract void paint(PaintCtx ctx);

    /** 这一帧画过我吗（{@code frame} 用 {@link PaintCtx#now()}）。 */
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
     * 树外的控件由屏幕自己判。{@code box} 是<b>判定命中用的那一份几何</b>——
     * 需要"点在盒子里的哪儿"的控件（滑条点哪跳哪）就在这里读它，不许自己再存一份。
     *
     * @return 这次按下要不要吃掉（按钮不对就吃不掉）
     */
    public boolean press(Rect box, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        pressed = true;
        // 【按下不再顺手拿焦点】谁拿焦点由树在命中时决定（{@code UiTree.pointerDown →
        // requestFocus}），控件只收结果 —— 自己在这里写一份就是第二份真相。
        return true;
    }

    /**
     * 松开。{@code activate} 是<b>调用方</b>的判断："按下与抬起是同一格"由它保证
     * （树那边用的就是 {@code bounds().contains(落点)} —— 同一份几何，不是第二份）。
     *
     * <p>无论如何都把 {@code pressed} 收回去：拖到格子外面松手，手感也必须结束。
     */
    public void release(Rect box, double mouseX, double mouseY, boolean activate) {
        boolean wasPressed = pressed;
        pressed = false;
        if (wasPressed && activate) {
            onActivate();
        }
    }

    /**
     * 拖拽：滑条要靠它才有手感。默认什么都不做。
     *
     * <p>{@code box} 与 {@code press} 收到的必须是同一份 —— 滑条把指针位置换算成比例时要它。
     */
    public void drag(Rect box, double mouseX, double mouseY) {
    }

    /**
     * 悬停也由调用方给（行内控件来自树，树外的来自屏幕自己的命中）。
     *
     * <p>它<b>不动焦点</b>：指针飘一下不该让键盘换人（否则鼠标一动输入就跑到别处去了）。
     * 焦点的事只有一处 —— 树在按下时决定，然后把 {@link #focusChanged(boolean)} 推过来。
     */
    public void hover(boolean value) {
        hovered = value;
    }

    /** 敲键盘（只有文本框这类需要）。 */
    public boolean keyPressed(int keyCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char c) {
        return false;
    }

    /**
     * 我是不是那个拿着键焦点的控件。<b>只读</b> —— 状态由树同步（见类注释）。
     */
    public final boolean focused() {
        return focused;
    }

    /**
     * 焦点变了（树在 {@code FOCUS} / {@code BLUR} 事件里推过来）。
     *
     * <p><b>final</b>：子类改不了"标志必须先落地、再让子类反应"这件事。它只写
     * {@link #onFocusChanged(boolean)}，于是子类里问 {@link #focused()} 拿到的<b>一定</b>是
     * 新值（否则每个子类都得自己猜"这个通知之后轮到我了吗"）。
     */
    public final void focusChanged(boolean value) {
        if (focused == value) {
            return;         // 没变就不通知：重复通知会把"一拿焦点就全选"变成"每次都重来一遍"
        }
        focused = value;
        onFocusChanged(value);
    }

    /**
     * 拿到 / 交还焦点的反应（默认什么都不做）。
     *
     * <p>【为什么要有这个钩子】文本框交还焦点时必须收尾（停掉编辑态、丢掉草稿），
     * 而"标志先落地"这条规矩不能交给子类去遵守 —— 从前它是 {@code blur()} 的 override，
     * 谁都可以在里面把标志写坏。
     */
    protected void onFocusChanged(boolean value) {
    }

    /** 点一下释放时触发（按钮/开关/循环都用这个语义）。 */
    protected void onActivate() {
    }
}
