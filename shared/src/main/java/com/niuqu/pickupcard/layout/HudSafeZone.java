package com.niuqu.pickupcard.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * 原版 HUD 占了哪几块，以及<b>卡堆该落在哪</b> —— 全部算出来，不写死留白。
 *
 * <p>【为什么会有这个类】2026-09-17 用户第三次报"卡片和物品栏 HUD 重叠"。前两次都在调那个
 * 魔数（16 → 52），第三次才去读原版源码：<b>漏的不是快捷栏，是"手持物品名"那一行</b>。
 * 所有数字抄自 1.20.1 的 {@code net/minecraft/client/gui/Gui.java}，注释里带行号 ——
 * 下次原版改版，对着行号重看一遍就知道该改哪个数。
 *
 * <pre>
 * 元素                        x 范围                距屏幕底多少像素        出处
 * 快捷栏 182×22               W/2 ± 91              H-22 .. H              Gui#renderHotbar
 * 选中框（高 1px）             W/2 ± 92              H-23 .. H-1            Gui#renderHotbar
 * 副手槽 29×24（有副手时）      <b>跟主手相反的一侧</b>    H-23 .. H+1            Gui#renderHotbar
 * 攻击指示器 18×18（快捷栏档）  见 hotbarRightHalf()   H-20 .. H-2            Gui#renderHotbar
 * 经验条 182×5                W/2 ± 91              H-29 .. H-24           Gui#renderExperienceBar
 * 经验等级数字（居中）          (W-w)/2 .. (W+w)/2    H-35 .. H-27           Gui#renderExperienceBar
 * 血量/食物（居中一排）         W/2 ± 91              H-39 .. H-30           Gui#renderPlayerHealth
 * 护甲/气泡（居中一排）         W/2 ± 91              H-49 .. H-40           Gui#renderPlayerHealth
 * <b>手持物品名（居中，宽度不定）</b>   (W-w)/2 .. (W+w)/2    H-59 .. H-50           Gui#renderSelectedItemName
 * 状态效果图标 24×24           W-25n .. W            1 .. 25 / 27 .. 51     Gui#renderEffects
 * 计分板侧栏                  W-w-5 .. W           H/2 上下 n*9           Gui#displayScoreboardSidebar
 * </pre>
 *
 * <p>【为什么"手持物品名"是关键】它是<b>居中</b>的、宽度随物品名变化 —— 而卡堆在右列：
 * 只要名字够长就会横伸过来。它的行在 H-59，而旧的底部留白 52 让最下面那张卡正好落在
 * H-52..H-72，与它整行相交。所以底部留白必须让到 H-60 以上（{@link #bottomInset}）。
 *
 * <p>【2026-09-18 晚删掉的一组】"快捷栏右侧条带"几何（stripLeft / hotbarRightHalf /
 * {@code STRIP_BOTTOM} / Strip）—— 卡堆改锚准星下方、向下生长，不再贴快捷栏，
 * 那套几何失去服务对象。本类剩下的是三样仍然活着的东西：
 * <b>底部留白</b>（{@link #bottomInset}，锚点以下算"放几张"的下界）、
 * <b>侧栏/状态效果让位</b>（{@link #reserve}，锚点在画布中部后更容易碰上）、
 * 与<b>底部各带的矩形</b>（给单测与诊断日志）。
 */
public final class HudSafeZone {

    // ---- 原版 HUD 的常量（逻辑像素）----
    /** 快捷栏/血量那一带向两侧各伸多少：182/2。 */
    public static final int HOTBAR_HALF = 91;
    /** 快捷栏底行（含选中框）距屏幕底多少像素。 */
    public static final int HOTBAR_TOP = 23;
    /**
     * 攻击指示器（快捷栏档）的宽度与它距快捷栏右缘的距离：画在 {@code W/2+91+6} 起、18 宽。
     * <p>它<b>只在充能时出现</b>（挥击后那不到一秒），而且只占快捷栏那一带。
     */
    public static final int ATTACK_INDICATOR_W = 18;
    public static final int ATTACK_INDICATOR_GAP = 6;
    /**
     * 手持物品名的行：pose 在 {@code H-59}，文字画在 {@code -4} 处 → 占 H-63 .. H-54。
     * 居中，宽度随物品名变化 —— 所以它会横伸进右列。
     */
    public static final int HELD_NAME_TOP = 63;
    /**
     * <b>动作栏提示语</b>（{@code Gui#renderOverlayMessage}）：pose 在 {@code H-68}，
     * 文字画在 {@code -4} 处、背后还有一条半透明底 → 整块占 <b>H-73 .. H-62</b>。
     * 它也居中、宽度不定（"按 F3 打开调试"能有一两百像素宽）。
     * <p>【为什么要单独记住它】它是那一带<b>最高</b>的一块 —— 卡堆只要让开它，
     * 就等于让开了下面所有东西。上一版只让到 H-62（照手持物品名算的），还是会压它。
     */
    public static final int OVERLAY_TOP = 73;
    /** 状态效果图标：一行 26 高、每列 25 宽。 */
    public static final int EFFECT_ROW_H = 26;
    public static final int EFFECT_COL_W = 25;
    /** 留白：卡与 HUD / 屏幕边之间至少这么多像素。 */
    public static final int PAD = 2;

    /**
     * 【这段记录留着当初的教训】快捷栏那一带向右伸到哪儿，曾经被算错成左撇子的几何：
     * "副手槽 W/2+91 .. W/2+120"是<b>左撇子</b>才有的事实，却被当成了所有人的。
     * 于是 427 宽的画布上右侧条带被算成 77.5px（真实 105.5px，宽了 36%），
     * 结论一度成了"那条缝放不下一张卡，所以整列放到 HUD 带上方去" —— 用户为此报了三次。
     * 副手槽画在<b>主手的反侧</b>（原版 {@code Gui#renderHotbar}：
     * {@code HumanoidArm arm = player.getMainArm().getOpposite()}）——
     * 右手玩家（默认）副手在左边，只有左撇子才会伸到右边来。
     * 条带几何随 2026-09-18 晚的锚点化一起删除。
     */

    private HudSafeZone() {
    }

    /** 一块矩形（屏幕绝对坐标，y 向下）。 */
    public record Rect(float x, float y, float w, float h) {
        public boolean intersects(Rect other) {
            return x < other.x + other.w && other.x < x + w
                    && y < other.y + other.h && other.y < y + h;
        }
    }

    /** 把 y 以"距屏幕底多少像素"表示的带子，转成屏幕坐标的矩形。 */
    private static Rect fromBottom(float guiHeight, float centerX, float halfWidth,
                                  int topFromBottom, float height) {
        return new Rect(centerX - halfWidth, guiHeight - topFromBottom,
                halfWidth * 2f, height);
    }

    /**
     * 卡堆底部必须留出的空白（距屏幕底的像素）= <b>动作栏提示语那一块之上再留 2px</b>。
     * <p>它是底部那一带最高的居中块（H-73），让开它就等于让开了手持物品名（H-63）、
     * 护甲（H-50）、血量（H-40）、经验（H-29）、快捷栏（H-23）—— 整条带一次让完。
     */
    public static int bottomInset() {
        return OVERLAY_TOP + PAD;
    }

    /**
     * 原版底部那一带的所有矩形（快捷栏 / 经验 / 血量 / 护甲 / 两块瞬时居中文字）。
     * <p>【给谁用】单测拿它一条条断言"不相交"；诊断日志也拿它解释"这次为什么让了这么多"。
     */
    public static List<Rect> bottomBands(float guiWidth, float guiHeight,
                                        float overlayWidth, float heldNameWidth) {
        float cx = guiWidth / 2f;
        List<Rect> rects = new ArrayList<>();
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF + 1, HOTBAR_TOP, 24f));      // 快捷栏 + 选中框
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 29, 5f));                   // 经验条
        rects.add(fromBottom(guiHeight, cx, Math.max(6f, 24f), 36, 9f));             // 等级数字（居中）
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 40, 10f));                  // 血量/食物
        rects.add(fromBottom(guiHeight, cx, HOTBAR_HALF, 50, 10f));                  // 护甲/气泡
        rects.add(fromBottom(guiHeight, cx, Math.max(2f, heldNameWidth / 2f), HELD_NAME_TOP, 10f));
        rects.add(fromBottom(guiHeight, cx, Math.max(2f, overlayWidth / 2f + 2f), OVERLAY_TOP, 11f));
        return rects;
    }

    /** 状态效果图标区（右上）：有益一排、有害再一排；没有效果就是空的。 */
    public static List<Rect> effectIcons(float guiWidth, int beneficial, int harmful) {
        List<Rect> rects = new ArrayList<>();
        if (beneficial > 0) {
            rects.add(new Rect(guiWidth - EFFECT_COL_W * beneficial, 1f,
                    EFFECT_COL_W * beneficial, 24f));
        }
        if (harmful > 0) {
            rects.add(new Rect(guiWidth - EFFECT_COL_W * harmful, 27f,
                    EFFECT_COL_W * harmful, 24f));
        }
        return rects;
    }

    /** 计分板侧栏（右侧一竖条）；没有就传 0。 */
    public static Rect sidebar(float guiWidth, float guiHeight, float width, int lines) {
        float h = Math.max(9f, lines * 9f + 12f);
        return new Rect(guiWidth - width - 5f, guiHeight / 2f - h / 2f, width + 5f, h);
    }

    /**
     * 右侧这一帧要额外让开多少 —— <b>只有卡堆真的碰上那一块才让</b>。
     * <p>
     * 【为什么不是"有就全让"】侧栏与状态效果图标都是"有时才在、而且在屏幕中部"的东西：
     * 画布 1080 高时卡堆贴着右下角（y≈890..1005），侧栏在 y≈472..607，两者根本碰不上，
     * 却因为一句无条件让位把整列推到快捷栏左边 —— 用户报的「低缩放下位置会变到物品栏左侧」
     * 就是这一条。状态效果图标与侧栏在这里共用一条规则，因为它们本来就是同一类东西。
     *
     * @param cards        卡堆这一帧（未左移时）占的矩形
     * @param sidebar      计分板侧栏的矩形；没有侧栏传 {@code null}
     * @param sidebarWidth 侧栏宽（让位量 = 它 + 5）
     * @param effects      状态效果图标带的矩形；没有效果传 {@code null}
     * @param effectsWidth 图标带的总宽（列数 × {@link #EFFECT_COL_W}）
     */
    public static float reserve(Rect cards, Rect sidebar, float sidebarWidth,
                                Rect effects, float effectsWidth) {
        float reserve = 0f;
        if (sidebar != null && cards.intersects(sidebar)) {
            reserve += sidebarWidth + 5f;
        }
        if (effects != null && cards.intersects(effects)) {
            reserve += effectsWidth;
        }
        return reserve;
    }

    /** 便捷：把一个矩形夹进"右侧留白"之内（左移多少）。 */
    public static float shiftLeft(float left, float cardWidth, float guiWidth, float rightReserve) {
        float overflow = (left + cardWidth) - (guiWidth - PAD - rightReserve);
        return overflow > 0f ? overflow : 0f;
    }

    /** 保留卡片的水平锚点，只避开这张卡实际碰到的右侧 HUD。 */
    public static float cardLeft(Rect card, List<Rect> obstacles) {
        float left = card.x();
        for (Rect obstacle : obstacles) {
            if (card.intersects(obstacle)) {
                left = Math.min(left, obstacle.x() - PAD - card.w());
            }
        }
        return Math.max(0f, left);
    }
}
