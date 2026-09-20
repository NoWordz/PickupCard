package com.niuqu.pickupcard.layout;

import com.niuqu.pickupcard.layout.LayoutSettings.Appear;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 排布这件事的"已知答案"（2026-09-19 起为<b>底锚、向上生长</b>语义）。
 * <p>
 * 【为什么专钉锚点】handoff 里记着那次翻车：游戏与草稿锚点方向相反，像素门禁只逐张比
 * 卡的结构（竖条宽、间距…），不比对锚点，于是一路绿灯。所以这里专钉两件事：
 * <b>锚在哪</b>、<b>谁在哪一位</b>。底锚 = 最新那张贴着锚线（HUD 带上方那条固定底线）、
 * 旧的被顶上去（2026-09-19 定案：数量优先 —— 427×240 锚点以上 145px 真放得下同屏 5 张）。
 */
class StackLayoutTest {

    private static final float EPS = 0.001f;
    private static final int MARGIN = 16;
    /** 逻辑画布：1280x720 @ guiScale 3 → 427x240。 */
    private static final float GUI_W = 427f;
    private static final float GUI_H = 240f;
    /** HUD 带留白（HudSafeZone.bottomInset()）。 */
    private static final int MARGIN_Y = 75;

    private static LayoutSettings anchored(Float x, Float y) {
        return new LayoutSettings(Appear.SLIDE, LayoutSettings.Exit.FADE, LayoutSettings.Side.LEFT,
                LayoutSettings.DEFAULT_SEPARATION, LayoutSettings.AUTO_SCALE,
                x == null ? LayoutSettings.AUTO_ANCHOR : x,
                y == null ? LayoutSettings.AUTO_ANCHOR : y);
    }

    private static List<StackLayout.Slot> stack(LayoutSettings layout, StackLayout.Size... sizes) {
        return StackLayout.stack(List.of(sizes), GUI_W, GUI_H, layout, MARGIN, MARGIN_Y, 6f);
    }

    @Test
    @DisplayName("没有卡时不算出任何位置")
    void emptyListProducesNothing() {
        assertTrue(stack(anchored(null, null)).isEmpty());
    }

    @Test
    @DisplayName("最新的一张贴着锚线顶边，旧的被顶上去一个级差")
    void newestCardSitsOnTheAnchor() {
        var s = stack(anchored(null, null), new StackLayout.Size(100, 30), new StackLayout.Size(100, 30));
        float anchor = GUI_H - MARGIN_Y - 30f;      // 自动档：贴 HUD 带上方
        assertEquals(anchor, s.get(0).y(), EPS, "index 0 = 最新 = 贴着锚线");
        assertEquals(s.get(0).y() - s.get(0).height() - 6f, s.get(1).y(), EPS,
                "旧的比最新那张高一个级差（向上生长）");
    }

    @Test
    @DisplayName("入场口不随张数漂：1 张和 5 张，最新那张的顶边是同一个 y")
    void entryEdgeIsStable() {
        var one = stack(anchored(null, null), new StackLayout.Size(100, 22));
        var many = stack(anchored(null, null), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22));
        assertEquals(one.get(0).y(), many.get(0).y(), EPS);
        // 而且越老的越高（y 越小）
        for (int i = 1; i < many.size(); i++) {
            assertTrue(many.get(i).y() < many.get(i - 1).y(), "第 " + i + " 张没有比前一张更高");
        }
    }

    @Test
    @DisplayName("退场那张在堆顶走人，其余卡一动不动（底锚的抖动老账）")
    void leavingCardLeavesFromTheTopWithoutDisturbingTheRest() {
        var five = fiveCards();
        var six = sixCards();
        for (int i = 0; i < 5; i++) {
            assertEquals(five.get(i).y() - 22f - 6f, six.get(i + 1).y(), EPS,
                    "第 " + i + " 张应该整体被顶上去一个级差");
        }
        // 到点退场的是最老那张（底锚下在堆顶，six 的第 5 张）。它走掉之后，剩下的五张
        // 与"从来只有五张"的排布完全一致 —— 也就是没有任何人需要补位。
        for (int i = 0; i < 5; i++) {
            assertEquals(five.get(i).y(), six.get(i).y(), EPS,
                    "堆顶那张走掉时第 " + i + " 张不该动 —— 这是底锚不抖的根据");
        }
    }

    @Test
    @DisplayName("水平：锚点是竖条左缘，所有卡左缘对齐；宽卡放不下时往左让")
    void anchorPinsTheBarLeftEdge() {
        // 锚点是**画布分数**：100/427 ≈ 0.234 → 在 427 宽画布上就是 x=100
        var s = stack(anchored(100f / GUI_W, null), new StackLayout.Size(120, 30),
                new StackLayout.Size(150, 30));
        assertEquals(100f, s.get(0).x(), EPS);
        assertEquals(100f, s.get(1).x(), EPS, "左缘对齐 —— 竖条成一条竖线");

        // 锚点想停在 320（320/427），但 427 宽 − 16 边距只装得下 91 宽的卡；给一张 200 宽的 → 自动左让
        var wide = stack(anchored(320f / GUI_W, null), new StackLayout.Size(200, 30));
        float maxLeft = GUI_W - MARGIN - 200;
        assertEquals(maxLeft, wide.get(0).x(), EPS, "放不下时往左让，绝不把内容挤出屏幕");
        assertTrue(wide.get(0).x() >= 0f);
    }

    @Test
    @DisplayName("右缘对齐：卡右缘贴锚线（HTML 的 rightalign 预设），宽卡被右边距拦住")
    void rightAlignPinsTheRightEdge() {
        LayoutSettings right = new LayoutSettings(Appear.SLIDE, LayoutSettings.Exit.FADE,
                LayoutSettings.Side.RIGHT, LayoutSettings.DEFAULT_SEPARATION,
                LayoutSettings.AUTO_SCALE, 300f / GUI_W, LayoutSettings.AUTO_ANCHOR);
        var s = stack(right, new StackLayout.Size(120, 30), new StackLayout.Size(150, 30));
        assertEquals(300f, s.get(0).x() + 120f, EPS, "窄卡右缘贴锚线");
        assertEquals(300f, s.get(1).x() + 150f, EPS, "宽卡右缘也贴锚线 —— 左缘参差、右缘齐");
        var wide = stack(right, new StackLayout.Size(200, 30));
        assertTrue(wide.get(0).x() + 200f <= GUI_W - MARGIN + EPS,
                "锚线太靠右时右缘被右边距拦住，不许出屏");
    }

    @Test
    @DisplayName("自动锚线按对齐档各自解析：左缘档=竖条成线公式，右缘档=贴右边距")
    void autoAnchorResolvesPerAlignSide() {
        // 2026-09-20 起默认是右缘档：defaults() 解析为贴右边距
        LayoutSettings right = LayoutSettings.defaults();
        assertEquals(GUI_W - MARGIN, right.anchorLeft(GUI_W), EPS,
                "右缘档的自动锚线贴右边距 —— 不再错拿左缘公式（卡会瞬移到屏幕中左的那个错）");

        LayoutSettings left = new LayoutSettings(Appear.SLIDE, LayoutSettings.Exit.FADE,
                LayoutSettings.Side.LEFT, 4f, LayoutSettings.AUTO_SCALE,
                LayoutSettings.AUTO_ANCHOR, LayoutSettings.AUTO_ANCHOR);
        assertEquals(LayoutSettings.autoLeftEdge(GUI_W), left.anchorLeft(GUI_W), EPS,
                "左缘档的竖条成线公式仍在");
    }

    @Test
    @DisplayName("默认锚点：横向 = 右缘贴右边距（2026-09-20 默认改右缘），纵向 = 贴 HUD 带上方")
    void defaultAnchorSitsAboveTheHudBand() {
        var s = StackLayout.stack(List.of(new StackLayout.Size(120, 30)), GUI_W, GUI_H,
                LayoutSettings.defaults(), MARGIN, MARGIN_Y, 6f);
        assertEquals(GUI_W - MARGIN - 120f, s.get(0).x(), EPS, "卡右缘贴右边距（MARGIN=16）：x = 右缘 − 卡宽");
        assertEquals(GUI_H - MARGIN_Y - 30f, s.get(0).y(), EPS, "最新那张顶边贴 HUD 带上方");
    }

    @Test
    @DisplayName("分数锚点跨缩放档稳定：同一分数在大小两块画布上落在同一个相对位置")
    void fractionalAnchorIsScaleInvariant() {
        // fx=0.5 / fy=0.3 在三档画布上都不触发夹取（y 方向 0.3*h 恒低于贴底档的锚线）
        LayoutSettings dragged = anchored(0.5f, 0.3f);
        for (float[] canvas : new float[][] {{426f, 240f}, {256f, 144f}, {640f, 360f}}) {
            var s = StackLayout.stack(List.of(new StackLayout.Size(100, 20)), canvas[0], canvas[1],
                    dragged, MARGIN, MARGIN_Y, 4f);
            assertEquals(canvas[0] * 0.5f, s.get(0).x(), EPS,
                    canvas[0] + " 宽：x 应该在 50% 处");
            assertEquals(canvas[1] * 0.3f, s.get(0).y(), EPS,
                    canvas[1] + " 高：y 应该在 30% 处");
        }
    }

    @Test
    @DisplayName("自定义锚拖得很低：排布照原样落在锚线上（不再自动抬），容量自己少排")
    void anchorDraggedLowStaysWhereDragged() {
        LayoutSettings low = anchored(0.7f, 0.98f);
        float cardH = 20f;
        float dragged = low.anchorTop(GUI_H, cardH, MARGIN_Y);
        assertEquals(GUI_H * 0.98f, dragged, EPS, "锚线就是玩家拖到的那条线，不夹不抬");

        var slots = StackLayout.stack(List.of(new StackLayout.Size(100, cardH)), GUI_W, GUI_H,
                low, MARGIN, MARGIN_Y, 4f);
        assertEquals(dragged, slots.get(0).y(), EPS, "排布必须用同一条锚线");
        // 放几张由容量说话：锚线拖得越<b>高</b>，上面越窄，容量越小（多的去排队）——
        // 拖低反而容量更大，这正是"全屏随便拖"后玩家自己拿捏的取舍
        assertTrue(StackLayout.fittingCount(dragged, cardH, 4f)
                > StackLayout.fittingCount(GUI_H - MARGIN_Y - cardH, cardH, 4f));
    }

    @Test
    @DisplayName("返回顺序与传入顺序一致，index 指得回去")
    void orderMatchesInputOrder() {
        var sizes = new StackLayout.Size[]{
                new StackLayout.Size(50, 20), new StackLayout.Size(60, 20), new StackLayout.Size(70, 20)};
        var s = stack(anchored(null, null), sizes);
        for (int i = 0; i < s.size(); i++) {
            assertEquals(i, s.get(i).index());
            assertEquals(sizes[i].width(), s.get(i).width(), EPS);
        }
    }

    @Test
    @DisplayName("整体高度把间隙算进去")
    void totalHeightIncludesGaps() {
        var sizes = List.of(new StackLayout.Size(10, 20), new StackLayout.Size(10, 30));
        assertEquals(20 + 30 + 5f, StackLayout.totalHeight(sizes, 5f), EPS);
    }

    @Test
    @DisplayName("取舍与排布同源：号称放得下的全在屏内，多一张必然越出屏顶")
    void onlyWhatFitsStaysOnScreen() {
        for (float h : new float[]{144f, 180f, 240f, 360f, 480f, 1080f}) {
            float top = LayoutSettings.defaults().anchorTop(h, 20f, 75);
            int fits = StackLayout.fittingCount(top, 20f, 4f);
            assertTrue(fits >= 1, "画布 " + h + " 高时一张都放不下，取舍就无从谈起");
            var slots = cards(h, fits + 1);
            for (int i = 0; i < fits; i++) {
                assertTrue(slots.get(i).y() >= 0f,
                        "画布 " + h + " 高：第 " + i + " 张号称放得下，顶边却出了屏幕（y="
                                + slots.get(i).y() + "）");
            }
            assertTrue(slots.get(fits).y() < 0f,
                    "画布 " + h + " 高：第 " + fits + " 张号称放不下，却还在屏内（y=" + slots.get(fits).y() + "）");
        }
    }

    /**
     * 反例对照（方向反过来了）：贴底锚点放得下的张数比"准星旁顶锚"时代<b>多</b> ——
     * 这是 2026-09-19 把锚点搬回右下的直接收益（可放高度从"锚点以下那一段"变回
     * "锚线以上的整段屏幕"）。谁要是把锚点搬回准星旁却忘改这里，这条会红。
     */
    @Test
    void bottomAnchoredColumnFitsMoreCardsThanACrosshairAnchoredOne() {
        // 240 高（guiScale 3 的 1280×720）：锚线 145 → 放得下 7 张；准星旁（132）只放得下 1 张
        assertEquals(7, fitsAt(240f));
        // 360 高（1920×1080 的 guiScale 3）：锚线 265 → 12 张
        assertEquals(12, fitsAt(360f));
        // 同屏上限默认 5 张（116px）在最小的常见画布上真放得下 —— 第四批审计的核心验收点
        assertTrue(fitsAt(240f) >= 5);
    }

    private static int fitsAt(float guiHeight) {
        float top = LayoutSettings.defaults().anchorTop(guiHeight, 20f, 75);
        return StackLayout.fittingCount(top, 20f, 4f);
    }

    /** n 张 100×20 的卡，用真实的底部留白（75）在给定画布高上排一遍。 */
    private static List<StackLayout.Slot> cards(float guiHeight, int n) {
        var sizes = new StackLayout.Size[n];
        for (int i = 0; i < n; i++) {
            sizes[i] = new StackLayout.Size(100, 20);
        }
        return StackLayout.stack(List.of(sizes), GUI_W, guiHeight, LayoutSettings.defaults(),
                MARGIN, 75, 4f);
    }

    /** 按排布结果数一数几张完整在屏内（顶边 ≥ 0）—— 与 fittingCount 同一把尺子。 */
    private static int countVisibleSlots(List<StackLayout.Slot> slots) {
        return (int) slots.stream().filter(s -> s.y() >= 0f).count();
    }

    private static List<StackLayout.Slot> fiveCards() {
        return stack(anchored(null, null), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22));
    }

    private static List<StackLayout.Slot> sixCards() {
        return stack(anchored(null, null), new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22),
                new StackLayout.Size(100, 22), new StackLayout.Size(100, 22));
    }
}
