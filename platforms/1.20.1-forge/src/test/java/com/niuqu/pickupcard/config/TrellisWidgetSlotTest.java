package com.niuqu.pickupcard.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.niuqu.pickupcard.render.nvg.ui.ConfigLayout;
import com.niuqu.pickupcard.render.nvg.ui.NvgPalette;
import com.niuqu.pickupcard.render.nvg.ui.TrellisColumn;
import com.niuqu.pickupcard.style.StyleModel;
import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.render.Canvas;
import dev.e33.trellis.geom.Path;
import dev.e33.trellis.text.TextLayout;
import dev.e33.trellis.tokens.Tokens;
import dev.e33.trellis.ui.ComponentEnv;
import dev.e33.trellis.ui.UiTree;
import dev.e33.trellis.ui.widget.GlyphPainter;
import dev.e33.trellis.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A-23：<b>组件边界那道桥的离线网</b> —— {@code WidgetSlot} 在搬进框架之前先钉住行为。
 *
 * <p>【为什么要有它】这道桥（槽 → 控件：几何、事件、绘制、焦点环）今天<b>零离线覆盖</b>，
 * 全靠真机读数与肉眼。而搬类是本轮唯一的高风险动作，坑 8 的教训就是"三轮真机全绿、
 * 按钮却点不动"—— 读数看不见的东西必须有测试。所以先补网、再搬。
 *
 * <p>【这张网钉的是什么】两件都不许在搬的时候变：
 * <ul>
 *   <li><b>几何身份</b>：控件收到的绘制盒子与命中盒子是<b>同一个 {@code Rect} 对象</b>
 *       （判据 1）—— 所以断言用 {@code assertSame}，不是 {@code assertEquals}；</li>
 *   <li><b>事件与状态的流向</b>：按下/抬起（含"落点还在不在格子里"）、键只发给焦点那一格、
 *       `KEY_UP` 故意不接、焦点环的颜色与圆角来自调色板。</li>
 * </ul>
 *
 * <p>【这一片全离线】零 MC、零 GPU、零时钟（与 {@code TrellisScrollHitTest} 同一条路）。
 * 画布与字形缝各有一个替身 —— 搬类之后这两条测试会跟着搬去框架（那边已有
 * {@code RecordingCanvas}），到时这两个替身一起删。
 */
class TrellisWidgetSlotTest {

    /** 真机那一档：1280x720 @ guiScale 3（与 {@code TrellisScrollHitTest} 同档）。 */
    private static final float CANVAS_W = 427f;
    private static final float CANVAS_H = 240f;
    private static final float GUI_SCALE = 3f;
    private static final float U = Tokens.Unit.BASE;
    private static final long NOW = 42L;

    /**
     * 一列三个控件行（中间那个是<b>小节头</b>：只有标签、没有控件）。
     *
     * <p>小节头是刻意的：{@link TrellisColumn#controlBox} 对它返回 {@code null}，
     * 而"第几行有控件"和"第几个控件"不是一回事 —— 桥必须承受这种行。
     */
    private static final class Fixture {

        final TestWidgets.Probe top = new TestWidgets.Probe();
        final TestWidgets.Probe bottom = new TestWidgets.Probe();
        final NvgPalette palette = NvgPalette.dark(StyleModel.Accents.defaults(), U);
        final TestCanvas canvas = new TestCanvas();
        final TestGlyphs glyphs = new TestGlyphs();
        final UiTree ui;

        Fixture() {
            ui = TrellisColumn.buildColumn(new Widget[]{top, null, bottom},
                    ConfigRows.topInset(U), U, palette);
            TrellisColumn.layoutColumn(ui, ConfigLayout.compute(CANVAS_W, CANVAS_H).items(),
                    1f / GUI_SCALE);
        }

        /**
         * 画这一帧（走宿主那条真实的路：{@code tick} 给时刻 → 表面带上宿主能力 → 画）。
         *
         * <p>【时刻为什么从树上走了（A-23）】从前它由表面多传一份，而宿主本来就要
         * {@code UiTree.tick(now)} 给动效用 —— 同一个 now 传两处。现在只有树上那一份，
         * 所以这里必须先 tick，否则 {@code PaintCtx.now()} 是 0。
         */
        void paint() {
            ui.tick(NOW);
            TrellisColumn.paint(
                    new TrellisColumn.Frame(canvas, new ComponentEnv(palette, glyphs)), ui);
        }

        /** 第 {@code row} 行控件盒子的中心点。 */
        float centerX(int row) {
            Rect box = TrellisColumn.controlBox(ui, row);
            return box.x() + box.width() / 2f;
        }

        float centerY(int row) {
            Rect box = TrellisColumn.controlBox(ui, row);
            return box.y() + box.height() / 2f;
        }
    }

    // ------------------------------------------------------------------
    // 事件：谁收到、收到什么
    // ------------------------------------------------------------------

    @Test
    @DisplayName("按下：控件收到的盒子就是命中读的那个对象（判据 1），坐标是原样转交的")
    void pressCarriesTheSameRect() {
        Fixture f = new Fixture();
        Rect box = TrellisColumn.controlBox(f.ui, 0);

        assertTrue(f.ui.pointerDown(f.centerX(0), f.centerY(0)));

        assertSame(box, f.top.pressBox, "控件收到的盒子不是 bounds() 那个对象 —— 那就是两份几何");
        assertEquals(1, f.top.presses);
        assertTrue(f.top.held(), "按下之后控件该处于按下态（基类那个字段）");
        assertEquals(f.centerX(0), f.top.pressX, 1e-4);
        assertEquals(f.centerY(0), f.top.pressY, 1e-4);
        assertEquals(0, f.bottom.presses, "按上面那一行，下面那行的控件不该收到东西");
    }

    @Test
    @DisplayName("抬起：落点还在格子里才算一次激活（拖出去松手不激活）")
    void releaseActivatesOnlyWhenStillInside() {
        Fixture f = new Fixture();
        f.ui.pointerDown(f.centerX(0), f.centerY(0));

        f.ui.pointerUp(f.centerX(0), f.centerY(0));

        assertEquals(1, f.top.releases);
        assertTrue(f.top.lastActivate, "落点还在格子里，该激活");
        assertEquals(1, f.top.activates);
        assertFalse(f.top.held(), "抬起之后必须把按下态收回去");
    }

    @Test
    @DisplayName("抬起：拖到格子外面松手 —— 照收事件，但不激活，按下态也要收回")
    void releaseOutsideStillEndsThePress() {
        Fixture f = new Fixture();
        f.ui.pointerDown(f.centerX(0), f.centerY(0));

        // 指针飘到很远的空白处再松手（宿主那边的口径：这一趟仍归按下的那一个）
        f.ui.pointerUp(5f, CANVAS_H - 5f);

        assertEquals(1, f.top.releases, "拖出去松手也必须收到抬起，否则按下态会永久留在屏幕上");
        assertFalse(f.top.lastActivate, "落点不在格子里，不算一次激活");
        assertEquals(0, f.top.activates);
        assertFalse(f.top.held());
    }

    @Test
    @DisplayName("焦点：按哪一格，那一格的控件拿到焦点；上一格收到交还")
    void focusIsPushedToTheWidget() {
        Fixture f = new Fixture();

        f.ui.pointerDown(f.centerX(0), f.centerY(0));
        assertTrue(f.top.hasFocus, "按下的那一格该拿到焦点");
        assertEquals(1, f.top.focusChanges);

        f.ui.pointerDown(f.centerX(2), f.centerY(2));
        assertTrue(f.bottom.hasFocus);
        assertFalse(f.top.hasFocus, "焦点换人了，上一层必须收到交还");
    }

    @Test
    @DisplayName("键只发给焦点那一格：按在谁身上谁收到，别人一个字都收不到")
    void keysGoToTheFocusedSlotOnly() {
        Fixture f = new Fixture();
        f.ui.pointerDown(f.centerX(2), f.centerY(2));      // 焦点给下面那一格

        assertTrue(f.ui.keyDown(65, 2), "控件吃掉了这个键");

        assertEquals(65, f.bottom.keyCode);
        assertEquals(2, f.bottom.keyMods);
        assertEquals(1, f.bottom.keys);
        assertEquals(0, f.top.keys, "没焦点的那一格不该收到键");

        assertTrue(f.ui.charTyped('x'));
        assertEquals('x', f.bottom.typed);
    }

    @Test
    @DisplayName("KEY_UP 故意不接：桥不吃它，宿主照旧拿到 false")
    void keyUpIsDeliberatelyNotTaken() {
        Fixture f = new Fixture();
        f.ui.pointerDown(f.centerX(0), f.centerY(0));

        assertFalse(f.ui.keyUp(65),
                "桥接了 KEY_UP 就等于把它从 MC 的默认路径上抢走 —— 今天没有控件需要它");
    }

    // ------------------------------------------------------------------
    // 绘制：盒子身份、帧号、守卫
    // ------------------------------------------------------------------

    @Test
    @DisplayName("绘制：控件拿到的盒子就是 bounds() 那个对象，帧号是这一帧的")
    void paintHandsTheBoundsObjectItself() {
        Fixture f = new Fixture();
        Rect box = TrellisColumn.controlBox(f.ui, 0);

        f.paint();

        assertSame(box, f.top.paintedBox, "绘制盒子不是 bounds() 那个对象 —— 判据 1 当场破");
        assertEquals(NOW, f.top.paintedNow, "帧号该是这一帧的（控件靠它记『这帧画过我』）");
        assertEquals(1, f.top.paints);
        assertEquals(1, f.bottom.paints, "同一列里每个控件都该被画到");
    }

    @Test
    @DisplayName("★ 没带宿主能力就画：当场抛 —— 静默跳过会变成『控件在、点得到、屏幕上一块空白』")
    void drawingWithoutHostCapabilitiesThrows() {
        Fixture f = new Fixture();

        assertThrows(IllegalStateException.class, () -> f.ui.draw(f.canvas),
                "单参版 UiTree.draw(canvas) 是『只画形状』那一趟，控件必须当场失败");
    }

    @Test
    @DisplayName("★ 宿主能力是每一趟重新盖的：不带它再画一次照样抛（不会留着上一帧的）")
    void capabilitiesAreStampedPerDraw() {
        Fixture f = new Fixture();
        f.paint();

        assertThrows(IllegalStateException.class, () -> f.ui.draw(f.canvas),
                "宿主能力顺着绘制那一趟传，所以不带它的那一趟必须是『没有』，"
                        + "不能留着上一帧的配色与字形");
    }

    // ------------------------------------------------------------------
    // 焦点环：颜色与圆角来自调色板
    // ------------------------------------------------------------------

    @Test
    @DisplayName("焦点环：颜色与圆角都来自调色板那两个角色（没焦点就不画）")
    void theFocusRingComesFromThePalette() {
        Fixture f = new Fixture();

        f.paint();
        assertEquals(0, f.canvas.countStrokesWith(f.palette.focusRing),
                "还没有焦点，不该有焦点环");

        f.ui.pointerDown(f.centerX(0), f.centerY(0));
        f.canvas.clear();
        f.paint();

        assertEquals(1, f.canvas.countStrokesWith(f.palette.focusRing),
                "拿到焦点之后该画一圈焦点环，颜色来自调色板的 focusRing 角色");
        float half = Tokens.Size.HAIRLINE / 2f;
        assertEquals(Math.max(0f, f.palette.radius - half), f.canvas.lastStrokeRadius(), 1e-4,
                "环的圆角取调色板的 radius（与控件自己那条 outline 同一个形状口径）");
    }

    // ------------------------------------------------------------------
    // 替身：这一片要一个画布与一个字形缝（真机那两份都要 MC）
    // ------------------------------------------------------------------

    /**
     * 只记录<b>圆角描边</b>的画布替身：这一片唯一要看的就是焦点环。
     *
     * <p>其余原语照常记进 {@link #ops}（不抛）—— 替身不该因为框架多用了一个原语就炸，
     * 那样测试会以"替身没实现"的形态红，看起来像被测代码坏了。
     */
    private static final class TestCanvas implements Canvas {

        final List<String> ops = new ArrayList<>();
        private final List<Integer> strokeColors = new ArrayList<>();
        private final List<Float> strokeRadii = new ArrayList<>();

        void clear() {
            ops.clear();
            strokeColors.clear();
            strokeRadii.clear();
        }

        int countStrokesWith(int argb) {
            int found = 0;
            for (int color : strokeColors) {
                if (color == argb) {
                    found++;
                }
            }
            return found;
        }

        float lastStrokeRadius() {
            return strokeRadii.get(strokeRadii.size() - 1);
        }

        @Override
        public void save() {
            ops.add("save");
        }

        @Override
        public void restore() {
            ops.add("restore");
        }

        @Override
        public void translate(float dx, float dy) {
            ops.add("translate");
        }

        @Override
        public void scale(float sx, float sy) {
            ops.add("scale");
        }

        @Override
        public void clip(Rect rect) {
            ops.add("clip");
        }

        @Override
        public void resetClip() {
            ops.add("resetClip");
        }

        @Override
        public void fillRect(Rect rect, int argb) {
            ops.add("fillRect");
        }

        @Override
        public void fillRoundRect(Rect rect, float radius, int argb) {
            ops.add("fillRoundRect");
        }

        @Override
        public void strokeRoundRect(Rect rect, float radius, float strokeWidth, int argb) {
            ops.add("strokeRoundRect");
            strokeColors.add(argb);
            strokeRadii.add(radius);
        }

        @Override
        public void strokeLine(float x0, float y0, float x1, float y1, float strokeWidth, int argb) {
            ops.add("strokeLine");
        }

        @Override
        public void fillCircle(float centerX, float centerY, float radius, int argb) {
            ops.add("fillCircle");
        }

        @Override
        public void fillPath(Path path, int argb) {
            ops.add("fillPath");
        }

        @Override
        public void strokePath(Path path, float strokeWidth, int argb) {
            ops.add("strokePath");
        }

        @Override
        public void drawImage(long texture, Rect destination, float alpha) {
            ops.add("drawImage");
        }

        @Override
        public void beginLayer(Rect bounds, float alpha) {
            ops.add("beginLayer");
        }

        @Override
        public void endLayer() {
            ops.add("endLayer");
        }

        @Override
        public void drawText(TextLayout layout, float x, float baselineY, int argb) {
            ops.add("drawText");
        }
    }

    /** 不画字的字形缝（控件里的文字这一趟不验 —— 那是宿主自己的事）。 */
    private static final class TestGlyphs implements GlyphPainter {

        @Override
        public float lineHeight() {
            return 9f;
        }

        @Override
        public float textWidth(String text) {
            return text.length() * 6f;
        }

        @Override
        public void text(String text, float x, float topY, int argb) {
        }

        @Override
        public void textCentered(String text, float centerX, float topY, int argb) {
        }

        @Override
        public void textCenteredFitted(String text, float centerX, float topY, int argb,
                                       float maxWidth) {
        }

        @Override
        public void textFitted(String text, float x, float topY, int argb, float maxWidth) {
        }
    }
}
