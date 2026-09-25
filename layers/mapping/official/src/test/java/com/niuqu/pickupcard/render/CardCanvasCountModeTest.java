package com.niuqu.pickupcard.render;

import com.niuqu.pickupcard.layout.LayoutSettings;
import com.niuqu.pickupcard.notice.Notice;
import com.niuqu.pickupcard.notice.PickupCardSettings;
import com.niuqu.pickupcard.pickup.CardContent;
import com.niuqu.pickupcard.pickup.Inbox;
import com.niuqu.pickupcard.style.CardTimeline;
import com.niuqu.pickupcard.style.StyleModel;
import com.niuqu.pickupcard.text.CountFormat;
import com.niuqu.pickupcard.text.CountMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 卡上数字的口径分派（离线能钉的那一半）。
 *
 * <p>【诚实边界】{@code displayCount} 的<b>物品卡</b>分支会走 {@code InventoryTotals}
 * （→ {@code Minecraft}，纯 JVM 直接抛），离线测不了 —— "拾 5 个、背包已有 10 个、卡上画 15"
 * 归 harness 截图验证。这里能钉的是<b>不碰 Minecraft 的那三条路</b>：经验卡在总数口径下
 * 回账本数量、prevText 的两档分派、countText 的符号分派。它们的坏法是静默的：
 * 分派写反了画面上就是"总数带加号"或"经验卡数字乱跳"，单测之外没人会红。
 */
class CardCanvasCountModeTest {

    private static CardCanvas canvas(CountMode mode) {
        PickupCardSettings settings = new PickupCardSettings(
                2_600L, 320L, com.niuqu.pickupcard.notice.MergeMode.defaults(), 5, 9,
                CountFormat.PLUS, true, true, false, 0,
                com.niuqu.pickupcard.notice.FullPolicy.REPLACE, mode);
        return new CardCanvas(0L, CardTimeline.defaults(), StyleModel.defaults(), settings,
                LayoutSettings.defaults(), 1920, 1080, 1f);
    }

    private static CardView experienceView(int count) {
        Notice<Inbox.Card> notice = new Notice<>(Inbox.XP_KEY, Inbox.XP_KEY,
                new Inbox.Card(new CardContent.Experience(), false), count, false, 0L, 0L, 0);
        return new CardView(notice);
    }

    @Test
    void pickupModeKeepsTheGainPrefix() {
        assertEquals("+7", canvas(CountMode.PICKUP).countText(7));
    }

    @Test
    void totalModeDropsTheGainPrefix() {
        assertEquals("7", canvas(CountMode.TOTAL).countText(7));
    }

    @Test
    void totalModeExperienceCardFallsBackToLedgerCount() {
        CardView view = experienceView(320);
        assertEquals(320, canvas(CountMode.TOTAL).displayCount(view),
                "经验没有「背包里的经验」可言：总数口径回账本数量，不碰物品栏");
    }

    @Test
    void totalModeNeverRollsFromAPrevCount() {
        CardView view = experienceView(3);
        // 合并一次：prevCount=3，count=8 —— 拾取口径会从 3 滚到 8
        view.absorbMerge(view.notice().mergeInto(5, 10L), 10L);
        assertEquals("+3", canvas(CountMode.PICKUP).prevCountText(view), "拾取口径照旧滚动");
        assertNull(canvas(CountMode.TOTAL).prevCountText(view),
                "总数口径的旧总数账本不知道，硬凑会从旧拾取数滚到新总数");
    }

    @Test
    void pickupModeStillReturnsNullWhenNothingToRoll() {
        CardView view = experienceView(3);
        assertNull(canvas(CountMode.PICKUP).prevCountText(view), "没合并过就没有旧值");
    }
}
