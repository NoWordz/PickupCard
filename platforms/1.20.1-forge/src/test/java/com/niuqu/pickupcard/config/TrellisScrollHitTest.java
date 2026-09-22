package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.UiTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-16：滚动之后，<b>命中与可见带必须还是同一份几何</b>。
 *
 * <p>【改动之前这里是双向都错的，全是实测】宿主把整棵树按 {@code scrollOffset} 上移，
 * 于是 {@code hitTest} 的"祖先矩形必须包含该点"用的是<b>上移过的</b>矩形（见 A-16 那张表）：
 * <ul>
 *   <li>底部 {@code scrollOffset} px 里的可见行<b>点不到</b> —— 上移 30 时 2 行、60 时 3 行；</li>
 *   <li>视口上方 {@code scrollOffset} px 里的点<b>能点到已经滚出去的行</b> —— 30 时 18 个点、60 时 36 个。</li>
 * </ul>
 * 这一片把两头都钉住。它同时是"容器自己的矩形 = 视口"这条设计的验收：命中那条现成的
 * 规则（{@code UiTree#hitTest} 要求祖先矩形包含该点）因此免费给出正确结果。
 */
class TrellisScrollHitTest {

    /** 外观页那一档的行形态（10 个控件行 + 2 个小节头），与真机那份同源。 */
    private static final NvgWidget[] CONTROLS = {
        new TestWidgets.Inert(), null, new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(), null,
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(),
    };
    /** 真机那一档：1280x720 @ guiScale 3。 */
    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;
    private static final float U = Tokens.Unit.BASE;
    private static final NvgPalette TEST_PALETTE = NvgPalette.dark(StyleModel.Accents.defaults(), U);

    /** 三个真的会滚起来的偏移（都比一屏矮不了多少，所以底部确实有可见行）。 */
    private static final float[] SCROLLS = {10f, 30f, 60f};

    private static UiTree column(float scrollOffset) {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        UiTree ui = TrellisColumn.buildColumn(CONTROLS, ConfigRows.topInset(U), U, TEST_PALETTE);
        // 先布局一趟让容器量出内容高（夹取要用），再设偏移，再布局 —— 宿主同一条顺序
        TrellisColumn.layoutColumn(ui, lo.items(), 1f / GUI_SCALE);
        TrellisColumn.scrollList(ui).scrollTo(0f, scrollOffset);
        TrellisColumn.layoutColumn(ui, lo.items(), 1f / GUI_SCALE);
        return ui;
    }

    @Test
    @DisplayName("滚动之后，落在可见带里的行仍然点得到（从前底部 scrollOffset px 是死区）")
    void visibleRowsStayHittableAfterScrolling() {
        for (float scroll : SCROLLS) {
            UiTree ui = column(scroll);
            ConfigLayout.Rect items = ConfigLayout.compute(CANVAS_W, CANVAS_H).items();
            int checked = 0;
            for (int row = 0; row < CONTROLS.length; row++) {
                if (CONTROLS[row] == null) {
                    continue;
                }
                Rect box = TrellisColumn.controlBox(ui, row);
                float cy = box.y() + box.height() / 2f;
                if (cy < items.y() || cy >= items.bottom()) {
                    continue;   // 中心不在这条可见带里，不归这条测试管
                }
                checked++;
                assertEquals(row, TrellisColumn.controlRowAt(ui, box.x() + 1f, cy),
                        "滚动 " + scroll + " 之后，第 " + row + " 行在可见带里却点不到");
            }
            assertTrue(checked >= 5,
                    "只检查到 " + checked + " 行 —— 样本太少，这条测试没在验什么");
        }
    }

    @Test
    @DisplayName("视口上方的点不再命中任何行（从前能点到已经滚出去的行）")
    void pointsAboveTheViewportHitNothing() {
        for (float scroll : SCROLLS) {
            UiTree ui = column(scroll);
            ConfigLayout.Rect items = ConfigLayout.compute(CANVAS_W, CANVAS_H).items();
            int falseHits = 0;
            for (float y = items.y() - scroll; y < items.y(); y += 1f) {
                for (int row = 0; row < CONTROLS.length; row++) {
                    if (CONTROLS[row] == null) {
                        continue;
                    }
                    Rect box = TrellisColumn.controlBox(ui, row);
                    if (TrellisColumn.controlRowAt(ui, box.x() + 1f, y) >= 0) {
                        falseHits++;
                        break;
                    }
                }
            }
            assertEquals(0, falseHits,
                    "滚动 " + scroll + " 之后，视口上方（y < " + items.y() + "）有 " + falseHits
                            + " 个点命中了行 —— 那些行根本看不见");
        }
    }

    @Test
    @DisplayName("滚出视口的行：它在树里、有矩形，但不在容器盒子里，所以命中不到")
    void rowsScrolledOutStayInTheTreeButOutOfReach() {
        UiTree ui = column(60f);
        ConfigLayout.Rect items = ConfigLayout.compute(CANVAS_W, CANVAS_H).items();
        // 第 8 行在滚动 60 之后仍在可见带里（这一条盯着"没被顺手删掉"）
        Rect visible = TrellisColumn.controlBox(ui, 8);
        assertTrue(visible.y() + visible.height() > items.y(),
                "第 8 行跑到视口上方去了，这条测试的前提不成立了");

        // 而它上面那些行确实已经离开可见带 —— 中心在带外的行，命中必须是 -1
        for (int row = 2; row <= 6; row++) {
            Rect box = TrellisColumn.controlBox(ui, row);
            float cy = box.y() + box.height() / 2f;
            if (cy >= items.y()) {
                continue;
            }
            assertEquals(-1, TrellisColumn.controlRowAt(ui, box.x() + 1f, cy),
                    "第 " + row + " 行已经滚出视口，却还能被点到");
        }
    }
}
