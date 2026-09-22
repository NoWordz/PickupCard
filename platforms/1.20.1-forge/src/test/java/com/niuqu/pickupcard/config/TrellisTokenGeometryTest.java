package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgWidget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.tokens.Units;
import dev.e33.trellis.ui.UiTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-12 的离线钉子：<b>这些几何值到底是多少</b>。
 *
 * <p>【为什么要有这一条】A-12 把宿主的裸数字换成了 token × u。换完之后"值没变"这件事，
 * 当时靠的是真机第 26 轮与第 25 轮的读数逐位相同 —— 那是**人工比对**，而且那两份日志不进仓库
 * （`run_trellis*.log` 在 .gitignore 里）。这条测试把那次比对变成**能自动跑的断言**：
 * 下面那几个数就是真机日志里那一行
 * {@code 树悬停=Rect[x=191.66667, y=72.0, width=65.000015, height=18.0]}。
 *
 * <p>【它挡的是什么】手滑把 {@code design/tokens.css} 里的 {@code --tl-size-control-max-width}
 * 改成 64：框架 314 条 + 宿主 190 条会**全绿**（两侧读同一份 token，改了一起变），
 * 只有人眼能发现。有了这条，改值当场红 —— 那时候要么是改错了，要么是**有意**的改动，
 * 顺手把这里的期望值一起更新（并说明为什么）。
 *
 * <p>【为什么不在这里重算公式】重算等于把宿主那份算术抄第二遍，抄错了测试跟着一起错。
 * 这里要的是"值被钉住"，所以期望值是**字面量**，来源写在每条断言里。
 */
class TrellisTokenGeometryTest {

    /** 真机那一档：427×240 逻辑画布 @ guiScale 3。 */
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

    /**
     * 外观页的行形态：10 个控件行 + 2 个小节头行（下标 1 = Shape、7 = Colors）。
     *
     * <p>控件用 {@link TestWidgets.Inert} 替身 —— 这条测的是几何，真控件会去碰 Forge 配置，
     * 而这条必须在不启动游戏时也能跑。
     */
    private static final NvgWidget[] CONTROLS = {
        new TestWidgets.Inert(), null, new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(), null,
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(),
    };

    @Test
    @DisplayName("宿主那份行模型：18 / 2 / 20 / 2 —— 全部是 token × 基准 u")
    void hostRowMetrics() {
        assertEquals(18, ConfigRows.rowH(U), "行高 = Size.ROW_H(9u) × u(2)");
        assertEquals(2, ConfigRows.rowGap(U), "行缝 = Space.STEP_1(1u) × u(2)");
        assertEquals(20, ConfigRows.rowStep(U), "行距 = 行高 + 行缝（不是第三个独立常数）");
        assertEquals(2, ConfigRows.topInset(U), "列顶内缩 = Space.STEP_1(1u) × u(2)");
    }

    @Test
    @DisplayName("树里第 2 行控件的矩形 == 真机第 25 / 26 轮日志里那个 Rect")
    void controlBoxMatchesTheRealMachine() {
        UiTree ui = column();

        Rect box = TrellisColumn.controlBox(ui, 2);
        assertEquals(191.66667f, box.x(), 0.01f, "控件左缘（真机日志：x=191.66667）");
        assertEquals(72.0f, box.y(), 0.01f, "第 2 行的 y（真机日志：y=72.0）");
        assertEquals(65.000015f, box.width(), 0.01f,
                "控件宽 = clamp(45% × 可用宽, 48, 130)（真机日志：65.000015）");
        assertEquals(18.0f, box.height(), 0.01f, "控件高 = 行高（Size.ROW_H × u）");
    }

    @Test
    @DisplayName("「行距 = 行高 + 行缝」：隔一个小节头的两个控件行，y 差正好 == 2 × 行距")
    void rowPitchComesFromHeightPlusGap() {
        UiTree ui = column();

        Rect first = TrellisColumn.controlBox(ui, 0);
        Rect third = TrellisColumn.controlBox(ui, 2);
        assertEquals(2f * ConfigRows.rowStep(U), third.y() - first.y(), 0.01f,
                "第 0 行到第 2 行隔了一个小节头 = 两个行距；行缝跑掉的话这里会先红");
    }

    /**
     * A-14 的自适应钉子：<b>u 跟着画布高走，行高/行距/内缩都跟着缩</b>。
     *
     * <p>【为什么必须有这一条】A-14 之前宿主那三个数是 {@code static final}（烤死基准 u），
     * 换一档 guiScale 它们纹丝不动 —— 而且**没有任何东西会报**。这条把"u 变了几何必须变"
     * 钉住：拿真机那档 427×240 算，u 落到 1.5（{@code Units.u}），行高 18→14、行距 20→16。
     */
    @Test
    @DisplayName("A-14：画布 240 高 → u=1.5，行高 14 / 行缝 2 / 行距 16 / 内缩 2")
    void adaptiveGeometryFollowsCanvasHeight() {
        float u = Units.u(CANVAS_H);
        assertEquals(1.5f, u, 1e-6f, "240 在地板线(400×0.8=320)之下 → fit=0.6 → 被 MIN 抬到 1.5");

        assertEquals(14, ConfigRows.rowH(u), "round(9u × 1.5) = round(13.5) = 14");
        assertEquals(2, ConfigRows.rowGap(u), "round(1u × 1.5) = round(1.5) = 2");
        assertEquals(16, ConfigRows.rowStep(u), "行距 = 14 + 2");
        assertEquals(2, ConfigRows.topInset(u), "列顶内缩 = round(1.5) = 2");

        UiTree ui = TrellisColumn.buildColumn(CONTROLS, ConfigRows.topInset(u), u);
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        ui.layout(new Rect(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h()),
                1f / GUI_SCALE);

        Rect box = TrellisColumn.controlBox(ui, 2);
        assertEquals(14.0f, box.height(), 1e-3f, "树里的控件高必须跟着 u 缩到 14");
        // 第 2 行 = 第 3 行 → 相对列顶偏移 2 × 行距 = 32（列顶内缩另加）
        assertEquals(2f * ConfigRows.rowStep(u),
                box.y() - (Math.round(lo.items().y()) + ConfigRows.topInset(u)),
                0.6f, "第 2 行的 y 相对列顶 ≈ 2 × 行距");
    }

    /** 按真机那一档建一棵树（12 行的外观页形态，滚动为 0），布局到 views 的矩形上。 */
    private static UiTree column() {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        UiTree ui = TrellisColumn.buildColumn(CONTROLS, ConfigRows.topInset(U), U);
        ui.layout(new Rect(lo.items().x(), lo.items().y(), lo.items().w(), lo.items().h()),
                1f / GUI_SCALE);
        return ui;
    }
}
