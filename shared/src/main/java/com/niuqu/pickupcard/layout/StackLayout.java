package com.niuqu.pickupcard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片竖排的纯几何：<b>底锚、向上生长</b>（2026-09-19 定案）—— 新卡永远出现在锚线
 * （HUD 带上方那条固定底线），旧的被顶上去。
 * <p>
 * 【为什么单独成一个类】排布是"几张卡、多大、屏幕多大"进去、"每张卡在哪"出来的纯函数，
 * 不认识 {@code GuiGraphics}、不认识字体、不认识 Forge。放在 shared 里的好处是它能在
 * 没有游戏的情况下跑单测 —— 上一版把这段数学写在 436 行的渲染器里，只能靠真机截图
 * 才能发现"卡叠在一起了"。
 * <p>
 * 【底锚的账，第三次回到它】2026-09-18 定过顶锚（新卡在锚点、旧的往下挤），理由是
 * "最新那张离视线最远"——那是<b>锚在准星旁</b>时才成立的理由。第四批审计把锚点搬回
 * 右下贴底后，这条理由反了过来：底锚下新卡出现在离快捷栏最近的固定底线上，1 张卡时
 * 贴底不悬空、拾取高潮向上生长；常见画布（427×240）锚点以上有 145px，同屏上限 5 张
 * （116px）真的放得下 —— 顶锚版在准星旁只剩 33px，同屏 5 张是空头支票。三次摇摆的
 * 几何账都记在案（见类注释与 memory），别再凭感觉翻第四 次。
 * <p>
 * 【抖动怎么兜】新卡插队时旧的全体上移一格，由 {@code CardMove} 的 340ms 过渡平滑；
 * 到点退场的是最老那张，它在底锚下的<b>堆顶</b>，走掉不动任何人。
 * <p>
 * 【坐标系】GUI 缩放之后的像素，原点在屏幕左上角，y 向下。这跟 {@code GuiGraphics}
 * 的绘制坐标系一致 —— 调用方拿到就能直接画，不需要再换算。
 */
public final class StackLayout {

    /** 一张卡的尺寸请求。 */
    public record Size(float width, float height) {
    }

    /** 一张卡算出来的位置。{@code index} 指回调用方传进来的序号。 */
    public record Slot(int index, float x, float y, float width, float height) {
    }

    private StackLayout() {
    }

    /**
     * 底锚、向上生长（最新的第 0 张贴着锚线，旧的依次往上）。
     * <p>
     * 【水平只有一条公式】{@code x = clamp(锚点边, 0, 放得下的最右位置)}：
     * 锚点是卡"想停"的那条边，放不下就往左让 —— <b>配置表达的是意图，不是绝对坐标</b>，
     * 内容再长也不会被挤出屏幕右缘。
     *
     * @param sizes     每张卡的尺寸，<b>第 0 张是最新的</b>（贴着锚线）。这个顺序跟
     *                  {@code CardStage} 传进来的顺序一致（live 是老到新，调用方反转过），别再反过来。
     * @param guiWidth  当前 GUI 逻辑宽度
     * @param guiHeight 当前 GUI 逻辑高度
     * @param layout    锚点与行为设置
     * @param marginX   卡片右缘距屏幕右边的安全留白
     * @param marginY   距屏幕<b>下边</b>的留白（HUD 带）；锚点被拖得太低时用它夹出一张卡的位置
     * @param gap       卡与卡之间的间隙
     * @return 与 {@code sizes} 同序的位置列表
     */
    public static List<Slot> stack(List<Size> sizes, float guiWidth, float guiHeight,
                                   LayoutSettings layout, int marginX, int marginY, float gap) {
        int n = sizes.size();
        List<Slot> slots = new ArrayList<>(n);
        if (n == 0) return slots;

        float left = layout.anchorLeft(guiWidth);
        // 锚线就是第 0 张（最新）的顶边；之后每张都比前一张高一个"卡高 + 间隙"。
        // 从锚线往上数不需要预知总高，一张一张减就行。
        float y = layout.anchorTop(guiHeight, sizes.get(0).height(), marginY);
        for (int i = 0; i < n; i++) {
            Size size = sizes.get(i);
            // 放得下的最右位置；再夹进屏幕，避免超宽卡算出负坐标
            float maxLeft = guiWidth - marginX - size.width();
            // 【两种对齐一条线】左缘锚定：竖条左缘贴锚线（界面上的「竖条左缘锚定」档）；
            // 右缘对齐：卡右缘贴锚线，左缘随卡宽参差（界面上的「右缘对齐」档）。
            float x;
            if (layout.align() == LayoutSettings.Side.RIGHT) {
                x = Math.max(0f, Math.min(left - size.width(), maxLeft));
            } else {
                x = Math.max(0f, Math.min(left, maxLeft));
            }
            slots.add(new Slot(i, x, y, size.width(), size.height()));
            y -= size.height() + gap;
        }
        return slots;
    }

    /** 这一摞卡的整体高度（含间隙）。屏幕放不下时调用方拿它做取舍。 */
    public static float totalHeight(List<Size> sizes, float gap) {
        if (sizes.isEmpty()) return 0f;
        float total = gap * (sizes.size() - 1);
        for (Size size : sizes) total += size.height();
        return total;
    }

    /**
     * 锚线<b>以上</b>能完整放下几张卡（锚线到屏幕顶就是卡堆的地盘）。
     * <p>
     * 【为什么取舍必须有人做】{@link #stack} 只做减法：{@code y} 会一路减过屏幕顶。
     * 超出的卡不该硬切 —— 调用方把它们交回排队（2026-09-19 定案：不再 forgetLeft）。
     * <p>【判据 = 最上面那张的顶边不出屏】第 i 张的顶边 y = anchorTop − i·(卡高+间隙)，
     * 完整可见 ⟺ y ≥ 0，所以张数 = ⌊anchorTop / (卡高+间隙)⌋ + 1。
     *
     * @param anchorTop  锚线纵坐标（最新的卡顶边；卡堆从这里向上长）
     * @param cardHeight 一张卡的高（同屏的卡等高）
     * @param gap        卡与卡之间的间隙
     * @return 能完整放下的张数；0 = 连一张都放不下
     */
    public static int fittingCount(float anchorTop, float cardHeight, float gap) {
        if (cardHeight <= 0f) {
            return Integer.MAX_VALUE;
        }
        if (anchorTop < cardHeight) {
            return 0;
        }
        return (int) (anchorTop / (cardHeight + gap)) + 1;
    }
}
