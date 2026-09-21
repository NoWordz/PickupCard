package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisBridge;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.ui.UiTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-10 的离线对账：<b>行内控件的命中由树判</b>，控件只收结果。
 *
 * <p>【这一条在钉什么】从前宿主自己拿 {@code widget.hit(...)} 判命中，而悬停底、标签、
 * 说明读的是 Trellis 那份 {@code bounds()} —— 两份几何。现在"指着哪一行、点到了哪一行"
 * 只有一个出处（{@link TrellisBridge#controlRowAt}），控件收到的按下/松开/悬停都由树转发。
 *
 * <p>【为什么不跑真机也能验】树、桥、控件替身三样都不碰游戏：布局是纯函数，
 * 事件是直接调 {@code pointerDown/pointerUp}。真机那一步验的是"真指针进来之后
 * 值有没有变"（harness 的 {@code clickOption} / {@code dragOption} 日志），
 * 这里验的是<b>路由对不对</b>（谁收到、收到什么、什么情况下收不到）。
 */
class TrellisControlInputTest {

    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;

    /** 三行：控件 / 小节头 / 控件 —— 小节头那一行没有任何控件可以收到事件。 */
    private final TestWidgets.Recorder first = new TestWidgets.Recorder();
    private final TestWidgets.Recorder third = new TestWidgets.Recorder();

    @Test
    @DisplayName("点在控件格子里：按下 → 松开，控件收到一次按下，松手时激活一次")
    void pressAndReleaseOnTheControlReachTheWidget() {
        UiTree ui = column();
        Rect box = TrellisBridge.labelBox(ui, 0);
        float x = control(ui, 0).x() + 1f;
        float y = control(ui, 0).y() + 1f;

        // 先确认这一点确实落在控件那一半（不在标签盒里）
        assertTrue(x > box.right(), "取点跑进标签那一半了，这条就不是在验控件");

        assertTrue(ui.pointerDown(x, y), "树没接住这次按下");
        assertEquals(1, first.presses, "控件没收到按下");
        assertTrue(first.held(), "按下之后控件该是'正被按着'");
        assertEquals(0, third.presses, "按下不该发给别的行");

        ui.pointerUp(x, y);
        assertEquals(1, first.releases);
        assertTrue(first.lastActivate, "同一格里松手才算一次点击");
        assertEquals(1, first.activates, "点击没激活");
        assertFalse(first.held(), "松手之后按下状态该收回去");
    }

    @Test
    @DisplayName("拖到格子外面松手：控件也收到松开（指针捕获），但不算点击")
    void releaseOutsideStillReachesTheWidgetWithoutActivating() {
        UiTree ui = column();
        Rect box = control(ui, 0);
        float x = box.x() + 1f;
        float y = box.y() + 1f;

        ui.pointerDown(x, y);
        ui.pointerUp(400f, 230f);       // 视口另一角

        assertEquals(1, first.releases,
                "抬起没发给按下的那一个 —— 按下状态会永久留在控件上（松了手还亮着）");
        assertFalse(first.lastActivate, "落点在外面就不算点击");
        assertEquals(0, first.activates);
        assertFalse(first.held());
    }

    @Test
    @DisplayName("点在标签那一半：控件什么都不该收到（那一格不是点击目标）")
    void pressOnTheLabelDoesNotReachTheWidget() {
        UiTree ui = column();
        Rect box = TrellisBridge.labelBox(ui, 0);

        ui.pointerDown(box.x() + 1f, box.y() + 1f);
        assertEquals(0, first.presses, "标签那一半不该把按下转给控件");
        assertEquals(-1, TrellisBridge.controlRowAt(ui, box.x() + 1f, box.y() + 1f),
                "标签那一半不该被认成'指着控件'");
    }

    @Test
    @DisplayName("小节头那一行没有控件：指着它既没有行号，也没有东西收到事件")
    void headerRowHasNoControl() {
        UiTree ui = column();
        Rect box = TrellisBridge.labelBox(ui, 1);

        assertEquals(-1, TrellisBridge.controlRowAt(ui, box.x() + 1f, box.y() + 1f));
        ui.pointerDown(box.x() + 1f, box.y() + 1f);
        assertEquals(0, first.presses + third.presses, "小节头不该把事件转给任何控件");
    }

    @Test
    @DisplayName("悬停由树推给控件：指着控件才悬停，指到标签就松开")
    void hoverFollowsTheTree() {
        UiTree ui = column();
        Rect box = control(ui, 2);
        float cx = box.x() + 1f;
        float cy = box.y() + 1f;

        ui.pointerMove(cx, cy);
        TrellisBridge.syncHover(ui);
        assertTrue(third.lastHovered, "指着控件，控件该是悬停的");
        assertFalse(first.lastHovered, "指着的是第三行，第一行不该悬停");

        Rect label = TrellisBridge.labelBox(ui, 2);
        ui.pointerMove(label.x() + 1f, label.y() + 1f);
        TrellisBridge.syncHover(ui);
        assertFalse(third.lastHovered, "指针移到标签那一半，控件就该松开悬停");
    }

    @Test
    @DisplayName("行号由树给：控件行给行号，超出视口/空白处给 -1")
    void controlRowAtAnswersWithTheTreeGeometry() {
        UiTree ui = column();
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        Rect box = control(ui, 2);

        assertEquals(2, TrellisBridge.controlRowAt(ui, box.x() + 1f, box.y() + 1f));
        assertEquals(-1, TrellisBridge.controlRowAt(ui, lo.items().x() - 5f, box.y() + 1f),
                "配置列左边外面不该有行");
        assertEquals(-1, TrellisBridge.controlRowAt(ui, box.x() + 1f, lo.items().bottom() + 5f),
                "视口下面不该有行");
        assertEquals(0, TrellisBridge.controlRowAt(ui, control(ui, 0).x() + 1f,
                control(ui, 0).y() + 1f));
    }

    // -----------------------------------------------------------------------

    private static Rect control(UiTree ui, int row) {
        return ui.root().children().get(row).children().get(1).bounds();
    }

    private UiTree column() {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        NvgWidget[] controls = {first, null, third};
        UiTree ui = TrellisBridge.buildColumn(controls, ConfigRows.ROWS_TOP_INSET);
        TrellisBridge.layoutColumn(ui, lo.items(), 0f, 1f / GUI_SCALE);
        return ui;
    }
}
