package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.ui.widget.Keys;
import dev.e33.trellis.ui.widget.Slider;
import dev.e33.trellis.ui.widget.Toggle;
import dev.e33.trellis.ui.widget.Widget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-29 的<b>集成</b>层：真的 {@link Toggle} / {@link Slider} 放进树里，用键把它拨动。
 *
 * <p>【与已有两条测试的分工，别重复】
 * <ul>
 *   <li>{@code TrellisKeyRoutingTest} 验"键走不走到焦点控件"—— 它用的是<b>永远收下的替身</b>
 *       （{@code Probe.keyPressed} 直接 {@code return true}），所以"控件到底接不接这个键"
 *       它测不到。</li>
 *   <li>框架的 {@code WidgetKeyboardTest} 验每个控件自己认不认键 —— 但那是<b>直调</b>
 *       {@code keyPressed}，不经过树。</li>
 *   <li>这一条把两半接起来：<b>真控件 + 真树</b>，而且用真实的状态回写（{@code onChange} 改一个
 *       测试自己拿着的格子）—— 于是"按空格开关真的翻了"是被观测的行为，不是被断言的回调次数。</li>
 * </ul>
 *
 * <p>【为什么不用宿主配置里的真控件】那会去碰 Forge 配置（见 {@code TestWidgets} 的说明）。
 * 这里的 Toggle / Slider 是真的框架控件，只是状态由一个测试持有的格子供给 —— 不碰游戏。
 */
class TrellisWidgetKeyboardTest {

    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;
    private static final float U = Tokens.Unit.BASE;

    private static final NvgPalette TEST_PALETTE =
            NvgPalette.dark(com.niuqu.pickupcard.style.StyleModel.Accents.defaults(), U);

    /** 开关拨动写回这里：断言读它，就是"玩家看到的那个开关真的翻了"。 */
    private boolean toggleOn;
    private final Toggle toggle = new Toggle("t", () -> toggleOn, v -> toggleOn = v,
            () -> "On", () -> "Off");

    /** 滑条值写回这里。 */
    private double level = 500;
    private final Slider slider = new Slider("s", 0, 1000, 100, () -> level, v -> level = v,
            d -> String.valueOf(d.intValue()));

    @Test
    @DisplayName("真开关进树：Tab 到它拿焦点，空格把它拨开（键从树走到控件，状态真的变了）")
    void realToggleFlipsOnSpaceThroughTheTree() {
        UiTree ui = column(toggle, null, null);

        assertFalse(toggleOn, "开局该是关的");
        focusRow(ui, 0);

        assertTrue(ui.keyDown(Keys.SPACE, 0), "空格该被开关吃掉（树说'吃掉了'）");
        assertTrue(toggleOn, "按了空格开关却没翻 —— 键没走到控件，或控件没接");
    }

    @Test
    @DisplayName("真开关进树：回车也翻，抬起再按翻回来（不是只跳一次）")
    void realToggleKeepsFlipping() {
        UiTree ui = column(toggle, null, null);
        focusRow(ui, 0);

        assertTrue(ui.keyDown(Keys.ENTER, 0));
        assertTrue(toggleOn);
        // ⚠️ 中间<b>必须</b>抬一次：不抬的第二次按下会被树判成"长按重复"，而开关对重复不动作
        // （A-33）。本喵第一版就是漏了这句 —— 那时它靠的正是"重复也翻"那个 bug。
        ui.keyUp(Keys.ENTER, 0);
        assertTrue(ui.keyDown(Keys.ENTER, 0));
        assertFalse(toggleOn, "抬起再按一次该翻回去 —— 循环得动");
    }

    @Test
    @DisplayName("真开关进树：按住不放只翻一次（A-33；从前进树的长按会让它来回抖）")
    void realToggleIgnoresHoldThroughTheTree() {
        UiTree ui = column(toggle, null, null);
        focusRow(ui, 0);

        assertTrue(ui.keyDown(Keys.SPACE, 0), "首按");
        assertTrue(toggleOn, "首按该翻到开");

        // 按住不放：树把后续的按下标成长按重复（同一个键、没抬），转发给控件
        assertTrue(ui.keyDown(Keys.SPACE, 0), "重复按下也要被吃掉");
        assertTrue(toggleOn, "长按重复不该把它翻回去 —— 从前进树这条路会让开关来回抖");
        assertTrue(ui.keyDown(Keys.SPACE, 0));
        assertTrue(toggleOn, "重复第 2 次也不该动");

        // 抬起之后再一次按下 = 新的首按
        ui.keyUp(Keys.SPACE, 0);
        assertTrue(ui.keyDown(Keys.SPACE, 0));
        assertFalse(toggleOn, "抬起再按才该翻回去");
    }

    @Test
    @DisplayName("真滑条进树：← / → 各挪一档，且树说键被吃掉了")
    void realSliderNudgesWithArrowsThroughTheTree() {
        UiTree ui = column(slider, null, null);
        focusRow(ui, 0);

        assertTrue(ui.keyDown(Keys.RIGHT, 0), "→ 该被滑条吃掉");
        assertEquals(600, level);
        assertTrue(ui.keyDown(Keys.LEFT, 0));
        assertEquals(500, level);
    }

    @Test
    @DisplayName("真滑条进树：按住不放一直走（重复照做）—— 与开关的重复不动正好相反")
    void realSliderKeepsMovingOnHold() {
        UiTree ui = column(slider, null, null);
        focusRow(ui, 0);

        assertTrue(ui.keyDown(Keys.RIGHT, 0), "首按");
        assertEquals(600, level);
        assertTrue(ui.keyDown(Keys.RIGHT, 0), "没抬又按 = 长按重复");
        assertEquals(700, level, "滑条的重复照做 —— 按住在往前走才是它的手感（评审指出宿主侧当时没钉这条）");
        assertTrue(ui.keyDown(Keys.RIGHT, 0));
        assertEquals(800, level);
    }

    @Test
    @DisplayName("没焦点时按空格：树说没人吃，开关一个字不动（'没焦点'与'控件拒收'分得开）")
    void withoutFocusTheToggleIsUntouched() {
        UiTree ui = column(toggle, null, null);

        assertFalse(ui.keyDown(Keys.SPACE, 0), "没焦点就该返回 false");
        assertFalse(toggleOn, "没焦点时键不该走到任何控件");
    }

    /**
     * 【A-29 评审逮到的那条真缺陷的回归网】宿主 {@code ConfigPageSpec.percent()} 把
     * "0 = 自动"之外的中间档全夹到 ≥50 —— 于是"从 50 按 ←"在滑条上原地不动，
     * 而那个 0 档<b>鼠标点最左端够得到</b>。键盘够不到鼠标够得到的值 = 键盘支持本身的缺陷。
     *
     * <p>这一条钉两件事：① 退到边界那条路真的接上；② 用的是<b>宿主真实的口径</b>
     * （与 {@code ConfigPageSpec.percent()} 同一个夹取规则），不是理想化的 onChange。
     */
    @Test
    @DisplayName("值域有洞：键盘能退到'自动'(0) —— 那个值鼠标够得到，键盘也必须够得到")
    void keyboardReachesTheAutoValueTheMouseCanReach() {
        double[] cfg = {50};
        Slider pct = new Slider("pct", 0, 200, 5,
                () -> cfg[0],
                v -> {
                    int p = (int) Math.round(v);
                    cfg[0] = p <= 0 ? 0 : Math.max(50, p);   // 与 ConfigPageSpec.percent 同规则
                },
                v -> v <= 0 ? "auto" : Math.round(v) + "%");
        UiTree ui = column(pct, null, null);
        focusRow(ui, 0);

        assertTrue(ui.keyDown(Keys.LEFT, 0));
        assertEquals(0, cfg[0], "从 50 按 ← 该能退到 0（自动）—— 否则这项键盘永远设不成自动");
    }

    // -----------------------------------------------------------------------

    /**
     * 让第 {@code row} 行拿到焦点 —— <b>走 Tab 那条路（{@code focusNext}），不点鼠标</b>。
     *
     * <p>【为什么不点它一下】对开关和滑条来说，"点一下"本身就是一次激活：鼠标按下再抬起会
     * 触发 {@code onActivate}（开关翻转 / 滑条跳到点的位置）。用点击来"只是为了拿焦点"，
     * 会顺手把控件改一次 —— 那验的就不再是键盘了。Tab 只换焦点、不激活，正是这一条要的干净前提。
     * （点击会不会顺带激活，是 A-10 的鼠标语义，另有 {@code TrellisControlInputTest} 盯着。）
     */
    private static void focusRow(UiTree ui, int row) {
        assertTrue(ui.focusNext(true), "树上没有可聚焦组件 —— 树没建起来？");
        assertEquals(row, TrellisColumn.focusedControlRow(ui), "Tab 该落到第 " + row + " 行");
    }

    private UiTree column(Widget row0, Widget row1, Widget row2) {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        Widget[] controls = {row0, row1, row2};
        UiTree ui = TrellisColumn.buildColumn(controls, ConfigRows.topInset(U), U, TEST_PALETTE);
        TrellisColumn.layoutColumn(ui, lo.items(), 1f / GUI_SCALE);
        return ui;
    }
}
