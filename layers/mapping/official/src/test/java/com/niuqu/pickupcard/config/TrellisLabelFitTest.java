package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.McFont;
import dev.e33.trellis.ui.widget.Widget;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn.LabelFit;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.text.FontMetrics;
import dev.e33.trellis.text.FontStack;
import dev.e33.trellis.text.TextMeasurer;
import dev.e33.trellis.ui.UiTree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 标签列搬到 Trellis 之后的离线对账：<b>文本框来自树、适配来自度量层</b>。
 *
 * <p>【为什么能离线做】这条路径上没有一个数需要游戏：{@code ConfigLayout} 是纯函数，
 * 树是纯布局，适配是 {@link TextMeasurer}。字体用一份<b>照 MC 形状捏的</b>假度量
 * （advance 1 em、ascent 7/9、descent 2/9）—— 这样断言全是闭式的，
 * 而"垂直居中"那条能真的验到 {@code TextAlign} 用的是行框。
 *
 * <p>【为什么必须有这条对账】上一轮（A-4）已经证明"看着差不多"会漏掉 1.3 逻辑 px；
 * 标签这件事上同样的差会变成"文字压在控件上"。盒子是谁的、宽多少、缩了多少 —— 都要有数。
 */
class TrellisLabelFitTest {

    /** 外观页那一档的行形态（10 个控件行 + 2 个小节头）—— 与真机那份同源，{@code null} = 小节头。 */
    private static final Widget[] CONTROLS = {
        new TestWidgets.Inert(), null, new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(), null,
        new TestWidgets.Inert(), new TestWidgets.Inert(), new TestWidgets.Inert(),
        new TestWidgets.Inert(),
    };
    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    /** 真机那一档：1280x720 @ guiScale 3。 */
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

    /** 标签缩字地板，与屏幕里那个常量同一个值（这里是"接口的一侧"，不是抄数）。 */
    private static final float MIN_FONT = 8f;
    /** 截断时接在末尾的串，与屏幕里那个常量同一个值。 */
    private static final String ELLIPSIS = "...";

    /**
     * 照 MC 形状捏的假字体：每码点 1 em 宽、行框 = ascent(7/9) + descent(2/9) = 1 em。
     * <p>宽度与字号成正比这一条与真字体一致，所以 {@code shrinkToFit} 那条闭式在这里成立。
     */
    private static final class McShapedFont implements FontMetrics {
        @Override
        public float advance(int codePoint) {
            return 1f;
        }

        @Override
        public float ascent() {
            return McFont.ASCENT_PX / McFont.EM;
        }

        @Override
        public float descent() {
            return McFont.DESCENT_PX / McFont.EM;
        }

        @Override
        public float lineGap() {
            return 0f;
        }

        @Override
        public boolean drawable() {
            return true;
        }
    }

    private static final TextMeasurer METRICS = new TextMeasurer(FontStack.of(new McShapedFont()));

    // -----------------------------------------------------------------------
    // 文本框：它必须就是树里那个叶子的矩形
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("标签盒就是树里那个标签叶子的 Rect 对象本身（判据 1 的文字版）")
    void labelBoxIsTheLeafsOwnRect() {
        UiTree ui = column(0f);
        for (int i = 0; i < CONTROLS.length; i++) {
            Rect leaf = row(ui, i).children().get(0).bounds();
            assertSame(leaf, TrellisColumn.labelBox(ui, i),
                    "第 " + i + " 行的标签盒不是叶子自己的那个对象 —— 那就又成了两份几何");
        }
    }

    @Test
    @DisplayName("标签盒贴着列内容左缘，与控件之间正好一个 GAP")
    void labelBoxSitsBetweenColumnPaddingAndControl() {
        UiTree ui = column(0f);
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        Rect box = TrellisColumn.labelBox(ui, 0);
        Rect control = row(ui, 0).children().get(1).bounds();

        // 6 = TrellisColumn 的 PAD/GAP。差一个设备像素以内是布局对齐的正常结果。
        assertEquals(6f, box.x() - ui.root().bounds().x(), 1f / GUI_SCALE + 1e-3f,
                "标签盒没有从列内容左缘开始");
        assertEquals(6f, control.x() - box.right(), 1f / GUI_SCALE + 1e-3f,
                "标签盒与控件之间不够一个 GAP（文字会贴到控件上）");
        assertEquals(ConfigRows.rowH(U), box.height(), 0.5f, "标签盒高度不是行高");
        assertTrue(box.right() <= control.x(), "标签盒压到控件上");
        assertEquals(lo.items().x() + 6f, box.x(), 1f, "标签盒没落在配置列里");
    }

    @Test
    @DisplayName("小节头那一行的标签盒更宽 —— 它没有控件，可用宽到列右缘")
    void headerRowGetsTheWholeWidth() {
        UiTree ui = column(0f);
        Rect header = TrellisColumn.labelBox(ui, 1);
        assertEquals(1, row(ui, 1).children().size(),
                "小节头不该有控件叶子");
        assertTrue(header.width() > TrellisColumn.labelBox(ui, 0).width() + 50f,
                "小节头的标签盒没有宽到列右缘：" + header.width());
    }

    @Test
    @DisplayName("滚动偏移进了树：标签盒的行顶与宿主那一份对得上（不传就是错开 N px）")
    void scrollOffsetMovesTheRows() {
        float scroll = 13f;
        Rect still = TrellisColumn.labelBox(column(0f), 3);
        Rect moved = TrellisColumn.labelBox(column(scroll), 3);

        assertEquals(scroll, still.y() - moved.y(), 0.5f, "滚动没有把行整体上移同样的距离");

        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        float hostY = Math.round(lo.items().y()) + ConfigRows.topInset(U)
                + 3 * (float) ConfigRows.rowStep(U) - Math.round(scroll);
        assertEquals(hostY, moved.y(), 1f,
                "滚动之后 Trellis 的行与宿主那一份错开了（悬停底、命中、标签会一起错）");
    }

    // -----------------------------------------------------------------------
    // 适配：缩的是字号、有地板，到地板还装不下就截断
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("装得下：原字号，量宽就是自然宽")
    void fitsAtFullSize() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        LabelFit fit = TrellisColumn.fitLabel(METRICS, "abcd", box, McFont.EM, MIN_FONT, ELLIPSIS);

        assertEquals(LabelFit.Mode.ORIGINAL, fit.mode(), "36px 的字在 73px 的盒子里不该缩");
        assertEquals(1f, fit.scale(), 1e-4f);
        assertEquals(36f, fit.width(), 1e-3f);
        assertEquals("abcd", fit.text(), "原字号那一档不该改串");
        assertEquals(box.x(), fit.x(), 1e-4f, "行框左端不是盒子的左端");
    }

    @Test
    @DisplayName("装不下：缩的是字号，量出来的宽正好落在盒宽上（不是把画出来的拉伸）")
    void shrinksToFitExactly() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        // 9 个字符 = 81px > 盒宽（约 73.3），需要缩到 0.905 左右
        LabelFit fit = TrellisColumn.fitLabel(METRICS, "abcdefghi", box, McFont.EM, MIN_FONT,
                ELLIPSIS);

        assertEquals(LabelFit.Mode.SHRUNK, fit.mode(), "81px 的字在 73px 的盒子里必须缩");
        assertEquals(box.width(), fit.width(), 1e-3f, "缩完的宽没有正好落在盒宽上");
        assertEquals(box.width() / 81f, fit.scale(), 1e-4f, "缩放倍数不是 maxW / 自然宽");
        assertTrue(fit.scale() * McFont.EM >= MIN_FONT, "缩过了地板");
        assertEquals("abcdefghi", fit.text(), "缩字那一档不该改串");
    }

    @Test
    @DisplayName("到地板还装不下就截断：串带省略号，量宽回到盒内")
    void ellipsizesAfterTheFloor() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        // 16 个字符 = 144px，线性缩到装下需要 0.51 < 地板 8/9
        LabelFit fit = TrellisColumn.fitLabel(METRICS, "abcdefghijklmnop", box, McFont.EM, MIN_FONT,
                ELLIPSIS);

        assertEquals(LabelFit.Mode.ELLIPSIZED, fit.mode(), "到地板还装不下就该截断");
        assertEquals(MIN_FONT / McFont.EM, fit.scale(), 1e-4f, "截断那一档不再是基准字号");
        // 8px 下每字 8px，"..." 占 24px：73.3 - 24 = 49.3 → 留住 6 个字
        assertEquals("abcdef" + ELLIPSIS, fit.text(), "截出来的串不是按簇边界切的");
        assertTrue(fit.width() <= box.width(), "截断之后还是越界：" + fit.width());
        assertTrue(fit.width() > 0f);
    }

    @Test
    @DisplayName("盒子窄到连省略号都放不下：截断那一档也要把越界报出来")
    void reportsOverflowWhenEvenTheEllipsisDoesNotFit() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        Rect tiny = new Rect(box.x(), box.y(), 20f, box.height());
        LabelFit fit = TrellisColumn.fitLabel(METRICS, "abcdefghijklmnop", tiny, McFont.EM,
                MIN_FONT, ELLIPSIS);

        assertEquals(LabelFit.Mode.ELLIPSIZED, fit.mode());
        assertEquals(ELLIPSIS, fit.text(), "连一个簇都放不下时至少要说『这里有内容』");
        assertTrue(fit.overflow(), "溢出必须报得出来（宿主据此才知道该改文案还是改布局）");
        assertTrue(fit.width() > tiny.width());
    }

    @Test
    @DisplayName("差千分之一 px 不算装不下：不许把整串字平白截掉（浮点余量）")
    void slackKeepsTheTextIntact() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        // 16 个字符在 8px 下量到 128px。把盒子做窄 0.001px —— 这不是"装不下"。
        // 第 14 轮真机就是这个坑：`Entrance style` 缩到 0.978 正好贴合，却被打成"截"。
        Rect justNarrow = new Rect(box.x(), box.y(), 128f - 0.001f, box.height());
        LabelFit intact = TrellisColumn.fitLabel(METRICS, "abcdefghijklmnop", justNarrow,
                McFont.EM, MIN_FONT, ELLIPSIS);
        assertEquals(LabelFit.Mode.SHRUNK, intact.mode(), "差千分之一像素被当成了装不下");
        assertEquals("abcdefghijklmnop", intact.text(), "整串字被平白截掉了");
        assertFalse(intact.overflow(), "千分之一的越界不该报成溢出");

        // 反过来，差半个 px 就是真的装不下 —— 那一档才该截
        Rect tooNarrow = new Rect(box.x(), box.y(), 127.5f, box.height());
        LabelFit cut = TrellisColumn.fitLabel(METRICS, "abcdefghijklmnop", tooNarrow,
                McFont.EM, MIN_FONT, ELLIPSIS);
        assertEquals(LabelFit.Mode.ELLIPSIZED, cut.mode(), "半个像素的越界没被当成装不下");
        assertTrue(cut.width() <= tooNarrow.width(), "截完还是越界：" + cut.width());
        assertFalse(cut.overflow());
    }

    @Test
    @DisplayName("文字行框在标签盒里垂直居中（基线是 TextAlign 算的，不是 y - h / 2）")
    void lineBoxIsVerticallyCentered() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        for (String text : new String[] {"abcd", "abcdefghi", "abcdefghijklmnop"}) {
            LabelFit fit = TrellisColumn.fitLabel(METRICS, text, box, McFont.EM, MIN_FONT,
                    ELLIPSIS);
            // 宿主画出来的行框 = [top, top + (ascent + descent) * scale]
            float center = fit.top()
                    + (McFont.ASCENT_PX + McFont.DESCENT_PX) * fit.scale() / 2f;
            assertEquals(box.y() + box.height() / 2f, center, 1e-3f,
                    "『" + text + "』的行框中心不在盒中心");
        }
    }

    @Test
    @DisplayName("Trellis 量的宽 == MC 量出来的宽 × 缩放（A-7 那条对账，三档都要成立）")
    void measuredWidthIsTheDrawnWidth() {
        Rect box = TrellisColumn.labelBox(column(0f), 0);
        for (String text : new String[] {"abcd", "abcdefghi", "abcdefghijklmnop"}) {
            LabelFit fit = TrellisColumn.fitLabel(METRICS, text, box, McFont.EM, MIN_FONT,
                    ELLIPSIS);
            // 比的是 <b>fit.text()</b>：截断那一档画出来的串和这一行的标签不是同一个
            assertEquals(METRICS.width(fit.text(), McFont.EM) * fit.scale(), fit.width(), 1e-3f,
                    "『" + text + "』量出来的宽和宿主按倍数画出来的宽对不上");
        }
    }

    // -----------------------------------------------------------------------

    /**
     * 第 {@code i} 行那一格。
     *
     * <p>【为什么测试也要知道树有几层】这一片里有两件事绕不开树的结构："标签盒就是树里那个
     * 对象本身"（判据 1 的文字版）和"小节头没有控件叶子"。A-16 起树的根是滚动容器，
     * 行在它的内容子节点下面 —— 取行只在这一个地方写，别处再写一遍就会踩到容器那一层
     * （症状是 {@code IndexOutOfBounds} 或者更坏的"行号整体错位一格"）。
     */
    private static dev.e33.trellis.ui.Component row(UiTree ui, int i) {
        return ui.root().children().get(0).children().get(i);
    }

    private static UiTree column(float scrollOffset) {
        ConfigLayout lo = ConfigLayout.compute(CANVAS_W, CANVAS_H);
        UiTree ui = TrellisColumn.buildColumn(CONTROLS, ConfigRows.topInset(U), U, TEST_PALETTE);
        // 【A-16 起偏移住在滚动容器里，而且要先布局一趟才设得进去】夹取靠"内容多高"这个
        // 布局量出来的数，没布局过它就是 0，设什么都会被夹成 0。宿主那边同一条顺序
        // （{@code updateTrellisColumn}：先建树布局，再把接回来的偏移写进容器）。
        TrellisColumn.layoutColumn(ui, lo.items(), 1f / GUI_SCALE);
        TrellisColumn.scrollList(ui).scrollTo(0f, scrollOffset);
        TrellisColumn.layoutColumn(ui, lo.items(), 1f / GUI_SCALE);
        return ui;
    }
}
