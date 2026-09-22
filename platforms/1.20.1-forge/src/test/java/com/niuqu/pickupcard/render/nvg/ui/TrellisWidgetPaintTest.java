package com.niuqu.pickupcard.render.nvg.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.render.nanovg.NvgCanvas;
import dev.e33.trellis.render.nanovg.Offscreen;
import dev.e33.trellis.tokens.Units;
import com.niuqu.pickupcard.style.StyleModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>判据 3 的正面考试 + "几何只从 box 来"的像素证据（A-10 第二步）。</b>
 *
 * <p>【为什么能离线做】Trellis 的 {@code Offscreen} 会开一个隐藏窗口 + GL 3.2 上下文，
 * 画进 FBO 再回读像素；而控件现在只依赖两样东西 —— Trellis 的 {@code Canvas}（形状）与
 * {@link GlyphPainter}（字形：测试里喂替身，MC 的字形本来也只由真机画）。所以控件能在
 * 这个 JVM 里被真画一遍、被量像素，不需要启动游戏。
 *
 * <p>【台子照抄 Minecraft 的账】设备缓冲是 {@code 逻辑 x 3}，{@code begin} 收逻辑尺寸 + 倍数 ——
 * 于是"逻辑 1 = 设备 3"是量出来的，不是假定的（见 Trellis 的 {@code AttachedCanvasTest}）。
 *
 * <p>【这里钉的五条】
 * <ol>
 *   <li><b>判据 3：复制一段绘制代码到别处，不串状态</b> —— 先画一段"别人的"绘制代码，
 *       再画目标控件，像素与单独画它时逐点相同。</li>
 *   <li><b>几何只从 {@code box} 来</b> —— 盒子平移 (dx, dy) 逻辑 px，整幅画跟着平移 (3dx, 3dy)
 *       设备 px；不是"控件自己记着位置"。</li>
 *   <li><b>落在 1/3 上的盒子精确落在设备像素上</b> —— 再挪 1/3 逻辑 px 正好挪 1 设备 px
 *       （量的是"最左边的墨"，抗锯齿在几何边缘外那 ~2% 的羽化不算数）。</li>
 *   <li><b>文字交给宿主</b> —— 位置是 {@code box} 的绝对值，字形由 {@link GlyphPainter} 收走。</li>
 *   <li><b>描边不外溢</b> —— 盒子外面一个像素都没有（NanoVG 的 stroke 骑在路径上，
 *       内缩半个线宽这件事由 {@link PaintCtx} 补齐）。</li>
 * </ol>
 * 需要桌面会话（GLFW 要能建窗口）。字体路径与本机 Trellis 检出位置一致 ——
 * 这条分支本来就不进 main（见 {@code settings.gradle} 的 {@code includeBuild}）。
 */
class TrellisWidgetPaintTest {

    /** 逻辑尺寸；设备缓冲是它的 3 倍（Minecraft：framebuffer = guiScaledWidth x guiScale）。 */
    private static final int W = 64;
    private static final int H = 48;
    private static final float SCALE = 3f;
    private static final Path FONT = Path.of("D:/Trellis/design/fonts/Inter-Regular.otf");

    private static Offscreen off;
    private static NvgPalette palette;

    @BeforeAll
    static void open() {
        assertTrue(Files.isRegularFile(FONT),
                "离屏台要一份字体（Trellis 的度量表就是它烘的）：" + FONT
                        + "。这份路径与 settings.gradle 的 includeBuild 同一个本机检出。");
        off = Offscreen.create((int) (W * SCALE), (int) (H * SCALE), FONT);
        palette = NvgPalette.dark(StyleModel.Accents.defaults(), Units.u(240f));
    }

    @AfterAll
    static void shut() {
        off.close();
    }

    /** 只记调用、不画字的字形替身（MC 的字形不在这个 JVM 里）。 */
    private static final class RecordingGlyphs implements GlyphPainter {
        final List<String> calls = new ArrayList<>();
        float lastX;
        float lastTop;

        @Override
        public float lineHeight() {
            return 9f;      // MC 的正文行高（真机就是 9）
        }

        @Override
        public float textWidth(String text) {
            return text.length() * 5f;
        }

        @Override
        public void text(String text, float x, float topY, int argb) {
            record("text", text, x, topY);
        }

        @Override
        public void textCentered(String text, float centerX, float topY, int argb) {
            record("centered", text, centerX, topY);
        }

        @Override
        public void textCenteredFitted(String text, float centerX, float topY, int argb,
                                       float maxWidth) {
            record("centeredFitted", text, centerX, topY);
        }

        @Override
        public void textFitted(String text, float x, float topY, int argb, float maxWidth) {
            record("fitted", text, x, topY);
        }

        private void record(String kind, String text, float x, float topY) {
            calls.add(kind + ":" + text);
            lastX = x;
            lastTop = topY;
        }
    }

    /** 画一帧并回读（顶行在前的 ARGB 设备像素）。 */
    private static int[] frame(Runnable draw) {
        off.clear();
        NvgCanvas canvas = off.canvas();
        canvas.begin(W, H, SCALE);
        draw.run();
        canvas.end();
        return off.snapshot();
    }

    private static PaintCtx ctx(Rect box, GlyphPainter glyphs) {
        return new PaintCtx(box, off.canvas(), palette, glyphs, 1_000L);
    }

    private static NvgSlider slider(double value) {
        return new NvgSlider("滑条", 0, 100, 1, () -> value, v -> {
        }, v -> Math.round(v) + "ms");
    }

    /**
     * 两个像素算不算"同一个"：每通道容差 1。
     *
     * <p>【为什么要容差】NanoVG 的抗锯齿是浮点算的：同一份几何画在 (8,6) 与 (14,12) 上，
     * 边缘那一列覆盖率可能差 1/255（实测 C9 vs CA）。那不是"画错了"，位置错一格会差几十。
     * 判据 3 那两条（同一位置重画）仍然用逐点相等 —— 那里必须一个位都不差。
     */
    private static boolean close(int a, int b) {
        for (int shift : new int[] {24, 16, 8, 0}) {
            if (Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)) > 1) {
                return false;
            }
        }
        return true;
    }

    /** 设备像素（x,y 是<b>设备</b>坐标）。 */
    private static int px(int[] frame, int x, int y) {
        return frame[y * off.width() + x];
    }

    // ------------------------------------------------------------------
    // 1. 判据 3：复制一段绘制代码到别处，不串状态
    // ------------------------------------------------------------------

    /**
     * "别人的绘制代码"：<b>从 {@link NvgToggle} 的绘制体整段抄过来</b>的（值换成字面量），
     * 就是判据 3 说的那个动作本身 —— 抄完之后目标控件的像素不许变。
     */
    private static final class CopiedToggleBody extends NvgWidget {
        CopiedToggleBody() {
            super("抄来的开关");
        }

        @Override
        protected void paint(PaintCtx ctx) {
            NvgPalette p = ctx.palette();
            float w = ctx.width();
            float h = ctx.height();
            boolean on = true;
            float pillW = Math.min(26f, w * 0.32f);
            float pillH = Math.max(8f, h - 8f);
            float pillX = w - pillW - 2f;
            float pillY = (h - pillH) / 2f;
            float r = pillH / 2f;

            ctx.textCentered("开", (pillX - ctx.textWidth("开")) / 2f,
                    (h - ctx.lineHeight()) / 2f, p.text);
            ctx.fillRoundRect(pillX, pillY, pillW, pillH, r, on ? p.accent : p.well);
            float knobX = on ? pillX + pillW - r : pillX + r;
            ctx.circle(knobX, pillY + r, r - 1f, on ? 0xFFFFFFFF : 0xFFD5DAE5);
        }
    }

    @Test
    @DisplayName("判据 3：先画一段抄来的绘制代码，再画滑条 —— 滑条的像素逐点不变")
    void copiedDrawCodeDoesNotBleed() {
        Rect target = new Rect(12f, 9f, 30f, 12f);
        Rect intruder = new Rect(12f, 30f, 30f, 12f);

        int[] alone = frame(() -> slider(50).draw(ctx(target, new RecordingGlyphs())));
        int[] afterCopy = frame(() -> {
            new CopiedToggleBody().draw(ctx(intruder, new RecordingGlyphs()));
            slider(50).draw(ctx(target, new RecordingGlyphs()));
        });

        assertTrue(regionHasInk(afterCopy, intruder),
                "抄来的那段必须真的画了东西 —— 否则这条考试是空的");
        assertEquals(0, pixelDiffIn(alone, afterCopy, target),
                "滑条那一片像素不该被前面那段绘制代码影响（NanoVG 是全局状态机，正是判据 3 要防的）");
    }

    @Test
    @DisplayName("判据 3：顺序反过来也一样 —— 先画滑条，再画抄来的那段，滑条不动")
    void orderDoesNotMatter() {
        Rect target = new Rect(12f, 9f, 30f, 12f);
        Rect intruder = new Rect(12f, 30f, 30f, 12f);

        int[] alone = frame(() -> slider(50).draw(ctx(target, new RecordingGlyphs())));
        int[] beforeCopy = frame(() -> {
            slider(50).draw(ctx(target, new RecordingGlyphs()));
            new CopiedToggleBody().draw(ctx(intruder, new RecordingGlyphs()));
        });

        assertEquals(0, pixelDiffIn(alone, beforeCopy, target),
                "后面画什么不该回头改动已经画好的那片");
    }

    // ------------------------------------------------------------------
    // 2. 几何只从 box 来
    // ------------------------------------------------------------------

    @Test
    @DisplayName("盒子平移 (dx,dy) 逻辑 px：整幅画跟着平移 (3dx,3dy) 设备 px")
    void geometryComesFromTheBox() {
        int[] atOrigin = frame(() -> slider(50).draw(ctx(new Rect(8f, 6f, 30f, 12f),
                new RecordingGlyphs())));
        int[] shifted = frame(() -> slider(50).draw(ctx(new Rect(14f, 12f, 30f, 12f),
                new RecordingGlyphs())));

        int dx = 18;    // 6 逻辑 px x 3
        int dy = 18;    // 6 逻辑 px x 3
        int compared = 0;
        int mismatched = 0;
        for (int y = 6 * 3; y < (6 + 12) * 3; y++) {
            for (int x = 8 * 3; x < (8 + 30) * 3; x++) {
                compared++;
                if (!close(px(atOrigin, x, y), px(shifted, x + dx, y + dy))) {
                    mismatched++;
                }
            }
        }
        assertTrue(compared > 1000, "比较的像素太少，这条考试没意义：" + compared);
        assertTrue(regionHasInk(atOrigin, new Rect(8f, 6f, 30f, 12f)),
                "这一帧必须真的画了东西 —— 否则两个全空的帧也会「逐点相同」");
        assertEquals(0, mismatched, "盒子平移了，画出来的东西必须跟着平移（抗锯齿边缘允许 1/255 "
                + "的浮点差，位置错一格会差几十）；第一处不同："
                + firstMismatch(atOrigin, shifted, dx, dy));
    }

    @Test
    @DisplayName("盒子落在 1/3 上：再挪 1/3 逻辑 px，墨迹正好挪 1 设备 px（对齐网格 = 1/guiScale）")
    void fractionalBoxMovesExactlyOneDevicePixel() {
        // 轨道左缘 = box.x + 4。取 value=100 把旋钮挪到右端 —— 左端就只剩轨道。
        // 【为什么值取满】旋钮半径 5 逻辑 px（15 设备 px），值最小那颗钮压在轨道左缘上，
        // "最左的墨"就成了钮的，量不到轨道。
        int[] a = frame(() -> slider(100).draw(ctx(new Rect(16f / 3f, 4f, 30f, 12f),
                new RecordingGlyphs())));
        int[] b = frame(() -> slider(100).draw(ctx(new Rect(17f / 3f, 4f, 30f, 12f),
                new RecordingGlyphs())));

        // 16/3 + 4 = 28/3 逻辑 -> 正好设备第 28 列；再挪 1/3 逻辑就是第 29 列。
        // 【阈值 16】抗锯齿在几何边缘外还有一层 ~2% 的羽化（实测 alpha 4），那不是"画到了这里"；
        // 吸附错一格会让整列变成 alpha 一百多 —— 差两个数量级。
        assertEquals(28, leftmostInk(a, 16),
                "x=16/3 的盒子：轨道左缘应当落在设备第 28 列（对齐网格是 1/3 逻辑 px）");
        assertEquals(29, leftmostInk(b, 16),
                "再挪 1/3 逻辑 px 必须正好挪 1 设备 px —— 这一条就在说「交给画布的倍数是 3」");
    }

    // ------------------------------------------------------------------
    // 3. 文字交给宿主
    // ------------------------------------------------------------------

    @Test
    @DisplayName("文字位置是 box 的绝对值，字形交给宿主（画布上只留下控件底）")
    void textGoesToTheHostPainter() {
        RecordingGlyphs glyphs = new RecordingGlyphs();
        Rect box = new Rect(10f, 5f, 30f, 12f);
        int[] frame = frame(() -> new NvgButton("值", () -> "480ms", () -> {
        }).draw(ctx(box, glyphs)));

        assertEquals(1, glyphs.calls.size(), "这一个控件只登记一次文字：" + glyphs.calls);
        assertEquals("centeredFitted:480ms", glyphs.calls.get(0));
        assertEquals(10f + 15f, glyphs.lastX, 0.01f, "居中点是 box.x + 盒宽/2");
        assertEquals(5f + (12f - 9f) / 2f, glyphs.lastTop, 0.01f, "行框顶是 box.y + (盒高-行高)/2");

        // 【这一条只证"画布上有底"】字形那半测不出来：替身本来就不画字，真机上字形也归
        // MC 的原版批次（不进 NanoVG 的画布）。字形到底画没画，只有真机截图能答（A-10 第 21 轮）。
        assertTrue(regionHasInk(frame, box), "控件底应当在画布上（证明这一帧真的画过）");
    }

    // ------------------------------------------------------------------
    // 4. 描边不外溢
    // ------------------------------------------------------------------

    @Test
    @DisplayName("描边的外沿就在盒子上：盒子外面一个像素都没有（内缩半个线宽的口径）")
    void strokeStaysInsideTheBox() {
        // NvgButton 走 {@code well} = 圆角填充 + 描边，正好考"描边落哪"这件事。
        Rect box = new Rect(16f / 3f, 4f, 30f, 12f);
        int[] frame = frame(() -> new NvgButton("值", () -> "480ms", () -> {
        }).draw(ctx(box, new RecordingGlyphs())));

        assertTrue(regionHasInk(frame, box), "先证明它真的画了");
        assertEquals(0, outsideInk(frame, box),
                "NanoVG 的 stroke 骑在路径上（一半在线外）；不内缩半个线宽的话，"
                        + "guiScale 3 下每条边会向外多出 1.5 个设备像素");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 两个整帧在这一片逻辑区域（含）里差多少像素。 */
    private static int pixelDiffIn(int[] a, int[] b, Rect logical) {
        int diff = 0;
        int x0 = Math.round(logical.x() * SCALE);
        int y0 = Math.round(logical.y() * SCALE);
        int x1 = Math.round((logical.x() + logical.width()) * SCALE);
        int y1 = Math.round((logical.y() + logical.height()) * SCALE);
        for (int y = Math.max(0, y0); y < Math.min(off.height(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(off.width(), x1); x++) {
                if (px(a, x, y) != px(b, x, y)) {
                    diff++;
                }
            }
        }
        return diff;
    }

    /** 这一帧里最左边"有墨"的设备列（alpha >= threshold；没有就是 -1）。 */
    private static int leftmostInk(int[] frame, int threshold) {
        for (int x = 0; x < off.width(); x++) {
            for (int y = 0; y < off.height(); y++) {
                if (((px(frame, x, y) >>> 24) & 0xFF) >= threshold) {
                    return x;
                }
            }
        }
        return -1;
    }

    /** 第一处不同的坐标与两侧的像素值（诊断用）。 */
    private static String firstMismatch(int[] a, int[] b, int dx, int dy) {
        for (int y = 6 * 3; y < (6 + 12) * 3; y++) {
            for (int x = 8 * 3; x < (8 + 30) * 3; x++) {
                if (px(a, x, y) != px(b, x + dx, y + dy)) {
                    return String.format("逻辑(%.3f,%.3f) 设备(%d,%d)=%08X vs 平移后=%08X",
                            x / SCALE, y / SCALE, x, y, px(a, x, y), px(b, x + dx, y + dy));
                }
            }
        }
        return "(没有)";
    }

    /** 盒子<b>之外</b>（设备像素）有多少个非透明像素。 */
    private static int outsideInk(int[] frame, Rect logical) {
        int x0 = Math.round(logical.x() * SCALE);
        int y0 = Math.round(logical.y() * SCALE);
        int x1 = Math.round((logical.x() + logical.width()) * SCALE);
        int y1 = Math.round((logical.y() + logical.height()) * SCALE);
        int n = 0;
        for (int y = 0; y < off.height(); y++) {
            for (int x = 0; x < off.width(); x++) {
                boolean inside = x >= x0 && x < x1 && y >= y0 && y < y1;
                if (!inside && (px(frame, x, y) >>> 24) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    /** 这一片逻辑区域里有没有画出东西。 */
    private static boolean regionHasInk(int[] frame, Rect logical) {
        int x0 = Math.round(logical.x() * SCALE);
        int y0 = Math.round(logical.y() * SCALE);
        int x1 = Math.round((logical.x() + logical.width()) * SCALE);
        int y1 = Math.round((logical.y() + logical.height()) * SCALE);
        for (int y = Math.max(0, y0); y < Math.min(off.height(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(off.width(), x1); x++) {
                if ((px(frame, x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    @DisplayName("反着来一次：空白帧在那一片上是空的（上面几条不是靠\"到处都画了\"混过去的）")
    void emptyFrameHasNoInk() {
        int[] empty = frame(() -> {
        });
        assertFalse(regionHasInk(empty, new Rect(12f, 9f, 30f, 12f)),
                "什么都不画时那一片必须是空的");
    }
}
