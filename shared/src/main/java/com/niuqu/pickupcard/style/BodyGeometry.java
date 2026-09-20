package com.niuqu.pickupcard.style;

/**
 * 卡面三个框的<b>自然位置</b>（卡片局部坐标：卡左缘 = 0，未缩放单位）。
 *
 * <p>三段式：<pre>非镜像  [竖条] gap [图标格] gap [信息框]</pre>
 * <pre>镜像    [信息框] gap [图标格] gap [竖条]</pre>
 *
 * <h2>为什么它必须只有一份</h2>
 * 这两个框的位置在渲染里要被问两遍：外壳（{@code NvgCardPainter#paintShell}，NanoVG 画矢量）
 * 与内容（{@code NvgCardContent#paint}，原版批次画图标与字形）。两边各算一遍的话，
 * 镜像这种"整套坐标反着来"的改动就一定会漏掉一边 —— 2026-09-20 用户报的
 * 「卡片动画镜像了、文字动画没有」就是它：内容那侧把滑出方向算反了（内容从左边冒出来，
 * 外壳从右边冒出来），稳态看不出来，只有动画途中才现形。
 *
 * <p>【方向不在这个类里】这里只答"静止时框在哪"；入场/退场的位移是**沿同一个 shift 平移**
 * （见 {@code NvgCardPainter#bodyShiftOf}，它的方向因子已经把镜像翻好了）。
 * 调用方一律 {@code 自然位置 + shift}，谁再乘一次方向就会把动画翻回去。
 *
 * @param bodyLeft  内容区（不含竖条）左缘
 * @param bodyWidth 内容区宽 = 卡宽 - 竖条 - 间隙
 * @param iconLeft  图标格左缘（图标格是 {@code cardHeight} 见方的格子）
 * @param infoLeft  信息框左缘
 * @param infoWidth 信息框宽
 */
public record BodyGeometry(float bodyLeft, float bodyWidth,
                           float iconLeft, float infoLeft, float infoWidth) {

    /**
     * 算出一张卡的自然几何。
     *
     * @param cardWidth  卡总宽（含竖条）
     * @param cardHeight 卡高，也是图标格的边长
     * @param barWidth   竖条宽
     * @param gap        框与框之间的间隙
     * @param mirror     镜像卡片：竖条贴右缘，内容区从卡左缘起算
     */
    public static BodyGeometry of(float cardWidth, float cardHeight, float barWidth, float gap,
                                  boolean mirror) {
        float bodyW = Math.max(0f, cardWidth - barWidth - gap);
        float infoW = Math.max(0f, bodyW - cardHeight - gap);
        if (!mirror) {
            float icon = barWidth + gap;
            return new BodyGeometry(icon, bodyW, icon, icon + cardHeight + gap, infoW);
        }
        // 镜像：图标格贴着竖条（卡右缘那侧），信息框占满左侧剩下的宽
        return new BodyGeometry(0f, bodyW, Math.max(0f, bodyW - cardHeight), 0f, infoW);
    }
}
