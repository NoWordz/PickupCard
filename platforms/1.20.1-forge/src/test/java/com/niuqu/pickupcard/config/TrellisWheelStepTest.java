package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import dev.e33.trellis.ui.widget.Widget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.ScrollContainer;
import dev.e33.trellis.ui.UiTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 滚轮一格滚多远。
 *
 * <p>【为什么单开这一条】这个数<b>从来没被钉过</b>：旧代码把它算成了
 * {@code rowStep × 3 × 3 = 9 行}（调用方乘一次、{@code ScrollMath} 内部又乘一次），
 * 而注释写着"一格滚三行" —— 两边不一致，谁都没发现，直到 A-16 的评审用算术把它翻出来。
 * 用户拍板按<b>实际行为（9 行）</b>保留已发布手感，这条测试负责让"改步长"变红。
 *
 * <p>【为什么要把视口压小】一格 9 行 ≈ 150 逻辑 px，而真机那一档的 maxOffset 只有 ~25 ——
 * 两种步长都被夹到底、测不出差别（第 33/34/35 轮都看不出手感变化就是这个原因）。
 * 这里把列压到五分之一高，让 maxOffset 比一格大。
 */
class TrellisWheelStepTest {

    /** 与真机那一档同源的行形态（10 个控件行 + 2 个小节头）。 */
    private static final Widget[] CONTROLS = {
        new TestWidgets.Inert(), null, new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(), null,
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(),
    };
    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;
    private static final float U = Tokens.Unit.BASE;
    private static final NvgPalette TEST_PALETTE = NvgPalette.dark(StyleModel.Accents.defaults(), U);

    @Test
    @DisplayName("一格滚轮 = 9 行（已发布的手感；旧代码乘了两次才得到它）")
    void oneNotchIsNineRows() {
        ConfigLayout.Rect items = ConfigLayout.compute(CANVAS_W, CANVAS_H).items();
        // 压到五分之一高（宿主那份类型，layoutColumn 收的就是它）
        ConfigLayout.Rect viewport =
                new ConfigLayout.Rect(items.x(), items.y(), items.w(), items.h() / 5f);
        UiTree ui = TrellisColumn.buildColumn(CONTROLS, ConfigRows.topInset(U), U, TEST_PALETTE);
        TrellisColumn.layoutColumn(ui, viewport, 1f / GUI_SCALE);
        ScrollContainer list = TrellisColumn.scrollList(ui);
        float step = ConfigRows.rowStep(U) * 9f;
        assertTrue(list.maxOffsetY() >= step,
                "视口还不够小：maxOffset=" + list.maxOffsetY() + " 一格=" + step
                        + " —— 这样两种步长都会被夹到底，这条测试就测不出东西了");

        // delta = -1 是"往下滚一格"（MC 的口径）
        assertTrue(ui.scrollAt(viewport.x() + 1f, viewport.y() + 1f, -1d), "这一格该被容器吃掉");

        assertEquals(step, list.offsetY(), 0.01f,
                "一格滚轮不是 9 行 —— 手感冒被改了（旧行为就是 9 行）");
    }
}
