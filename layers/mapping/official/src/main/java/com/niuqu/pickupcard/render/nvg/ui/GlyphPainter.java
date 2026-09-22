package com.niuqu.pickupcard.render.nvg.ui;

/**
 * <b>字形缝</b>：位置与串由布局/文本层给，<b>字形由宿主画</b>。
 *
 * <p>【为什么需要这么一层】试点的分工是"Trellis 管几何与排布，字形由宿主喂"，原因是硬的：
 * Minecraft 的字是<b>位图图集</b>，而 Trellis 的 {@code Canvas.drawText} 收的是
 * {@code TextLayout} —— 它要求后端注册的字体和度量表来自<b>同一个 TTF</b>
 * （见 {@code Canvas#drawText} 的注释），MC 的图集喂不进去；{@code Canvas.drawImage}
 * 也帮不上忙：它没有源子矩形，一张图集画不出单个字形。
 *
 * <p>【A-7 已经解决了一半】{@code McFont} 把 MC 的度量灌进文本层，所以"这串字有多宽、
 * 基线在哪"Treilis 算得出来。这里补的是另一半：<b>把算好的位置交回宿主去落笔</b>。
 *
 * <p>【坐标口径与 {@link NvgUi} 一致】绝对逻辑坐标，{@code y} 是<b>行框顶</b>（不是基线）——
 * {@code NvgUi.text*} 就是这么收的，因此实现里只做转发、不换算，出来的像素与从前逐点相同。
 * 取整、延迟到帧尾提交、裁剪这些都留在 {@link NvgUi} 里（它们是"MC 的批次怎么走"的事）。
 *
 * <p>【为什么不是直接把 {@code NvgUi} 传下去】控件要能被离线测试：这个 JVM 里没有
 * {@code Minecraft}，{@code NvgUi} 的类一碰就 {@code NoClassDefFoundError}。有了这层接口，
 * 测试可以喂一个只记调用、不画字的替身，于是"形状那一半"能在离屏画布上验像素（判据 3 的考试）。
 */
public interface GlyphPainter {

    /** 一行的高度（MC 的行框）——控件拿它做垂直居中，别自己拿字号拼。 */
    float lineHeight();

    /** 一串字画出来有多宽（MC 的度量）——右对齐、光标位置要用。 */
    float textWidth(String text);

    /** 左对齐写一行。{@code x} 是左缘，{@code topY} 是行框顶。 */
    void text(String text, float x, float topY, int argb);

    /** 居中写一行（{@code centerX} 给中心）。 */
    void textCentered(String text, float centerX, float topY, int argb);

    /**
     * 居中写一行，<b>超过 {@code maxWidth} 就整体缩小到装得下</b>。
     *
     * <p>宿主这条路（{@code k = maxWidth / 字宽}）与 Trellis 的 {@code shrinkToFit}
     * （重测一个更小字号 + 地板 + 截断）不是同一套口径，见 A-8/A-9 的记录；控件里的值沿用前者，
     * 因为"值认不出来"比"越界"更糟。
     */
    void textCenteredFitted(String text, float centerX, float topY, int argb, float maxWidth);

    /** 左对齐写一行，超过 {@code maxWidth} 就整体缩小（用处见上一条）。 */
    void textFitted(String text, float x, float topY, int argb, float maxWidth);
}
