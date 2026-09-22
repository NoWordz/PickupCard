package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import dev.e33.trellis.ui.widget.Widget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.UiTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-11b 的宿主侧对账：<b>长按重复这个判据在真的那棵配置列上成立</b>。
 *
 * <p>【这一条在钉什么 —— 以及它<b>不</b>钉什么】它钉的是"判据 + 真列的树形态"这一层：
 * 键从树进去、账按真列的行数/层级照记、重复与首按各归各位。它<b>不</b>覆盖宿主那两趟转发
 * （{@code PickupCardConfigScreen.keyPressed / keyReleased}）—— 那几个方法要 MC 的
 * {@code Screen}/`Minecraft` 才跑得起来，纯 JUnit 里加载不了（评审指出：把 {@code keyReleased}
 * 整个删掉，这个类照样全绿）。
 *
 * <p>【那条接线由谁守】真机那一步：harness 的 `A-11b 长按重复` 探针<b>从
 * {@code Screen.keyPressed / keyReleased} 进</b>（不是直接调树），转发一断，
 * 读数里的"按后按着"就会是 false、"被判重复"永远是 0。要动这条接线，就得跑真机 ——
 * 这是本仓库对"接线类改动"一贯的口径。
 */
class TrellisKeyRepeatTest {

    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;
    private static final float U = Tokens.Unit.BASE;
    private static final NvgPalette TEST_PALETTE =
            NvgPalette.dark(StyleModel.Accents.defaults(), U);

    /** 方向键：宿主不截它（Tab 会被拿去做焦点遍历），会原样走到树里。 */
    private static final int KEY_DOWN = 264;

    @Test
    @DisplayName("真列上：没抬又按下 = 重复；抬起之后再按 = 首按")
    void repeatIsDeducedOnTheRealColumn() {
        UiTree ui = column();
        ui.pointerDown(control(ui).x() + 1f, control(ui).y() + 1f);
        int key = KEY_DOWN;

        ui.keyDown(key, 0);
        assertEquals(0, ui.repeatsDeduced(), "第一次按下不该是重复");
        assertTrue(ui.isKeyHeld(key));

        // 真机上这就是系统送来的那次"长按重复"：同一个键，还没收到抬键
        ui.keyDown(key, 0);
        assertEquals(1, ui.repeatsDeduced(), "同一个键没抬又按下 —— 这就是长按重复，必须被认出来");

        // 抬起（真机上由宿主 keyReleased 送进来 —— 那一趟的门闩在 harness 探针，见类注释）
        ui.keyUp(key, 0);
        assertFalse(ui.isKeyHeld(key));
        ui.keyDown(key, 0);
        assertEquals(1, ui.repeatsDeduced(),
                "抬过一次之后再按必须算首按 —— 还是 2 就说明抬起没有出账，松手再按会变成'一直没停过'");
    }

    @Test
    @DisplayName("关界面/失焦结账：补发抬键、清空账本，回来第一下算首按")
    void releaseAllKeysClearsTheLedger() {
        UiTree ui = column();
        ui.pointerDown(control(ui).x() + 1f, control(ui).y() + 1f);

        ui.keyDown(KEY_DOWN, 0);
        ui.keyDown(KEY_DOWN, 0);

        assertEquals(1, ui.releaseAllKeys(), "账上有一个键（同一个键按两次只算一个）");
        assertFalse(ui.isKeyHeld(KEY_DOWN));
        assertEquals(0, ui.releaseAllKeys(), "空账再结一次不该有动作");

        // 到此为止重复计数是 1（第 2 次按下算的）。结账之后那一下如果又算重复，它就会变成 2
        ui.keyDown(KEY_DOWN, 0);
        assertEquals(1, ui.repeatsDeduced(),
                "结账之后的第一下必须是首按 —— 否则'切出去再切回来'一按就是重复");
    }

    /** 真机上那一档画布上的一行控件（几何走适配器的公开读数，不自己数树有几层）。 */
    private static Rect control(UiTree ui) {
        return TrellisColumn.controlBox(ui, 0);
    }

    private UiTree column() {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        Widget[] controls = {new TestWidgets.Inert(), new TestWidgets.Inert()};
        UiTree ui = TrellisColumn.buildColumn(controls, ConfigRows.topInset(U), U, TEST_PALETTE);
        TrellisColumn.layoutColumn(ui, lo.items(), 1f / GUI_SCALE);
        return ui;
    }
}
