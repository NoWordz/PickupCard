package com.niuqu.pickupcard.notice;

import com.niuqu.pickupcard.text.CountFormat;
import com.niuqu.pickupcard.text.CountMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「一张卡该显示什么」这一层的夹逼：配置是玩家手改的，非法值不该变成崩溃或空卡。
 */
class PickupCardSettingsTest {

    @Test
    void defaultsShowTheNameAndRespectTheScreenBudget() {
        PickupCardSettings d = PickupCardSettings.defaults();
        assertTrue(d.enabled(), "默认开着");
        assertTrue(d.showItemName(), "默认显示物品名");
        assertTrue(!d.showItemId(), "默认显示名字而不是 ID");
        assertEquals(0, d.nameMaxWidth(), "默认不设名字宽度上限（按屏宽比例自动）");
    }

    /** 名字最大宽度：0 是"自动"，给了正数也不许小到比一个字符还窄。 */
    @Test
    void nameMaxWidthIsZeroOrSane() {
        assertEquals(0, settingsWithNameWidth(-5).nameMaxWidth(), "负数当没设");
        assertEquals(0, settingsWithNameWidth(0).nameMaxWidth());
        assertEquals(24, settingsWithNameWidth(3).nameMaxWidth(), "太窄的宽限到 24");
        assertEquals(200, settingsWithNameWidth(200).nameMaxWidth());
        assertEquals(600, settingsWithNameWidth(9_999).nameMaxWidth(), "上限 600");
    }

    @Test
    void nullFullPolicySanitizesToReplace() {
        PickupCardSettings settings = new PickupCardSettings(2_600L, 320L, MergeMode.defaults(),
                5, 9, CountFormat.PLUS, true, true, false, 0, null);
        assertEquals(FullPolicy.REPLACE, settings.sanitized().fullPolicy(), "null 防御回新默认 REPLACE");
    }

    @Test
    void fullPolicyDefaultsToReplace() {
        assertEquals(FullPolicy.REPLACE, PickupCardSettings.defaults().fullPolicy(),
                "新默认：屏满顶掉旧卡");
        PickupCardSettings viaTenArgs = new PickupCardSettings(1_000L, 100L, MergeMode.SAME_NBT,
                3, 9, CountFormat.PLUS, true, true, false, 0);
        assertEquals(FullPolicy.REPLACE, viaTenArgs.fullPolicy(), "10 参旧签名缺省 REPLACE");
    }

    @Test
    void nullCountModeSanitizesToPickup() {
        PickupCardSettings settings = new PickupCardSettings(2_600L, 320L, MergeMode.defaults(),
                5, 9, CountFormat.PLUS, true, true, false, 0, FullPolicy.REPLACE, null);
        assertEquals(CountMode.PICKUP, settings.sanitized().countMode(), "null 防御回默认口径");
    }

    @Test
    void countModeDefaultsToPickup() {
        assertEquals(CountMode.PICKUP, PickupCardSettings.defaults().countMode(),
                "默认口径 = 本次拾取数（0.2.2 及以前的行为）");
        PickupCardSettings viaElevenArgs = new PickupCardSettings(1_000L, 100L, MergeMode.SAME_NBT,
                3, 9, CountFormat.PLUS, true, true, false, 0, FullPolicy.QUEUE);
        assertEquals(CountMode.PICKUP, viaElevenArgs.countMode(), "11 参旧签名缺省 PICKUP");
        PickupCardSettings viaTenArgs = new PickupCardSettings(1_000L, 100L, MergeMode.SAME_NBT,
                3, 9, CountFormat.PLUS, true, true, false, 0);
        assertEquals(CountMode.PICKUP, viaTenArgs.countMode(), "10 参旧签名也缺省 PICKUP");
        assertEquals(FullPolicy.REPLACE, viaTenArgs.fullPolicy(), "10 参旧签名的 fullPolicy 缺省不变");
    }

    @Test
    void switchesSurviveSanitizing() {
        PickupCardSettings off = new PickupCardSettings(1_000L, 100L, MergeMode.SAME_NBT, 3, 9,
                CountFormat.PLAIN, false, false, true, 0).sanitized();
        assertTrue(!off.enabled(), "总开关不该被夹没了");
        assertTrue(!off.showItemName());
        assertTrue(off.showItemId());
    }

    private static PickupCardSettings settingsWithNameWidth(int width) {
        return new PickupCardSettings(1_000L, 100L, MergeMode.SAME_NBT, 3, 9,
                CountFormat.PLUS, true, true, false, width).sanitized();
    }
}
