package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import dev.e33.trellis.ui.widget.Widget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.tokens.Units;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.ui.UiTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 判据 1 的对账：<b>Trellis 的命中</b>和<b>宿主自己的控件矩形</b>在真机画布上是不是同一件事。
 *
 * <p>【为什么能离线做】两边都是纯函数：宿主那边是 {@link ConfigRows#controlX}/{@link ConfigRows#controlW}
 * 加行距，Trellis 那边是 {@link UiTree#hitTest}（读的就是组件的 {@code bounds()}）。
 * 所以可以把整个配置列<b>逐点扫一遍</b>，比截图看得清楚得多 —— 截图看不出"差 1.3 逻辑 px"，
 * 扫描能量出那 1.3 px 落在哪几条边上。
 *
 * <p>已知结论（A-4 闭式算过、A-5 之后仍然如此）：竖直完全一致；水平差约 1.3 逻辑 px，
 * 来源是宿主把 {@code round(items.right)} 与截断过的 {@code controlW} 当整数用，
 * 而 Trellis 保留浮点、只按设备网格对齐。这个测试把那份差<b>钉在它该在的位置上</b>：
 * 只许出现在控件左右两条边的窄带里，控件内部一格都不许错。
 */
class TrellisHitParityTest {

    /**
     * 外观页的行形态：10 个控件行 + 2 个小节头行（下标 1 = Shape、7 = Colors）。
     * 小节头也占一行、只是没有控件 —— 两边的树都必须按这个形态建（{@code null} = 小节头）。
     *
     * <p>控件用 {@link TestWidgets.Inert} 替身：这一条测的是<b>几何</b>，
     * 真控件会去碰 Forge 配置，而这条必须在没启动游戏时也能跑。
     */
    private static final Widget[] CONTROLS = {
        new TestWidgets.Inert(), null, new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(), null,
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(),
    };
    private static final int ROWS = CONTROLS.length;
    /** 这一行有没有控件（小节头没有）。 */
    private static boolean hasControl(int row) {
        return CONTROLS[row] != null;
    }
    /** 真机那一档：1280x720 @ guiScale 3。 */
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

    /** 测试用的调色板（A-15 起 buildColumn 要它给焦点环的颜色）。 */
    private static final NvgPalette TEST_PALETTE =
            NvgPalette.dark(com.niuqu.pickupcard.style.StyleModel.Accents.defaults(), U);

    /** 扫描步长（逻辑 px）—— 比一个设备像素还细四倍。 */
    private static final float STEP = 1f / 12f;
    /** 这个宽度以内的不一致算"边缘带"，之外就是真错。 */
    private static final float EDGE_BAND = 2f;

    @Test
    @DisplayName("判据 1（基准 u）：逐点扫整个配置列，Trellis 的命中与宿主的控件矩形只在左右边缘带里不一致")
    void trellisHitsMatchHostControlRects() {
        scan(U);
    }

    /**
     * <b>A-14：同一个对账要在 u ≠ BASE 时也成立。</b>
     *
     * <p>【为什么必须补这一条】A-14 起宿主的 {@code controlW / controlX / labelX} 也乘了 u
     * （见验算侧），而这条对账是"Trellis 树里的命中"与"宿主自己那份算术"**唯一**的水平比对。
     * 只在基准那一档扫过，等于"u 缩放这条路上有没有新的边缘带错位"没人看着。
     * 拿真机 427×240 那一档（u=1.5）重扫一遍，判据与基准档完全相同（边缘带以内算边缘）。
     */
    @Test
    @DisplayName("判据 1（u=1.5）：u 缩放之后，同一份逐点扫描仍然只在边缘带里不一致")
    void trellisHitsMatchHostControlRectsAtAdaptiveUnit() {
        float u = Units.u(CANVAS_H);
        assertEquals(1.5f, u, 0.001f, "这一档就是要验「非基准 u」这条路，u 得真的是 1.5");
        scan(u);
    }

    /** 逐点扫一遍：Trellis 命中 vs 宿主控件矩形，只许在左右边缘带里不一致。 */
    private static void scan(float u) {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        UiTree ui = TrellisColumn.buildColumn(CONTROLS, ConfigRows.topInset(u), u, TEST_PALETTE);
        ui.layout(new Rect(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h()),
                1f / GUI_SCALE);

        int agree = 0;
        int edgeOnly = 0;
        List<String> wrong = new ArrayList<>();
        List<String> controlCenters = new ArrayList<>();

        for (float y = lo.items().y(); y < lo.items().bottom(); y += STEP) {
            for (float x = lo.items().x(); x < lo.items().right(); x += STEP) {
                int host = hostControlAt(lo, x, y, u);
                int trellis = trellisControlAt(ui, x, y);
                if (host == trellis) {
                    agree++;
                    continue;
                }
                if (insideEdgeBand(lo, x, y, u)) {
                    edgeOnly++;
                } else {
                    wrong.add("(" + x + "," + y + ") 宿主=" + host + " Trellis=" + trellis);
                }
            }
        }

        // 每个控件的正中那一点：两边必须给<b>同一个答案</b>（含"在视口外 → 谁都点不到"）。
        int wholeRowsInViewport = 0;
        for (int i = 0; i < ROWS; i++) {
            if (!hasControl(i)) {
                continue;       // 小节头没有控件，中心点无从谈起
            }
            Rect r = hostControl(lo, i, u);
            float cx = r.x() + r.width() / 2f;
            float cy = r.y() + r.height() / 2f;
            int host = hostControlAt(lo, cx, cy, u);
            int trellis = trellisControlAt(ui, cx, cy);
            controlCenters.add(i + "->" + host);
            assertEquals(host, trellis, "第 " + i + " 行控件中心：两边答案不一致");
            if (r.bottom() <= lo.items().bottom()) {
                assertEquals(i, trellis, "整行都在视口里的第 " + i + " 行，中心必须命中它自己");
                wholeRowsInViewport++;
            }
        }
        assertTrue(wholeRowsInViewport >= 6, "视口里该有好几整行，实际 " + wholeRowsInViewport);

        assertTrue(wrong.isEmpty(), "边缘带之外还有 " + wrong.size() + " 个点不一致，前几个：" + wrong);

        System.out.printf("扫描 %d 点：一致 %d、只在边缘带不同 %d、真错 %d%n",
                agree + edgeOnly + wrong.size(), agree, edgeOnly, wrong.size());
    }

    // -----------------------------------------------------------------------

    /** 宿主那一套：控件右对齐到列右缘留 6，宽按比例夹上下限（整数运算）。 */
    private static Rect hostControl(ConfigLayout lo, int line, float u) {
        float y = Math.round(lo.items().y()) + ConfigRows.topInset(u)
                + line * (float) ConfigRows.rowStep(u);
        return new Rect(ConfigRows.controlX(lo, u), y, ConfigRows.controlW(lo, u), ConfigRows.rowH(u));
    }

    private static int hostControlAt(ConfigLayout lo, float x, float y, float u) {
        // 宿主自己也把行控件夹在视口里（屏幕里那句「滚出视口的行不该还能被点到」）。
        // 不镜像这一条，比出来的就不是几何差，而是"一边裁一边不裁"。
        if (y < lo.items().y() || y >= lo.items().bottom()) {
            return -1;
        }
        for (int i = 0; i < ROWS; i++) {
            if (hasControl(i) && hostControl(lo, i, u).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Trellis 那一套：直接问生产代码那条 {@link TrellisColumn#controlRowAt} ——
     * 宿主现在也用它（悬停缓动、底部说明、点击路由），所以对账比的就是真在跑的那一条，
     * 不是测试里另抄一遍。
     */
    private static int trellisControlAt(UiTree ui, float x, float y) {
        return TrellisColumn.controlRowAt(ui, x, y);
    }

    /**
     * 这一点是不是落在某个控件<b>左右两条边的窄带</b>里（按 y 找那一行）。
     *
     * <p>宿主说"没命中"、Trellis 说"命中第 i 行"的那些点也在这里 —— 它们同样只是边缘带：
     * 差的那 1.3 逻辑 px 正好是宿主取整吃掉的宽度。
     */
    private static boolean insideEdgeBand(ConfigLayout lo, float x, float y, float u) {
        for (int i = 0; i < ROWS; i++) {
            if (!hasControl(i)) {
                continue;
            }
            Rect r = hostControl(lo, i, u);
            if (y >= r.y() && y < r.bottom()
                    && (Math.abs(x - r.x()) <= EDGE_BAND || Math.abs(x - r.right()) <= EDGE_BAND)) {
                return true;
            }
        }
        return false;
    }
}
