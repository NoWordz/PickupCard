package com.niuqu.pickupcard.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卡堆不许碰原版 HUD —— 用户报了<b>三次</b>的那个 bug 的回归测试（2026-09-18 起为锚点语义）。
 *
 * <p>【为什么值得单独一个测试类】前两次都在调一个魔数（16 → 52），每次都说"这次好了"，
 * 而每次漏的都是另一块。魔数改成"从原版 HUD 的矩形算出来"之后，这类漏检才有可能被一次测掉。
 * 锚点化之后本类管三件事：<b>底部留白</b>仍旧让开整条 HUD 带（锚点以下的下界）、
 * <b>侧栏/效果让位</b>只让真的碰上的（锚点在画布中部后更容易碰上）、
 * 以及锚点被拖得太低时的<b>夹取</b>。
 */
class HudSafeZoneTest {

    @Test
    void collisionOnAnUpperCardDoesNotMoveTheBottomCardFromItsAnchor() {
        var sidebar = HudSafeZone.sidebar(640f, 360f, 144f, 5);
        var upper = new HudSafeZone.Rect(490f, 173f, 134f, 21f);
        var bottom = new HudSafeZone.Rect(490f, 264f, 134f, 21f);
        assertTrue(upper.intersects(sidebar));
        assertEquals(sidebar.x() - HudSafeZone.PAD - upper.w(),
                HudSafeZone.cardLeft(upper, List.of(sidebar)), 0.01f);
        assertEquals(bottom.x(), HudSafeZone.cardLeft(bottom, List.of(sidebar)), 0.01f);
    }

    /**
     * 反例对照：旧的底部留白（写死的 52）在那一带<b>必然相交</b>（先撞动作栏提示语、再撞手持物品名）。
     * <p>这就是用户第三次报的那个 bug —— 如果哪天有人把底部留白又改回一个拍脑袋的数，
     * 这条断言会告诉他那个数错在哪。
     */
    @Test
    void theOldHardcodedInsetWouldStillCollide() {
        float guiWidth = 426f;
        float guiHeight = 240f;
        float cardWidth = guiWidth * 0.45f;
        float stackHeight = 5 * 20f + 4 * 4f;
        float left = guiWidth - 16f - cardWidth;
        HudSafeZone.Rect old = new HudSafeZone.Rect(left, guiHeight - 52f - stackHeight,
                cardWidth, stackHeight);
        boolean hit = false;
        for (HudSafeZone.Rect band : HudSafeZone.bottomBands(guiWidth, guiHeight, 180f, 120f)) {
            hit |= old.intersects(band);
        }
        assertTrue(hit, "旧留白 52 应该会撞上手持物品名那一行 —— 不然这条对照就失效了");
    }

    /**
     * 锚点夹取的最低保障：<b>最小画布（guiScale 5 → 256×144）上至少放得下一张卡</b>。
     * <p>自动锚点贴底（2026-09-19 起）：锚线 = 144−75−20 = 49，锚线以上放得下 3 张 ——
     * 贴底选址后"最小画布放不下几张"的老问题自然消失；这条守的是夹取公式本身。
     */
    @Test
    void smallestCanvasStillShowsOneCard() {
        float guiHeight = 144f;
        int inset = HudSafeZone.bottomInset();
        LayoutSettings defaults = LayoutSettings.defaults();
        float cardHeight = 20f;
        float gap = 4f;
        float top = defaults.anchorTop(guiHeight, cardHeight, inset);
        int fits = StackLayout.fittingCount(top, cardHeight, gap);
        assertTrue(fits >= 1, "256×144 上一张都放不下（锚线在 " + top + "，留白 " + inset + "）");
        // 而且"放得下的那一张"真的在 HUD 带上方
        assertTrue(top + cardHeight <= guiHeight - inset,
                "夹取后的第一张卡压过了 HUD 带（底 y=" + (top + cardHeight) + "）");
    }

    /** 右侧有侧栏 / 状态效果时，卡堆整体左移，不许压上去。 */
    @Test
    void sidebarAndEffectsPushTheColumnLeft() {
        float guiWidth = 426f;
        float guiHeight = 240f;
        float cardWidth = guiWidth * 0.45f;
        float stackHeight = 5 * 20f + 4 * 4f;
        float sidebarWidth = 90f;
        HudSafeZone.Rect sidebar = HudSafeZone.sidebar(guiWidth, guiHeight, sidebarWidth, 8);
        float left = guiWidth - 16f - cardWidth;
        float shift = HudSafeZone.shiftLeft(left, cardWidth, guiWidth, sidebarWidth + 5f);
        HudSafeZone.Rect cards = new HudSafeZone.Rect(left - shift,
                guiHeight - HudSafeZone.bottomInset() - stackHeight, cardWidth, stackHeight);
        assertFalse(cards.intersects(sidebar), "左移之后还压着侧栏：" + describe(cards));

        for (HudSafeZone.Rect effect : HudSafeZone.effectIcons(guiWidth, 2, 1)) {
            assertFalse(cards.intersects(effect), "左移之后还压着效果图标：" + describe(effect));
        }
    }

    /** 效果图标区只按"有几列"算宽，没有效果时是空的。 */
    @Test
    void effectIconsAreEmptyWithoutEffects() {
        assertEquals(0, HudSafeZone.effectIcons(426f, 0, 0).size());
        List<HudSafeZone.Rect> two = HudSafeZone.effectIcons(426f, 2, 0);
        assertEquals(1, two.size());
        assertEquals(50f, two.get(0).w(), 0.01f, "两列 = 50 宽");
        assertTrue(two.get(0).intersects(new HudSafeZone.Rect(400f, 2f, 10f, 10f)),
                "效果图标在右上，x 从 426-50=376 起");
    }

    /**
     * 让位判据：<b>只有卡堆真的碰上那一块才让</b>。
     * <p>
     * 【为什么这条重要】从前侧栏那条是无条件的 —— 卡堆贴右下角时侧栏在屏幕中部，两者
     * 隔着几百像素，却把整列推到快捷栏左边去。这就是用户报的「低缩放下位置会变到物品栏左侧」。
     * 锚点挪到准星下方之后，"碰上"的概率反而变高（卡堆默认就在中部），这条判据从偶尔生效
     * 变成了常备 —— 下面第二条钉的就是撞上时真让。
     */
    @Test
    void reservesOnlyWhatTheStackActuallyTouches() {
        // 卡堆贴右下角；侧栏 200 宽、15 行在屏幕中部 → 够不着
        HudSafeZone.Rect tallCards = new HudSafeZone.Rect(1040f, 890f, 192f, 116f);
        HudSafeZone.Rect tallSidebar = HudSafeZone.sidebar(1920f, 1080f, 200f, 15);
        assertEquals(0f, HudSafeZone.reserve(tallCards, tallSidebar, 200f, null, 0f), 0.01f,
                "隔着 280 像素却让位了：" + describe(tallSidebar));

        // 426×240 画布、锚点 55%：卡堆从 y≈132 起伸向 HUD 带，侧栏 120 宽 15 行 → 真的撞上
        HudSafeZone.Rect shortCards = new HudSafeZone.Rect(219f, 49f, 120f, 116f);
        HudSafeZone.Rect shortSidebar = HudSafeZone.sidebar(426f, 240f, 120f, 15);
        assertTrue(shortCards.intersects(shortSidebar), "这条断言的前提是两者相交");
        assertEquals(125f, HudSafeZone.reserve(shortCards, shortSidebar, 120f, null, 0f), 0.01f,
                "让位量 = 侧栏宽 + 5");
    }

    /** 状态效果图标同一条规则：够不着就不让（纵向差得远就不该让位）。 */
    @Test
    void effectsAreReservedOnlyWhenReached() {
        // 1920 宽的画布上三列有益 + 一列有害：图标带在 x=1845..1920、y=1..51
        HudSafeZone.Rect icons = HudSafeZone.effectIcons(1920f, 3, 1).get(0);
        // 卡堆贴着右下角（y≈890），够不着右上角那一带
        HudSafeZone.Rect lowCards = new HudSafeZone.Rect(1845f, 890f, 75f, 116f);
        assertEquals(0f, HudSafeZone.reserve(lowCards, null, 0f, icons, 75f), 0.01f,
                "隔着 800 多像素却让位了：" + describe(icons));

        // 同一摞卡整摞抬到屏幕顶部 → 真的撞上，让 75
        HudSafeZone.Rect highCards = new HudSafeZone.Rect(1845f, 0f, 75f, 116f);
        assertTrue(highCards.intersects(icons), "这条断言的前提是两者相交");
        assertEquals(75f, HudSafeZone.reserve(highCards, null, 0f, icons, 75f), 0.01f);
    }

    private static String describe(HudSafeZone.Rect r) {
        return String.format("(x=%.0f y=%.0f w=%.0f h=%.0f)", r.x(), r.y(), r.w(), r.h());
    }
}
