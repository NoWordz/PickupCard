package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.PaintCtx;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.ui.UiTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-11 的离线对账：<b>键盘与焦点也归组件树</b>。
 *
 * <p>【这一条在钉什么】键盘不再由宿主每帧遍历所有控件兜底转发：键只发给
 * <b>树里那个焦点组件</b>（{@code UiTree.keyDown}），控件收到的焦点由树同步
 * （{@link NvgWidget#focusChanged(boolean)}）。于是"谁能收到键"与"谁拿着焦点"是同一次决定 ——
 * 从前控件自己那份 {@code focused} 与树里那份可以不一样，而"不一样"的表现是
 * "光标亮在这一行、字打到那一行去"这种只有肉眼能发现的错。
 *
 * <p>【为什么不跑真机也能验】树、适配器、控件替身三样都不碰游戏：布局是纯函数，事件直接调
 * {@code pointerDown / keyDown / charTyped / requestFocus}。真机那一步（harness）
 * 验的是"点进去打字有反应、再点别处焦点交还"；这里验的是<b>路由与顺序</b>：
 * 谁收到、收到什么、没焦点时谁都不收到、拒收与"没人管"能不能分开。
 */
class TrellisKeyRoutingTest {

    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;

    /**
     * 这些测试量的是<b>几何关系</b>（谁在谁旁边、差多少），所以跑在<b>设计基准 u</b> 上 ——
     * 关系对任何 u 都成立，钉在基准上就让断言值保持"设计稿那一版"的整数，读起来一眼能对。
     * 自适应路径本身由 {@link TrellisTokenGeometryTest#adaptiveGeometryFollowsCanvasHeight} 与
     * 框架的 {@code UnitsTest} 盯着。
     */
    private static final float U = Tokens.Unit.BASE;

    /** 随便两个键码：这几条验的是路由，不是某个具体键的语义。 */
    private static final int KEY_A = 65;
    private static final int KEY_BACKSPACE = 259;
    /**
     * 修饰键位掩码：GLFW 里 {@code MOD_CONTROL} 就是 2（{@code MOD_SHIFT} 是 1）。
     * 这几条验的是"原样透传"，所以值取真口径 —— 免得下一个人照名字把位序记反。
     */
    private static final int MOD_CONTROL = 2;

    /** 三行：控件 / 小节头 / 控件 —— 小节头那一行没有任何控件可以收到焦点。 */
    private final List<String> focusLog = new ArrayList<>();
    private final KeyRecorder first = new KeyRecorder("first", focusLog);
    private final KeyRecorder third = new KeyRecorder("third", focusLog);

    @Test
    @DisplayName("树上没焦点：keyDown/charTyped 都返回 false，且没有任何控件收到")
    void withoutFocusNobodyReceivesKeys() {
        UiTree ui = column();

        assertNull(ui.focused(), "还没点过谁，树上不该有焦点");
        assertFalse(ui.keyDown(KEY_A, 0), "没焦点就该返回 false（宿主据此走 MC 的默认处理）");
        assertFalse(ui.charTyped('x'));

        assertEquals(0, first.keys + third.keys, "没焦点时键不该发给任何控件");
        assertEquals(0, first.chars + third.chars);
        assertEquals(-1, TrellisColumn.focusedControlRow(ui), "没有焦点控件时行号必须是 -1");
    }

    @Test
    @DisplayName("点第一行拿焦点：键与字符只到那一行的控件，别的行一个都收不到")
    void keysGoOnlyToTheFocusedRow() {
        UiTree ui = column();

        ui.pointerDown(centerX(ui, 0), centerY(ui, 0));
        assertTrue(first.focused(), "按下之后树该把焦点同步给这一格");
        assertEquals(0, TrellisColumn.focusedControlRow(ui));

        assertTrue(ui.keyDown(KEY_A, 0), "焦点控件收下了这次按键，树该说'吃掉了'");
        assertTrue(ui.charTyped('x'));

        assertEquals(1, first.keys);
        assertEquals(1, first.chars);
        assertEquals(KEY_A, first.lastKeyCode);
        assertEquals('x', first.lastChar);
        assertEquals(0, third.keys + third.chars, "键不该发给没焦点的那一行");
    }

    @Test
    @DisplayName("点另一行：旧控件【先】收 BLUR、新控件【再】收 FOCUS，行的读数跟着换")
    void clickingAnotherRowMovesFocusOldFirstThenNew() {
        UiTree ui = column();
        ui.pointerDown(centerX(ui, 0), centerY(ui, 0));
        ui.keyDown(KEY_A, 0);      // 焦点在第一行时先来一个键，换完焦点才看得出"键跟着走"
        focusLog.clear();
        ui.pointerDown(centerX(ui, 2), centerY(ui, 2));

        // 顺序写死在断言里：反过来的话，新控件会在旧控件还开着编辑态时就开始动作
        assertEquals(List.of("blur:first@false", "focus:third@true"), focusLog,
                "换焦点必须是先旧的 BLUR、后新的 FOCUS，而且通知时标志已经落地");
        assertFalse(first.focused());
        assertTrue(third.focused());
        assertEquals(2, TrellisColumn.focusedControlRow(ui));

        ui.keyDown(KEY_A, 0);
        assertEquals(1, third.keys, "焦点换过去之后，键该跟着换人");
        assertEquals(1, first.keys, "第一行只收到换焦点之前那一次");
    }

    @Test
    @DisplayName("requestFocus(null)：旧控件收到 BLUR，之后 keyDown 返回 false")
    void requestFocusNullReleasesTheWidget() {
        UiTree ui = column();
        ui.pointerDown(centerX(ui, 0), centerY(ui, 0));

        focusLog.clear();
        ui.requestFocus(null);

        assertEquals(List.of("blur:first@false"), focusLog, "交还焦点只发 BLUR（不许顺手发别的）");
        assertNull(ui.focused());
        assertFalse(first.focused());
        assertEquals(-1, TrellisColumn.focusedControlRow(ui));
        assertFalse(ui.keyDown(KEY_BACKSPACE, 0), "交还之后没人管键盘了");
        assertEquals(0, first.keys, "交还焦点之后不该再有键走到控件");
    }

    @Test
    @DisplayName("修饰键原样到控件手里：传 2，控件收到的就是 2")
    void modifiersReachTheWidgetUnchanged() {
        UiTree ui = column();
        ui.pointerDown(centerX(ui, 0), centerY(ui, 0));

        assertTrue(ui.keyDown(KEY_A, MOD_CONTROL));

        assertEquals(MOD_CONTROL, first.lastModifiers,
                "修饰键被吞掉或被改成别的口径，Ctrl/Shift 组合键就没法做");
    }

    @Test
    @DisplayName("控件拒收时：keyDown 返回 false，但树上仍然有焦点（'拒收'与'没人管'能分开）")
    void rejectedKeyIsDistinguishableFromNoFocus() {
        UiTree ui = column();
        ui.pointerDown(centerX(ui, 0), centerY(ui, 0));
        first.acceptKeys = false;      // 文本框不在编辑态时就是这样

        assertFalse(ui.keyDown(KEY_A, 0), "控件拒收 → 树说没人吃掉（宿主这时才该走默认处理）");

        assertNotNull(ui.focused(),
                "拒收不等于没人管：要区分这两种情况只能问 tree.focused()（返回值两者都 false）");
        assertEquals(0, TrellisColumn.focusedControlRow(ui), "焦点还在那一行的控件上");
    }

    @Test
    @DisplayName("KEY_UP 不被控件吃掉：树返回 false（它还留在 MC 的默认路径上）")
    void keyUpIsNotSwallowed() {
        UiTree ui = column();
        ui.pointerDown(centerX(ui, 0), centerY(ui, 0));

        assertFalse(ui.keyUp(KEY_A, 0),
                "现在没有任何控件需要抬起键；ControlSlot 接它就会把它从 MC 的默认路径上抢走");
        assertEquals(0, first.keys, "抬起不该被当成按下再发一次");
    }

    @Test
    @DisplayName("焦点不再由控件自己挣：press 不拿焦点，hover(false) 也不清焦点")
    void theWidgetDoesNotFightForFocus() {
        // 直接给控件一个盒子：这一条根本不经过树（它验的是"控件自己不动焦点"）
        Rect box = control(column(), 0);

        assertTrue(first.press(box, box.x() + 1f, box.y() + 1f, 0));
        assertFalse(first.focused(), "按下不再顺手拿焦点（那是树的决定，见 UiTree.pointerDown）");

        first.focusChanged(true);
        assertTrue(first.focused());
        first.hover(false);
        assertTrue(first.focused(), "悬停走开不该清焦点（指针飘一下不该让键盘换人）");

        first.focusChanged(false);
        assertFalse(first.focused());
        focusLog.clear();
        first.focusChanged(false);
        assertEquals(List.of(), focusLog, "值没变就不该再通知一次");
    }

    private static Rect control(UiTree ui, int row) {
        return ui.root().children().get(row).children().get(1).bounds();
    }

    private static float centerX(UiTree ui, int row) {
        return control(ui, row).x() + 1f;
    }

    private static float centerY(UiTree ui, int row) {
        return control(ui, row).y() + 1f;
    }

    private UiTree column() {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        NvgWidget[] controls = {first, null, third};
        UiTree ui = TrellisColumn.buildColumn(controls, ConfigRows.topInset(U), U);
        TrellisColumn.layoutColumn(ui, lo.items(), 0f, 1f / GUI_SCALE);
        return ui;
    }

    /** 记下收到的键、字符与焦点通知（不画、不碰配置）。 */
    private static final class KeyRecorder extends NvgWidget {

        private final List<String> focusLog;
        /** 收到的键/字符要不要算"吃掉"（文本框不在编辑态时返回 false）。 */
        boolean acceptKeys = true;
        int keys;
        int chars;
        int lastKeyCode = -1;
        int lastModifiers = -1;
        char lastChar;

        KeyRecorder(String label, List<String> focusLog) {
            super(label);
            this.focusLog = focusLog;
        }

        @Override
        public boolean keyPressed(int keyCode, int modifiers) {
            keys++;
            lastKeyCode = keyCode;
            lastModifiers = modifiers;
            return acceptKeys;
        }

        @Override
        public boolean charTyped(char c) {
            chars++;
            lastChar = c;
            return acceptKeys;
        }

        /**
         * 【为什么把 {@code focused()} 也记进去】树保证"状态先落地、再通知"：记到的那个值
         * 必须是新值，否则每个子类都得自己猜"这个通知之后轮到我了吗"。
         */
        @Override
        protected void onFocusChanged(boolean value) {
            focusLog.add((value ? "focus:" : "blur:") + label() + "@" + focused());
        }

        @Override
        protected void paint(PaintCtx ctx) {
        }
    }
}
