package com.niuqu.pickupcard.render.nvg.ui;

import dev.e33.trellis.geom.Rect;
import dev.e33.trellis.render.Canvas;

/**
 * 控件的绘制上下文：<b>几何只有一个来源（{@link #box()}），形状走 Trellis 画布，字形走宿主</b>。
 *
 * <p>【这一层是 A-10 第二步的产物】接管之前，控件自己存着 {@code x/y/w/h}（宿主的整数取整版），
 * 而命中、悬停、标签读的是 Trellis 的 {@code bounds()} —— 两份几何，实测差 1.3 逻辑 px（A-4）。
 * 现在控件<b>不存几何</b>：谁画它，谁把那个 {@code Rect} 交进来；树那条路交的就是
 * {@code Component.bounds()} <b>本身</b>（命中和绘制因此读同一个对象，判据 1 落在结构上）。
 *
 * <p>【坐标口径：本对象的坐标一律相对 {@link #box()} 左上角】控件只认"我在盒子里画在哪"，
 * 盒子在屏幕的哪里是别人的事 —— 这也是"复制一段绘制代码到别处不串位置"的前提。
 * <b>事件那一侧相反</b>：{@code press/drag} 收的指针是绝对坐标（见 {@link NvgWidget}），别混。
 *
 * <p>【形状为什么必须走 Trellis 画布】NanoVG 是全局状态机：对齐/字体/alpha 是全局的，
 * 一段绘制代码会污染后面那段。{@code Canvas} 的每个原语自带它依赖的全部状态，
 * 于是"从开关的绘制代码复制到滑条"这件事才安全（判据 3）。
 *
 * <p>【字形为什么走宿主】见 {@link GlyphPainter}：MC 的字是位图图集，喂不进
 * {@code Canvas.drawText}。
 */
public final class PaintCtx {

    private final Rect box;
    private final Canvas canvas;
    private final NvgPalette palette;
    private final GlyphPainter glyphs;
    private final long now;

    /**
     * @param box     这一格几何（逻辑坐标）。树那条路给的是 {@code bounds()} 那个对象本身
     * @param canvas  Trellis 画布（宿主上下文接过来的，见 {@code NvgCanvas.attach}）
     * @param palette 配色（仍是宿主的：主题与深浅色是宿主的事）
     * @param glyphs  字形缝
     * @param now     这一帧的时刻（控件记"这一帧画过我"用）
     */
    public PaintCtx(Rect box, Canvas canvas, NvgPalette palette, GlyphPainter glyphs, long now) {
        this.box = box;
        this.canvas = canvas;
        this.palette = palette;
        this.glyphs = glyphs;
        this.now = now;
    }

    /** 这一格的几何。<b>唯一来源</b> —— 控件不许再存一份。 */
    public Rect box() {
        return box;
    }

    /** 盒子宽（= {@code box().width()}，省得每处都拆一次）。 */
    public float width() {
        return box.width();
    }

    /** 盒子高。 */
    public float height() {
        return box.height();
    }

    public NvgPalette palette() {
        return palette;
    }

    public long now() {
        return now;
    }

    public Canvas canvas() {
        return canvas;
    }

    public GlyphPainter glyphs() {
        return glyphs;
    }

    // ------------------------------------------------------------------
    // 形状（坐标相对 box 左上角）
    // ------------------------------------------------------------------

    /** 实心圆角矩形。 */
    public void fillRoundRect(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0f || h <= 0f) {
            return;     // 零尺寸不画：和宿主从前那套一致，也省掉后端一次空路径
        }
        canvas.fillRoundRect(abs(x, y, w, h), radius, argb);
    }

    /**
     * 圆角矩形描边，线宽取调色板那一档。
     *
     * <p>【为什么这里要内缩半个线宽】NanoVG 的 {@code nvgStroke} 是<b>骑在路径上</b>的
     * （一半在线外），宿主从前的 {@code NvgUi.strokeRoundRect} 因此先把路径内缩半个线宽、
     * 再按 {@code w - 线宽} 建路径 —— 整条线落在框里。Trellis 的 {@code Canvas.strokeRoundRect}
     * 的语义是"把这条路径描出来"，不含内缩，所以这件事必须由调用方补齐。
     *
     * <p>【不补的后果是可见的】{@code outlineWidth = 1} 逻辑 px、guiScale 3 → 每条边向外多
     * 1.5 个设备像素：控件看着比它的命中框大一圈，而且"画出来的都在框里"这条不再成立
     * （A-10 第二步的像素对账就是这么发现的）。
     */
    public void strokeRoundRect(float x, float y, float w, float h, float radius, int argb) {
        float width = palette.outlineWidth;
        if (w <= width || h <= width) {
            return;
        }
        float half = width / 2f;
        canvas.strokeRoundRect(abs(x + half, y + half, w - width, h - width),
                Math.max(0f, radius - half), width, argb);
    }

    /** 实心圆（滑块与开关的钮）。 */
    public void circle(float centerX, float centerY, float radius, int argb) {
        if (radius <= 0f) {
            return;
        }
        canvas.fillCircle(box.x() + centerX, box.y() + centerY, radius, argb);
    }

    /** 一块"井"：控件底 = 圆角填充 + 描边（宿主从前 {@code NvgUi.well} 那两笔）。 */
    public void well(float x, float y, float w, float h, int argb) {
        fillRoundRect(x, y, w, h, palette.radius, argb);
        strokeRoundRect(x, y, w, h, palette.radius, palette.outline);
    }

    // ------------------------------------------------------------------
    // 文字（字形由宿主画，见 GlyphPainter）
    // ------------------------------------------------------------------

    /** MC 的行高。 */
    public float lineHeight() {
        return glyphs.lineHeight();
    }

    /** 一串字画出来多宽（MC 度量）。 */
    public float textWidth(String text) {
        return glyphs.textWidth(text);
    }

    public void text(String text, float x, float topY, int argb) {
        glyphs.text(text, box.x() + x, box.y() + topY, argb);
    }

    public void textCentered(String text, float centerX, float topY, int argb) {
        glyphs.textCentered(text, box.x() + centerX, box.y() + topY, argb);
    }

    public void textCenteredFitted(String text, float centerX, float topY, int argb,
                                   float maxWidth) {
        glyphs.textCenteredFitted(text, box.x() + centerX, box.y() + topY, argb, maxWidth);
    }

    public void textFitted(String text, float x, float topY, int argb, float maxWidth) {
        glyphs.textFitted(text, box.x() + x, box.y() + topY, argb, maxWidth);
    }

    /** 相对坐标 -> 逻辑坐标。<b>全类只有这一处做换算</b>（其余都走它）。 */
    private Rect abs(float x, float y, float w, float h) {
        return new Rect(box.x() + x, box.y() + y, w, h);
    }
}
