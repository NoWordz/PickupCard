package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.TrellisBridge;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.ui.Component;
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
     * 小节头也占一行、只是没有控件 —— 两边的树都必须按这个形态建。
     */
    private static final boolean[] HAS_CONTROL = {
        true, false, true, true, true, true, true, false, true, true, true, true,
    };
    private static final int ROWS = HAS_CONTROL.length;
    /** 真机那一档：1280x720 @ guiScale 3。 */
    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;
    /** 扫描步长（逻辑 px）—— 比一个设备像素还细四倍。 */
    private static final float STEP = 1f / 12f;
    /** 这个宽度以内的不一致算"边缘带"，之外就是真错。 */
    private static final float EDGE_BAND = 2f;

    @Test
    @DisplayName("判据 1：逐点扫整个配置列，Trellis 的命中与宿主的控件矩形只在左右边缘带里不一致")
    void trellisHitsMatchHostControlRects() {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        UiTree ui = TrellisBridge.buildColumn(HAS_CONTROL, ConfigRows.ROWS_TOP_INSET);
        ui.layout(new Rect(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h()),
                1f / GUI_SCALE);

        int agree = 0;
        int edgeOnly = 0;
        List<String> wrong = new ArrayList<>();
        List<String> controlCenters = new ArrayList<>();

        for (float y = lo.items().y(); y < lo.items().bottom(); y += STEP) {
            for (float x = lo.items().x(); x < lo.items().right(); x += STEP) {
                int host = hostControlAt(lo, x, y);
                int trellis = trellisControlAt(ui, x, y);
                if (host == trellis) {
                    agree++;
                    continue;
                }
                if (insideEdgeBand(lo, x, y)) {
                    edgeOnly++;
                } else {
                    wrong.add("(" + x + "," + y + ") 宿主=" + host + " Trellis=" + trellis);
                }
            }
        }

        // 每个控件的正中那一点：两边必须给<b>同一个答案</b>（含"在视口外 → 谁都点不到"）。
        int wholeRowsInViewport = 0;
        for (int i = 0; i < ROWS; i++) {
            if (!HAS_CONTROL[i]) {
                continue;       // 小节头没有控件，中心点无从谈起
            }
            Rect r = hostControl(lo, i);
            float cx = r.x() + r.width() / 2f;
            float cy = r.y() + r.height() / 2f;
            int host = hostControlAt(lo, cx, cy);
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
    private static Rect hostControl(ConfigLayout lo, int line) {
        float y = Math.round(lo.items().y()) + ConfigRows.ROWS_TOP_INSET
                + line * (float) ConfigRows.ROW_STEP;
        return new Rect(ConfigRows.controlX(lo), y, ConfigRows.controlW(lo), ConfigRows.ROW_H);
    }

    private static int hostControlAt(ConfigLayout lo, float x, float y) {
        // 宿主自己也把行控件夹在视口里（屏幕里那句「滚出视口的行不该还能被点到」）。
        // 不镜像这一条，比出来的就不是几何差，而是"一边裁一边不裁"。
        if (y < lo.items().y() || y >= lo.items().bottom()) {
            return -1;
        }
        for (int i = 0; i < ROWS; i++) {
            if (HAS_CONTROL[i] && hostControl(lo, i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Trellis 那一套：{@link UiTree#hitTest} 命中的组件往上走到根的直接子（那一行）；
     * 而且必须落在<b>控件</b>那半边 —— 标签那半边宿主没有控件，两边都算"没命中"。
     */
    private static int trellisControlAt(UiTree ui, float x, float y) {
        Component hit = ui.hitTest(x, y);
        if (hit == null || hit == ui.root()) {
            return -1;
        }
        Component line = hit;
        while (line.parent() != null && line.parent() != ui.root()) {
            line = line.parent();
        }
        if (line.parent() != ui.root()) {
            return -1;
        }
        int row = ui.root().children().indexOf(line);
        if (row < 0 || line.children().size() < 2) {
            return -1;      // 小节头那一行没有控件
        }
        return line.children().get(1).isAncestorOf(hit) ? row : -1;
    }

    /**
     * 这一点是不是落在某个控件<b>左右两条边的窄带</b>里（按 y 找那一行）。
     *
     * <p>宿主说"没命中"、Trellis 说"命中第 i 行"的那些点也在这里 —— 它们同样只是边缘带：
     * 差的那 1.3 逻辑 px 正好是宿主取整吃掉的宽度。
     */
    private static boolean insideEdgeBand(ConfigLayout lo, float x, float y) {
        for (int i = 0; i < ROWS; i++) {
            if (!HAS_CONTROL[i]) {
                continue;
            }
            Rect r = hostControl(lo, i);
            if (y >= r.y() && y < r.bottom()
                    && (Math.abs(x - r.x()) <= EDGE_BAND || Math.abs(x - r.right()) <= EDGE_BAND)) {
                return true;
            }
        }
        return false;
    }
}
